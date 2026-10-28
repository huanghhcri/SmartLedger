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
        /** wallet：微信/支付宝等 App；bank：银行 App 或短信。用来区分「同一笔的另一条通知」和「两分钟后又买了一单」。 */
        val origin: String? = null,
        /** 这条账已经同时吃进过收银台通知和银行通知，不能再吞下一单。 */
        val alreadyPaired: Boolean = false,
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
        origin: String? = null,
    ): Transaction? {
        val recentList = dao.getByTimeRangeOnce(now - MAX_WINDOW_MS, now + MAX_WINDOW_MS)
        val incoming = PaymentSignature(
            amountCents, type, merchant, paymentMethod, now, channelHint, origin
        )
        return recentList
            .mapNotNull { existing ->
                val signature = PaymentSignature(
                    amountCents = CurrencyUtil.toCents(existing.amount),
                    type = existing.type,
                    merchant = existing.merchant,
                    paymentMethod = existing.paymentMethod,
                    transactionTime = existing.transactionTime,
                    channelHint = inferChannelHint(existing.merchant)
                        ?: channelHintFromStoredKey(existing.notificationKey),
                    origin = originFromStoredKey(existing.notificationKey),
                    alreadyPaired = hasBothSides(existing.notificationKey),
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
        // 第一单的微信和银行已经配上之后：再来一条微信，只认十几秒内的重复推送；
        // 银行短信可以晚一点，但两分钟后的下一单不能再吸进来。
        if (existing.alreadyPaired) {
            if (hintsConflict(existing, incoming)) return diff < CONFLICT_WINDOW_MS
            val limit = if (incoming.origin == ORIGIN_BANK) {
                SAME_CHANNEL_WINDOW_MS
            } else {
                SAME_CHANNEL_NO_MERCHANT_MS
            }
            return diff < limit
        }
        return diff < windowMs(existing, incoming)
    }

    fun windowMs(existing: PaymentSignature, incoming: PaymentSignature): Long {
        val methodA = existing.paymentMethod.orEmpty()
        val methodB = incoming.paymentMethod.orEmpty()
        val sameChannel = methodA.isNotBlank() && methodA == methodB
        val sameMerchant = merchantMatch(incoming.merchant, existing.merchant)
        val bothGeneric = isGenericMerchant(existing.merchant) && isGenericMerchant(incoming.merchant)
        val sameOrigin = existing.origin != null && existing.origin == incoming.origin

        if (hintsConflict(existing, incoming)) return CONFLICT_WINDOW_MS
        // 两条都来自微信，或两条都来自银行：只合并十几秒内的重复推送，10:49 的下一单单独记
        if (sameOrigin) return SAME_CHANNEL_NO_MERCHANT_MS
        // 微信 App 和银行 App，即使支付方式都被写成「工商银行」也是同一笔的两侧
        if (isWalletAndBank(existing, incoming)) return CROSS_CHECKOUT_WINDOW_MS

        if (isCrossChannelCheckout(existing, incoming)) {
            if (checkoutLinked(existing, incoming)) return CROSS_CHECKOUT_WINDOW_MS
            return CROSS_WALLET_WINDOW_MS
        }
        // 同一 App 再推一条：不因为商户名相同就放宽到 2 分钟
        if (sameChannel) return SAME_CHANNEL_NO_MERCHANT_MS
        if (sameMerchant && !bothGeneric) return SAME_MERCHANT_WINDOW_MS
        if (sameBankFamily(methodA, methodB) &&
            (sameMerchant || isGenericMerchant(existing.merchant) || isGenericMerchant(incoming.merchant))
        ) {
            return CROSS_CHECKOUT_WINDOW_MS
        }
        if (methodA.isNotBlank() && methodB.isNotBlank()) return CROSS_WALLET_WINDOW_MS
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

    /** 通知来自哪个收银台。银行 App 返回 null，渠道以正文为准。 */
    fun walletFromPackage(packageName: String): String? {
        return when {
            packageName.contains("tencent.mm") -> "微信"
            packageName.contains("AlipayGphone") -> "支付宝"
            packageName.contains("unionpay") -> "云闪付"
            packageName.contains("ugc.aweme") || packageName.contains("ugc.live") -> "抖音"
            packageName.contains("jingdong") || packageName.contains("jd.jr") ||
                packageName.contains("jdlite") -> "京东"
            packageName.contains("taobao") -> "淘宝"
            packageName.contains("tmall") -> "天猫"
            else -> null
        }
    }

    fun hintToken(hint: String?): String? {
        if (hint.isNullOrBlank()) return null
        return HINT_PREFIX + hint.trim()
    }

    fun channelHintFromStoredKey(notificationKey: String?): String? {
        return notificationKey
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith(HINT_PREFIX) }
            ?.removePrefix(HINT_PREFIX)
            ?.takeIf { it.isNotBlank() }
    }

    fun withChannelHint(keys: Collection<String>, hint: String?, origin: String? = null): List<String> {
        return keys + listOfNotNull(hintToken(hint), originToken(origin))
    }

    fun originToken(origin: String?): String? {
        return when (origin) {
            ORIGIN_WALLET -> WALLET_ORIGIN_TOKEN
            ORIGIN_BANK -> BANK_ORIGIN_TOKEN
            else -> null
        }
    }

    fun hasBothSides(notificationKey: String?): Boolean {
        val parts = splitKeys(notificationKey)
        return parts.any { it == WALLET_ORIGIN_TOKEN } && parts.any { it == BANK_ORIGIN_TOKEN }
    }

    fun originFromStoredKey(notificationKey: String?): String? {
        val parts = splitKeys(notificationKey)
        val wallet = parts.any { it == WALLET_ORIGIN_TOKEN }
        val bank = parts.any { it == BANK_ORIGIN_TOKEN }
        return when {
            wallet && !bank -> ORIGIN_WALLET
            bank && !wallet -> ORIGIN_BANK
            else -> null
        }
    }

    private fun splitKeys(notificationKey: String?): List<String> {
        return notificationKey?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
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
        incomingChannelHint: String? = null,
        incomingOrigin: String? = null,
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
        val noticeKey = appendNoticeKeys(
            existing.notificationKey,
            withChannelHint(noticeKeys, incomingChannelHint, incomingOrigin),
        )
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
            if (trimmed.isEmpty() || trimmed in parts) continue
            // 已有渠道标记时保留先到的那一个，避免微信和支付宝标记叠在同一行
            if (trimmed.startsWith(HINT_PREFIX) && parts.any { it.startsWith(HINT_PREFIX) }) continue
            if ((trimmed == WALLET_ORIGIN_TOKEN || trimmed == BANK_ORIGIN_TOKEN) && trimmed in parts) continue
            parts.add(trimmed)
        }
        while (parts.count { !isStickyToken(it) } > 8) {
            val index = parts.indexOfFirst { !isStickyToken(it) }
            if (index < 0) break
            parts.removeAt(index)
        }
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

    private fun isWalletAndBank(a: PaymentSignature, b: PaymentSignature): Boolean {
        return (a.origin == ORIGIN_WALLET && b.origin == ORIGIN_BANK) ||
            (a.origin == ORIGIN_BANK && b.origin == ORIGIN_WALLET)
    }

    private fun isStickyToken(token: String): Boolean {
        return token.startsWith(HINT_PREFIX) || token == WALLET_ORIGIN_TOKEN || token == BANK_ORIGIN_TOKEN
    }

    private fun isCrossChannelCheckout(a: PaymentSignature, b: PaymentSignature): Boolean {
        val bankA = isBankMethod(a.paymentMethod)
        val bankB = isBankMethod(b.paymentMethod)
        val walletA = isWalletMethod(a.paymentMethod)
        val walletB = isWalletMethod(b.paymentMethod)
        return (bankA && walletB) || (bankB && walletA)
    }

    private fun checkoutLinked(a: PaymentSignature, b: PaymentSignature): Boolean {
        val wallet = if (isBankMethod(a.paymentMethod)) b else a
        // 微信、支付宝用储蓄卡付款时，银行那条通知常常只有商户名，没有「财付通/支付宝」
        return isWalletMethod(wallet.paymentMethod)
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

    private fun isWalletHint(hint: String?): Boolean = hint != null && hint in WALLET_HINTS

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

    private val WALLET_HINTS = WALLET_METHODS

    private const val HINT_PREFIX = "ch:"
    const val ORIGIN_WALLET = "wallet"
    const val ORIGIN_BANK = "bank"
    private const val WALLET_ORIGIN_TOKEN = "ow:1"
    private const val BANK_ORIGIN_TOKEN = "ob:1"

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
