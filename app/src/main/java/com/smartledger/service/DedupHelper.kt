package com.smartledger.service

import android.util.Log
import com.smartledger.data.db.dao.TransactionDao
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.util.CurrencyUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.min

/**
 * 同一笔真实交易只保留一条。
 *
 * 购物 App 用银行卡付款时，收银台和银行几乎同时各推一条通知；
 * 银行短信也可能随后补一条。去重必须：
 * - 双向比较时间（后到的通知 postTime 可能更早）
 * - 收银台（京东/淘宝/微信等）和银行卡交叉匹配放宽到 3 分钟
 * - 串行化「查询 + 写入」，避免两条通知同时通过检查
 *
 * 未清掉的旧通知在几小时后被系统重新推送时，不能靠这个时间窗，
 * 要靠 [ProcessedNoticeStore] 的通知指纹，以及 [stableEventTime]。
 */
object DedupHelper {

    private const val TAG = "DedupHelper"

    /** 京东/微信等收银台 ↔ 银行卡：同一笔付款两条通知的最大间隔 */
    const val CROSS_CHECKOUT_WINDOW_MS = 180_000L

    /** 商户名一致（互相包含） */
    private const val SAME_MERCHANT_WINDOW_MS = 120_000L

    /** 微信 ↔ 支付宝这类不同钱包，窗口短一些，减少两笔独立消费被并成一笔 */
    private const val CROSS_WALLET_WINDOW_MS = 90_000L

    /** 同渠道且双方都没有真实商户：只合并几乎同时的重复推送 */
    private const val SAME_CHANNEL_NO_MERCHANT_MS = 15_000L

    /** 渠道提示互相矛盾时，只合并极短时间内的重复包 */
    private const val CONFLICT_WINDOW_MS = 15_000L

    private const val SAME_CHANNEL_WINDOW_MS = 60_000L

    const val MAX_WINDOW_MS = CROSS_CHECKOUT_WINDOW_MS

    private val gate = Mutex()

    /** 通知入账、短信入账、确认入账必须共用这把锁，否则会同时插入。 */
    suspend fun <T> exclusive(block: suspend () -> T): T = gate.withLock { block() }

    data class PaymentSignature(
        val amountCents: Long,
        val type: String,
        val merchant: String?,
        val paymentMethod: String?,
        val transactionTime: Long,
        /** 银行通知正文里识别出的对方渠道：微信 / 支付宝 / 京东 / 抖音 / 云闪付 */
        val channelHint: String? = null,
    )

    /**
     * @return 已有的重复交易；null 表示应新记一笔
     */
    suspend fun findDuplicate(
        dao: TransactionDao,
        amountCents: Long,
        type: String,
        merchant: String?,
        paymentMethod: String? = null,
        now: Long = System.currentTimeMillis(),
        channelHint: String? = null,
    ): Transaction? {
        val recentList = dao.getByTimeRangeOnce(now - MAX_WINDOW_MS, now + MAX_WINDOW_MS)
        val incoming = PaymentSignature(
            amountCents, type, merchant, paymentMethod, now, channelHint
        )
        return recentList
            .mapNotNull { existing ->
                val signature = PaymentSignature(
                    amountCents = CurrencyUtil.toCents(existing.amount),
                    type = existing.type,
                    merchant = existing.merchant,
                    paymentMethod = existing.paymentMethod,
                    transactionTime = existing.transactionTime,
                    channelHint = inferChannelHint(existing.merchant),
                )
                if (paymentsAreDuplicate(signature, incoming)) {
                    existing to abs(existing.transactionTime - now)
                } else {
                    null
                }
            }
            .minByOrNull { it.second }
            ?.first
    }

    fun paymentsAreDuplicate(existing: PaymentSignature, incoming: PaymentSignature): Boolean {
        if (existing.amountCents != incoming.amountCents || existing.type != incoming.type) {
            return false
        }
        val diff = abs(existing.transactionTime - incoming.transactionTime)
        return diff < windowMs(existing, incoming)
    }

    fun windowMs(existing: PaymentSignature, incoming: PaymentSignature): Long {
        val methodA = existing.paymentMethod.orEmpty()
        val methodB = incoming.paymentMethod.orEmpty()
        val sameChannel = methodA.isNotBlank() && methodA == methodB
        val sameMerchant = merchantMatch(incoming.merchant, existing.merchant)
        val bothGeneric = isGenericMerchant(existing.merchant) && isGenericMerchant(incoming.merchant)

        if (isCrossChannelCheckout(existing, incoming)) {
            if (hintsConflict(existing, incoming)) return CONFLICT_WINDOW_MS
            // 京东/淘宝对银行卡，或银行正文写了财付通/网银在线：基本是同一笔
            if (checkoutLinked(existing, incoming)) return CROSS_CHECKOUT_WINDOW_MS
            // 没对上渠道时只留 90 秒，覆盖截图里同一分钟的双通知
            return CROSS_WALLET_WINDOW_MS
        }
        if (sameMerchant && !bothGeneric) return SAME_MERCHANT_WINDOW_MS
        if (sameBankFamily(methodA, methodB) &&
            (sameMerchant || isGenericMerchant(existing.merchant) || isGenericMerchant(incoming.merchant))
        ) {
            return CROSS_CHECKOUT_WINDOW_MS
        }
        if (sameChannel && bothGeneric) return SAME_CHANNEL_NO_MERCHANT_MS
        if (!sameChannel && methodA.isNotBlank() && methodB.isNotBlank()) {
            return CROSS_WALLET_WINDOW_MS
        }
        return SAME_CHANNEL_WINDOW_MS
    }

    /**
     * 通知被系统刷新时，postTime 会跳成“现在”，[notificationWhen] 往往还是原交易时间。
     * 用原时间去对账，早上那笔才不会在下午被当成新交易。
     */
    fun stableEventTime(
        postTime: Long,
        notificationWhen: Long,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val posted = if (postTime > 0L) postTime else now
        if (notificationWhen <= 0L) return posted
        if (notificationWhen > now + 60_000L) return posted
        if (now - notificationWhen > 18L * 60 * 60 * 1000) return posted
        val postIsFresh = abs(now - posted) <= 2 * 60_000L
        val whenIsEarlier = posted - notificationWhen >= 2 * 60_000L
        return if (postIsFresh && whenIsEarlier) notificationWhen else posted
    }

    fun merchantMatch(new: String?, old: String?): Boolean {
        if (isGenericMerchant(new) || isGenericMerchant(old)) return false
        val a = new!!.trim()
        val b = old!!.trim()
        return a.contains(b) || b.contains(a)
    }

    /** 支付通道占位名，不是用户认识的商户。 */
    fun isGenericMerchant(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val n = name.trim()
        if (n in GENERIC_EXACT) return true
        if (n.contains("平台商户")) return true
        if (n.contains("网银在线") && n.length <= 12) return true
        return false
    }

    /**
     * 从银行通知 / 短信正文判断这笔扣款实际走的收银台。
     * 例：支出(财付通-某某) → 微信；理财网银在线-平台商户 → 京东。
     */
    fun inferChannelHint(text: String?): String? {
        if (text.isNullOrBlank()) return null
        if (text.contains("财付通") || text.contains("微信支付")) return "微信"
        if (text.contains("支付宝")) return "支付宝"
        if (text.contains("抖音支付")) return "抖音"
        if (text.contains("网银在线") || text.contains("京东支付")) return "京东"
        if (text.contains("云闪付")) return "云闪付"
        return null
    }

    fun chooseMerchant(existing: String?, incoming: String?): String? {
        val oldReal = !isGenericMerchant(existing)
        val newReal = !isGenericMerchant(incoming)
        return when {
            newReal && !oldReal -> incoming!!.trim()
            oldReal && !newReal -> existing!!.trim()
            newReal && oldReal -> {
                val n = incoming!!.trim()
                val o = existing!!.trim()
                if (n.length > o.length) n else o
            }
            else -> null
        }
    }

    fun chooseMethod(existing: String?, incoming: String?): String? {
        if (incoming.isNullOrBlank()) return existing
        if (existing.isNullOrBlank()) return incoming
        return if (isMoreSpecificMethod(incoming, existing)) incoming else existing
    }

    /**
     * 合并进已有记录：保留更具体的银行卡渠道、真实商户，并把本条通知指纹挂到同一行上。
     * 时间取更早的那一个（两条通知描述的是同一时刻的付款）。
     */
    suspend fun absorbDuplicate(
        dao: TransactionDao,
        existing: Transaction,
        incomingMethod: String?,
        incomingMerchant: String?,
        incomingTime: Long,
        noticeKeys: Collection<String>,
        categories: List<Category>,
    ) {
        val merchant = chooseMerchant(existing.merchant, incomingMerchant)
        val method = chooseMethod(existing.paymentMethod, incomingMethod)
        val suggested = SmartCategorizer.categorize(
            merchant = merchant,
            paymentMethod = method,
            note = existing.note,
            categories = categories,
            type = existing.type,
        )
        val categoryId = upgradedCategoryId(existing.categoryId, suggested, categories)
        val noticeKey = appendNoticeKeys(existing.notificationKey, noticeKeys)
        val transactionTime = earlierTime(existing.transactionTime, incomingTime)
        val changed = merchant != existing.merchant ||
            method != existing.paymentMethod ||
            categoryId != existing.categoryId ||
            noticeKey != existing.notificationKey ||
            transactionTime != existing.transactionTime
        if (!changed) {
            Log.d(TAG, "Duplicate kept as-is id=${existing.id}")
            return
        }
        dao.update(
            existing.copy(
                merchant = merchant,
                paymentMethod = method,
                categoryId = categoryId,
                notificationKey = noticeKey,
                transactionTime = transactionTime,
            )
        )
        Log.d(
            TAG,
            "Absorbed duplicate into id=${existing.id}: method=$method, merchant=$merchant"
        )
    }

    fun appendNoticeKeys(existing: String?, keys: Collection<String>): String? {
        val parts = existing
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableList()
            ?: mutableListOf()
        for (key in keys) {
            val trimmed = key.trim()
            if (trimmed.isNotEmpty() && trimmed !in parts) parts.add(trimmed)
        }
        while (parts.size > 8) parts.removeAt(0)
        return parts.joinToString(",").ifBlank { null }
    }

    fun upgradedCategoryId(
        currentId: Long?,
        suggestedId: Long?,
        categories: List<Category>,
    ): Long? {
        if (suggestedId == null) return currentId
        if (currentId == null || currentId == suggestedId) return suggestedId
        val current = categories.find { it.id == currentId } ?: return currentId
        val suggested = categories.find { it.id == suggestedId } ?: return currentId
        // 银行通知往往先落到「其他」，收银台商户名到达后再升到购物/餐饮等
        return if (current.name == "其他" && suggested.name != "其他") suggestedId else currentId
    }

    private fun earlierTime(existing: Long, incoming: Long): Long {
        if (incoming <= 0L) return existing
        if (existing <= 0L) return incoming
        return min(existing, incoming)
    }

    private fun isCrossChannelCheckout(a: PaymentSignature, b: PaymentSignature): Boolean {
        val bankA = isBankMethod(a.paymentMethod)
        val bankB = isBankMethod(b.paymentMethod)
        val walletA = isWalletMethod(a.paymentMethod)
        val walletB = isWalletMethod(b.paymentMethod)
        return (bankA && walletB) || (bankB && walletA)
    }

    private fun checkoutLinked(a: PaymentSignature, b: PaymentSignature): Boolean {
        val bank = if (isBankMethod(a.paymentMethod)) a else b
        val wallet = if (bank === a) b else a
        if (isShoppingPlatform(wallet.paymentMethod)) return true
        val bankHint = bank.channelHint ?: inferChannelHint(bank.merchant)
        val walletHint = wallet.channelHint ?: inferChannelHint(wallet.merchant)
        if (bankHint != null && walletHint != null && bankHint == walletHint) return true
        if (bankHint != null && hintCompatible(bankHint, wallet.paymentMethod.orEmpty())) return true
        return false
    }

    private fun isShoppingPlatform(method: String?): Boolean {
        return method == "京东" || method == "淘宝" || method == "天猫" || method == "抖音"
    }

    /**
     * 银行侧已经写明是支付宝，对侧却是微信：多半是两笔金额碰巧相同的独立消费。
     */
    private fun hintsConflict(a: PaymentSignature, b: PaymentSignature): Boolean {
        val bank = when {
            isBankMethod(a.paymentMethod) && isWalletMethod(b.paymentMethod) -> a
            isBankMethod(b.paymentMethod) && isWalletMethod(a.paymentMethod) -> b
            else -> return false
        }
        val wallet = if (bank === a) b else a
        val bankHint = bank.channelHint ?: inferChannelHint(bank.merchant) ?: return false
        // 京东收银台正文若写了「微信支付」，以正文为准，不要和京东这个渠道名硬碰
        val walletHint = wallet.channelHint ?: inferChannelHint(wallet.merchant)
        if (walletHint != null) return bankHint != walletHint
        val walletName = wallet.paymentMethod ?: return false
        return !hintCompatible(bankHint, walletName)
    }

    private fun hintCompatible(hint: String, wallet: String): Boolean {
        return when (hint) {
            "微信" -> wallet == "微信"
            "支付宝" -> wallet == "支付宝"
            "京东" -> wallet == "京东"
            "抖音" -> wallet == "抖音"
            "云闪付" -> wallet == "云闪付"
            else -> true
        }
    }

    private fun sameBankFamily(a: String, b: String): Boolean {
        if (!isBankMethod(a) || !isBankMethod(b)) return false
        if (a == "银行卡" || b == "银行卡") return true
        return a == b
    }

    private fun isBankMethod(method: String?): Boolean = method != null && method in BANK_METHODS

    private fun isWalletMethod(method: String?): Boolean = method != null && method in WALLET_METHODS

    private fun isMoreSpecificMethod(new: String, existing: String): Boolean {
        return (METHOD_PRIORITY[new] ?: 0) > (METHOD_PRIORITY[existing] ?: 0)
    }

    private val GENERIC_EXACT = setOf(
        "平台商户", "网银在线", "理财网银在线", "财付通", "微信支付",
        "支付宝", "云闪付", "抖音支付", "京东支付", "商户", "收款方",
    )

    private val BANK_METHODS = setOf(
        "工商银行", "邮政储蓄", "建设银行", "中国银行", "农业银行", "招商银行",
        "平安银行", "浦发银行", "民生银行", "光大银行", "兴业银行", "交通银行",
        "中信银行", "银行卡",
    )

    private val WALLET_METHODS = setOf(
        "微信", "支付宝", "云闪付", "抖音", "京东", "淘宝", "天猫",
    )

    private val METHOD_PRIORITY = mapOf(
        "工商银行" to 10, "邮政储蓄" to 10, "建设银行" to 10,
        "中国银行" to 10, "农业银行" to 10, "招商银行" to 10,
        "平安银行" to 10, "浦发银行" to 10, "民生银行" to 10,
        "光大银行" to 10, "兴业银行" to 10, "交通银行" to 10,
        "中信银行" to 10,
        "银行卡" to 5,
        "支付宝" to 3, "微信" to 3, "云闪付" to 3,
        "京东" to 3, "淘宝" to 3, "天猫" to 3,
        "抖音" to 2,
    )
}
