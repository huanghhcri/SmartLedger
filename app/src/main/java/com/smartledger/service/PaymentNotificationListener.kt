package com.smartledger.service

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"

        // ═══ 支付 / 电商收银台 ═══
        private val MONITORED_PACKAGES = setOf(
            "com.tencent.mm",              // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.unionpay",                // 云闪付
            "com.ss.android.ugc.aweme",    // 抖音
            "com.ss.android.ugc.live",     // 抖音极速版
            "com.jd.jrapp",                // 京东金融（银行卡支付确认）
            "com.jingdong.app.mall",       // 京东
            "com.jd.jdlite",               // 京东极速版
            "com.taobao.taobao",           // 淘宝（银行卡支付）
            "com.tmall.wireless"           // 天猫
        )

        // ═══ 银行类 App（精确包名；另有 contains 宽松匹配）═══
        private val BANK_PACKAGES = setOf(
            "com.icbc",                 // 工商银行
            "com.icbc.im",              // 工商银行(融e联)
            "com.icbc.icbcmb",          // 工商银行(手机银行)
            "com.icbc.android",         // 工商银行(部分机型)
            "com.chinapost.pbs",        // 邮政储蓄银行
            "com.psbc",                 // 邮政储蓄银行(新)
            "com.ccb.start",            // 建设银行
            "com.ccb.main",             // 建设银行(新)
            "com.chinamworld.bocmbci",  // 中国银行
            "com.boc.bocsoft.bocmbs",   // 中国银行(新)
            "com.abchina.abcpocket",    // 农业银行
            "com.android.bankabc",      // 农业银行(新)
            "cmb.pb",                   // 招商银行
            "cmb.b2c",                  // 招商银行(新)
            "com.chinamworld.main",     // 中信银行
            "com.pingan.paces.ccms",    // 平安银行
            "com.spdb.mobilebank",      // 浦发银行
            "com.cmbc",                 // 民生银行
            "com.cebbank",              // 光大银行
            "com.cib",                  // 兴业银行
            "com.bankcomm",             // 交通银行
            "com.bocomm"                // 交通银行(新)
        )

        /** 包名片段：兼容厂商定制包名（如 com.icbc.xxx） */
        private val BANK_PACKAGE_HINTS = listOf(
            "icbc", "psbc", "chinapost", "ccb", "abchina", "bankabc",
            "bocmbci", "bocsoft", "cmb.pb", "cmb.b2c", "pingan.paces",
            "spdb", "cmbc", "cebbank", "bankcomm", "bocomm", "cib"
        )

        // ═══ 支出关键词 ═══
        private val EXPENSE_KEYWORDS = listOf(
            "付款", "支付", "消费", "支出", "转出", "扣款", "已付", "成功付款",
            "购买", "缴费", "还款", "充值", "已扣", "交易支出"
        )

        // ═══ 收入关键词 ═══
        private val INCOME_KEYWORDS = listOf(
            "退款", "退回", "到账", "收款", "转入", "已收", "存入",
            "收入", "入账", "已到账", "退款成功", "红包", "奖励",
            "工资", "奖金", "利息", "返现", "报销"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        com.smartledger.util.NotificationStyle.ensureChannels(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        ListenerStatus.setConnected(applicationContext, true)
        ListenerStatus.clearPendingInAppPrompt(applicationContext)
        ListenerStatus.clearAfterUpdatePrompt(applicationContext)
        KeepAliveService.start(applicationContext)
        KeepAliveService.refreshNotification(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected, will requestRebind")
        ListenerStatus.setConnected(applicationContext, false)
        KeepAliveService.refreshNotification(applicationContext)
        KeepAliveService.start(applicationContext)
        // 只 requestRebind，不要短时间 forceReconnect（会拆组件导致一直断开）
        mainHandler.postDelayed({
            ListenerStatus.requestRebind(applicationContext, force = true)
        }, 1500L)
        mainHandler.postDelayed({
            ListenerStatus.requestRebind(applicationContext, force = true)
        }, 6000L)
    }

    override fun onDestroy() {
        // 服务销毁 ≠ 权限撤销；勿标断开，否则冷启动/组件抖动会一直显示「已断开」
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 能收到通知 = binder 已连接（修复误标断开）
        ListenerStatus.markAliveFromNotification(applicationContext)

        val packageName = sbn.packageName
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 兼容各渠道把金额放在 BIG_TEXT / 同组子通知里
        var (title, content) = extractNotificationText(extras)
        var postTime = sbn.postTime  // 通知发布时间，比 currentTimeMillis 更准确
        var text = NotificationParser.normalizeNotificationText("$title $content")

        val isGroupSummary =
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isMonitoredEarly = isMonitoredPackage(packageName)
        val isBankPkgEarly = isBankPackage(packageName)

        // 全渠道聚合补全：系统栏可能显示「[N条]…金额」，extras 却只有「[N条]」
        // 微信/支付宝/云闪付/抖音/京东淘宝/各银行 App 均适用
        val needGroupEnrich = isMonitoredEarly &&
            (isGroupSummary || NotificationParser.looksLikeGroupedSummary(text)) &&
            !NotificationParser.hasPayableAmountSignal(text) &&
            !NotificationParser.hasBankLedgerSignal(text)
        if (needGroupEnrich) {
            val enriched = enrichPaymentTextFromGroup(sbn)
            if (enriched != null) {
                title = enriched.title
                content = enriched.content
                postTime = enriched.postTime
                text = NotificationParser.normalizeNotificationText("$title $content")
                Log.d(TAG, "Enriched grouped pay notice ($packageName): $text")
            }
        }

        // 聚合摘要：仅监控渠道且补全/原文已像真实账务时才解析，其它 App 的摘要一律跳过
        if (isGroupSummary) {
            val looksLikePay = NotificationParser.looksLikeLedgerChild(text) ||
                text.contains("已支付") || text.contains("付款") || text.contains("动账")
            if (!isMonitoredEarly || !looksLikePay) {
                Log.d(TAG, "Skip group summary: pkg=$packageName text=$text")
                return
            }
            Log.d(TAG, "Parse payment group summary: pkg=$packageName bank=$isBankPkgEarly text=$text")
        }

        Log.d(TAG, "=== New Notification ===")
        Log.d(TAG, "Package: $packageName")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Content: $content")
        Log.d(TAG, "Combined text: $text")

        // 白条 / 花呗 / 借呗 / 金条等信贷支付：不记账
        if (NotificationParser.isCreditProductPayment(text)) {
            Log.d(TAG, "Credit product payment, skip: $text")
            return
        }

        // 营销 / 额度 / 物流 / 提现提醒等
        if (NotificationParser.isPromotionalOrNonPayment(text)) {
            Log.d(TAG, "Promotional/non-payment, skip: $text")
            return
        }

        // 判断是否包含收入或支出关键词
        val hasExpenseKeyword = EXPENSE_KEYWORDS.any { text.contains(it) }
        val hasIncomeKeyword = INCOME_KEYWORDS.any { text.contains(it) }

        Log.d(TAG, "hasExpenseKeyword=$hasExpenseKeyword, hasIncomeKeyword=$hasIncomeKeyword")

        val isMonitoredApp = isMonitoredPackage(packageName)
        val isBankPkg = isBankPackage(packageName)
        val isShopping = packageName.contains("jingdong") || packageName.contains("jd.jr") ||
            packageName.contains("jdlite") || packageName.contains("taobao") ||
            packageName.contains("tmall")

        // 正文含银行/动账/支付确认等也处理（覆盖未列入包名的银行 App）
        val isBankRelated = isBankPkg ||
                text.contains("银行") || text.contains("工商") || text.contains("邮政") ||
                text.contains("工行") || text.contains("邮储") || text.contains("建设") ||
                text.contains("中国银行") || text.contains("农业") || text.contains("招商") ||
                text.contains("动账通知") || text.contains("交易提醒") ||
                text.contains("支付信息确认") || text.contains("储蓄卡") || text.contains("借记卡") ||
                Regex("尾号\\d{4}卡").containsMatchIn(text)

        Log.d(TAG, "isMonitoredApp=$isMonitoredApp, isBankRelated=$isBankRelated, isBankPkg=$isBankPkg")

        // 银行类：必须有动账信号，禁止营销/余额/还款提醒误记
        if (isBankRelated &&
            !NotificationParser.hasBankLedgerSignal(text) &&
            !NotificationParser.hasStrongPaymentSignal(text)
        ) {
            Log.d(TAG, "Bank-related without ledger signal, skip: $text")
            return
        }

        // 金额：含「元」或裸数字（工行截断：支出(...)35....）
        val hasAmount = text.contains("元") || text.contains("￥") || text.contains("¥") ||
                Regex("支付\\s*[\\d.]+").containsMatchIn(text) ||
                Regex("支出\\([^)]*\\)\\s*[\\d.]+").containsMatchIn(text) ||
                Regex("消费\\([^)]*\\)\\s*[\\d.]+").containsMatchIn(text)

        val isChatPay = packageName == "com.tencent.mm" ||
            packageName.contains("AlipayGphone") ||
            packageName.contains("ugc.aweme") ||
            packageName.contains("ugc.live")

        // 微信/支付宝/抖音/电商：无明确支付确认时直接跳过（防企业号「每天5元干饭」）
        if ((isShopping || isChatPay) && !NotificationParser.hasStrongPaymentSignal(text)) {
            Log.d(TAG, "Chat/shopping without pay confirm, skip: $text")
            return
        }

        if (!hasExpenseKeyword && !hasIncomeKeyword) {
            Log.d(TAG, "No keywords found, checking if monitored app with amount...")
            // 银行动账截断可能只有「支出(...)35」；其它仍需关键词或强信号
            val bankOk = isBankRelated && NotificationParser.hasBankLedgerSignal(text) && hasAmount
            if (isShopping || isChatPay || (!bankOk && !(isMonitoredApp && hasAmount))) {
                Log.d(TAG, "Not monitored app or no amount (or promo app), skipping")
                return
            }
        }

        if (!isMonitoredApp && !isBankRelated) {
            Log.d(TAG, "Not monitored app and not bank related, skipping")
            return
        }

        val parsed = NotificationParser.parse(title, content, packageName)
        if (parsed == null) {
            Log.d(TAG, "Parse failed for: $title - $content")
            if (isDebugEnabled()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        applicationContext,
                        "未能识别：${content.take(28)}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        Log.d(
            TAG,
            "Parsed: amount=${parsed.amount}, merchant=${parsed.merchant}, " +
                "method=${parsed.paymentMethod}, type=${parsed.type}, confidence=${parsed.confidence}"
        )

        // 调试模式下弹出提示（文案与主页语气一致）
        if (isDebugEnabled()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val typeLabel = if (parsed.type == "income") "收入" else "支出"
                val conf = if (parsed.confidence == ParseConfidence.UNCERTAIN) " · 待确认" else ""
                android.widget.Toast.makeText(
                    applicationContext,
                    "$typeLabel ¥${String.format("%.2f", parsed.amount)} · ${parsed.paymentMethod}$conf",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        // 模糊 / 全部需确认：先弹确认，确认前不落库
        if (shouldAskConfirm(parsed)) {
            askUserConfirm(parsed, postTime)
            return
        }

        scope.launch {
            try {
                saveAutoTransaction(parsed, postTime)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transaction", e)
            }
        }
    }

    /** 设置：模糊需确认（默认开）；或全部自动记账都确认 */
    private fun shouldAskConfirm(parsed: ParsedPayment): Boolean {
        val prefs = getSharedPreferences("smart_ledger", MODE_PRIVATE)
        if (prefs.getBoolean("confirm_all_auto", false)) return true
        if (!prefs.getBoolean("confirm_uncertain", true)) return false
        return parsed.confidence == ParseConfidence.UNCERTAIN
    }

    private fun askUserConfirm(parsed: ParsedPayment, postTime: Long) {
        val pendingId = PendingConfirmStore.put(
            amount = parsed.amount,
            type = parsed.type,
            merchant = parsed.merchant,
            paymentMethod = parsed.paymentMethod,
            notificationKey = parsed.notificationKey,
            transactionTime = postTime,
            reason = parsed.uncertainReason ?: "识别结果不够确定",
            rawSnippet = parsed.rawSnippet
        )
        Log.d(TAG, "Ask confirm pendingId=$pendingId reason=${parsed.uncertainReason}")
        com.smartledger.util.NotificationStyle.notifyNeedsConfirm(
            applicationContext,
            pendingId,
            parsed.amount,
            parsed.paymentMethod,
            parsed.uncertainReason
        )
        try {
            val intent = android.content.Intent(
                applicationContext,
                com.smartledger.ConfirmPaymentActivity::class.java
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(com.smartledger.ConfirmPaymentActivity.EXTRA_PENDING_ID, pendingId)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // 部分机型后台禁弹 Activity：仍可通过「待确认」通知点开
            Log.w(TAG, "Confirm activity blocked, use notification tap", e)
        }
    }

    private suspend fun saveAutoTransaction(parsed: ParsedPayment, postTime: Long) {
        val db = AppDatabase.getInstance(applicationContext)

        val amountCents = com.smartledger.util.CurrencyUtil.toCents(parsed.amount)
        val duplicate = DedupHelper.findDuplicate(
            db.transactionDao(),
            amountCents,
            parsed.type,
            parsed.merchant,
            parsed.paymentMethod,
            postTime
        )

        if (duplicate != null) {
            Log.d(TAG, "Duplicate: existing=${duplicate.paymentMethod}, new=${parsed.paymentMethod}, amount=${parsed.amount}")
            DedupHelper.mergeIfDuplicate(db.transactionDao(), duplicate, parsed.paymentMethod, parsed.merchant)
            return
        }

        val transaction = Transaction(
            amount = parsed.amount,
            type = parsed.type,
            categoryId = null,
            merchant = parsed.merchant,
            paymentMethod = parsed.paymentMethod,
            note = null,
            source = "auto",
            notificationKey = parsed.notificationKey,
            transactionTime = postTime
        )
        val id = db.transactionDao().insert(transaction)
        Log.d(TAG, "Transaction saved: id=$id, type=${parsed.type}")

        try {
            val categories = db.categoryDao().getAllOnce()
            val categoryId = SmartCategorizer.categorize(
                merchant = parsed.merchant,
                paymentMethod = parsed.paymentMethod,
                note = null,
                categories = categories,
                type = parsed.type
            )
            if (categoryId != null) {
                val savedTransaction = db.transactionDao().getById(id)
                if (savedTransaction != null) {
                    db.transactionDao().update(savedTransaction.copy(categoryId = categoryId))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto categorize failed", e)
        }

        com.smartledger.util.NotificationStyle.notifyPaymentDetected(
            applicationContext,
            parsed.amount,
            parsed.merchant,
            parsed.paymentMethod,
            parsed.type,
            id
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    /**
     * 从通知 extras 中提取完整的标题和正文
     * 兼容微信等把金额放在 EXTRA_BIG_TEXT / EXTRA_TEXT_LINES 的通知
     */
    private fun extractNotificationText(extras: android.os.Bundle): Pair<String, String> {
        // 标题：优先 EXTRA_TITLE，其次 EXTRA_TITLE_BIG
        val title = (
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
                ?: ""
        )

        // 正文：按优先级收集各字段，去重拼接
        val parts = mutableListOf<String>()

        fun addIfNew(text: CharSequence?) {
            val trimmed = text?.toString()?.trim() ?: return
            if (trimmed.isEmpty()) return
            // 查找已存在且与新文本有包含关系的条目
            val existingIndex = parts.indexOfFirst { it.contains(trimmed) || trimmed.contains(it) }
            if (existingIndex >= 0) {
                // 新文本更长 → 替换旧的短文本（保留信息量更大的版本）
                if (trimmed.length > parts[existingIndex].length) {
                    parts[existingIndex] = trimmed
                }
                // 新文本更短或相同 → 跳过
            } else {
                parts.add(trimmed)
            }
        }

        // a) EXTRA_TEXT（标准正文）
        addIfNew(extras.getCharSequence(Notification.EXTRA_TEXT))
        // b) EXTRA_BIG_TEXT（展开后的长文本）
        addIfNew(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        // c) EXTRA_TEXT_LINES（CharSequence[]，逐行拼接）
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null) {
            for (line in lines) {
                addIfNew(line)
            }
        }
        // d) EXTRA_SUB_TEXT / SUMMARY
        addIfNew(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        addIfNew(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        // e) EXTRA_INFO_TEXT
        addIfNew(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        // f) MessagingStyle 消息列表（部分机型微信聚合走这里）
        try {
            @Suppress("DEPRECATION")
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null) {
                for (msg in messages) {
                    if (msg is android.os.Bundle) {
                        addIfNew(msg.getCharSequence("text"))
                    }
                }
            }
        } catch (_: Exception) {
        }

        val content = parts.joinToString(" ")

        Log.d(TAG, "extractNotificationText: title='$title', content='$content' (from ${parts.size} parts)")

        return title to content
    }

    private data class EnrichedPayText(
        val title: String,
        val content: String,
        val postTime: Long
    )

    /**
     * 从同组 / 同包最近活跃通知中找出带真实账务金额的子通知。
     * 适用于微信/支付宝聚合、银行 App 折叠动账、云闪付/电商收银台等。
     */
    private fun enrichPaymentTextFromGroup(sbn: StatusBarNotification): EnrichedPayText? {
        val active = try {
            activeNotifications ?: return null
        } catch (e: Exception) {
            Log.w(TAG, "activeNotifications unavailable", e)
            return null
        }
        val groupKey = sbn.groupKey
        val notifGroup = sbn.notification.group
        val candidates = active
            .asSequence()
            .filter { it.packageName == sbn.packageName }
            .filter { it.key != sbn.key }
            // 跳过其它摘要，避免套娃
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .filter { child ->
                val sameGroup = (!groupKey.isNullOrBlank() && child.groupKey == groupKey) ||
                    (!notifGroup.isNullOrBlank() && child.notification.group == notifGroup)
                val recentSamePkg = kotlin.math.abs(sbn.postTime - child.postTime) <= 180_000L
                // 有 group：同组；无 group：同包近 3 分钟（银行/厂商常不带 group）
                sameGroup || ((groupKey.isNullOrBlank() && notifGroup.isNullOrBlank()) && recentSamePkg)
            }
            .sortedByDescending { it.postTime }
            .toList()

        for (child in candidates) {
            val extras = child.notification?.extras ?: continue
            val (t, c) = extractNotificationText(extras)
            val combined = NotificationParser.normalizeNotificationText("$t $c")
            if (NotificationParser.looksLikeLedgerChild(combined)) {
                Log.d(TAG, "Found ledger child: pkg=${child.packageName} key=${child.key} text=$combined")
                return EnrichedPayText(t, c, child.postTime)
            }
        }
        return null
    }

    private fun isMonitoredPackage(packageName: String): Boolean {
        if (packageName in MONITORED_PACKAGES || packageName in BANK_PACKAGES) return true
        if (BANK_PACKAGE_HINTS.any { packageName.contains(it, ignoreCase = true) }) return true
        if (packageName.contains("jd.jr") || packageName.contains("jingdong")) return true
        return false
    }

    private fun isBankPackage(packageName: String): Boolean {
        if (packageName in BANK_PACKAGES) return true
        return BANK_PACKAGE_HINTS.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun isDebugEnabled(): Boolean {
        return getSharedPreferences("smart_ledger", MODE_PRIVATE).getBoolean("debug_toasts", false)
    }
}
