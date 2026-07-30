package com.smartledger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartledger.MainActivity
import com.smartledger.R
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"
        private const val CHANNEL_ID = "payment_detected"
        private const val NOTIFICATION_ID = 2001

        // ═══ 支付类 App ═══
        private val MONITORED_PACKAGES = setOf(
            "com.tencent.mm",           // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.unionpay",             // 云闪付
            "com.ss.android.ugc.aweme", // 抖音
            "com.ss.android.ugc.live"   // 抖音极速版
        )

        // ═══ 银行类 App ═══
        private val BANK_PACKAGES = setOf(
            "com.icbc",                 // 工商银行
            "com.icbc.im",              // 工商银行(融e联)
            "com.icbc.icbcmb",          // 工商银行(手机银行)
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
            "com.bankcomm",              // 交通银行
            "com.bocomm"                 // 交通银行(新)
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

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 跳过 group summary 通知（系统聚合通知）
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val packageName = sbn.packageName
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 兼容微信等把金额放在 BIG_TEXT 的通知
        val (title, content) = extractNotificationText(extras)
        val postTime = sbn.postTime  // 通知发布时间，比 currentTimeMillis 更准确

        Log.d(TAG, "Notification: pkg=$packageName, title=$title, content=$content, postTime=$postTime")

        val text = "$title $content"

        Log.d(TAG, "=== New Notification ===")
        Log.d(TAG, "Package: $packageName")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Content: $content")
        Log.d(TAG, "Combined text: $text")

        // 判断是否包含收入或支出关键词
        val hasExpenseKeyword = EXPENSE_KEYWORDS.any { text.contains(it) }
        val hasIncomeKeyword = INCOME_KEYWORDS.any { text.contains(it) }

        Log.d(TAG, "hasExpenseKeyword=$hasExpenseKeyword, hasIncomeKeyword=$hasIncomeKeyword")

        // 判断是否是监控的App
        val isMonitoredApp = packageName in MONITORED_PACKAGES || packageName in BANK_PACKAGES

        // 如果不是监控的App，但包含银行关键词，也处理
        val isBankRelated = text.contains("银行") || text.contains("工商") || text.contains("邮政") ||
                text.contains("工行") || text.contains("邮储") || text.contains("建设") ||
                text.contains("中国银行") || text.contains("农业") || text.contains("招商") ||
                text.contains("动账通知") || text.contains("交易提醒")

        Log.d(TAG, "isMonitoredApp=$isMonitoredApp, isBankRelated=$isBankRelated")

        // 对于监控的App，即使没有明确关键词，只要有金额也尝试解析
        val hasAmount = text.contains("元") || text.contains("￥") || text.contains("¥")

        if (!hasExpenseKeyword && !hasIncomeKeyword) {
            Log.d(TAG, "No keywords found, checking if monitored app with amount...")
            // 没有关键词，只有监控的App且有金额才继续
            if (!(isMonitoredApp && hasAmount)) {
                Log.d(TAG, "Not monitored app or no amount, skipping")
                return
            }
        }

        if (!isMonitoredApp && !isBankRelated) {
            Log.d(TAG, "Not monitored app and not bank related, skipping")
            return
        }

        // 过滤明显非交易通知
        val nonTransactionKeywords = listOf("验证码", "登录", "登录验证", "可用额度", "信用额度", "账单日")
        val hasTransactionVerb = EXPENSE_KEYWORDS.any { text.contains(it) } || INCOME_KEYWORDS.any { text.contains(it) }
        if (nonTransactionKeywords.any { text.contains(it) } && !hasTransactionVerb) {
            Log.d(TAG, "Non-transaction notification, skipping")
            return
        }

        val parsed = NotificationParser.parse(title, content, packageName)
        if (parsed == null) {
            Log.d(TAG, "Parse failed for: $title - $content")
            if (isDebugEnabled()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(applicationContext, "⚠️ 解析失败: ${content.take(30)}...", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        Log.d(TAG, "Parsed: amount=${parsed.amount}, merchant=${parsed.merchant}, method=${parsed.paymentMethod}, type=${parsed.type}")

        // 调试模式下弹出提示
        if (isDebugEnabled()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val emoji = if (parsed.type == "income") "💰" else "💸"
                android.widget.Toast.makeText(applicationContext, "$emoji ${parsed.type}: ¥${parsed.amount} (${parsed.paymentMethod})", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        scope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)

                // ═══ 统一去重（金额转分 + 时间窗）═══
                val amountCents = (parsed.amount * 100).toLong()
                val duplicate = DedupHelper.findDuplicate(db.transactionDao(), amountCents, parsed.type, parsed.merchant, postTime)

                if (duplicate != null) {
                    Log.d(TAG, "Duplicate: existing=${duplicate.paymentMethod}, new=${parsed.paymentMethod}, amount=${parsed.amount}")
                    DedupHelper.mergeIfDuplicate(db.transactionDao(), duplicate, parsed.paymentMethod, parsed.merchant)
                    return@launch
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
                    transactionTime = postTime  // 使用通知发布时间
                )
                val id = db.transactionDao().insert(transaction)
                Log.d(TAG, "Transaction saved: id=$id, type=${parsed.type}")

                // 智能分类
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
                            val categoryName = categories.find { it.id == categoryId }?.name
                            Log.d(TAG, "Auto categorized: $categoryName (id=$categoryId)")
                        }
                    } else {
                        Log.d(TAG, "No category match for merchant: ${parsed.merchant}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Auto categorize failed", e)
                }

                // 发系统通知
                showPaymentNotification(parsed.amount, parsed.merchant, parsed.paymentMethod, parsed.type, id)

                // 尝试弹悬浮窗（在主线程执行）
                try {
                    if (android.provider.Settings.canDrawOverlays(applicationContext)) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            FloatingWindowService.show(
                                context = applicationContext,
                                amount = parsed.amount,
                                merchant = parsed.merchant,
                                paymentMethod = parsed.paymentMethod,
                                transactionId = id
                            )
                        }
                    } else {
                        Log.d(TAG, "Overlay permission not granted, skip floating window")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Floating window failed, notification already shown", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transaction", e)
            }
        }
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
        // d) EXTRA_SUB_TEXT
        addIfNew(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        // e) EXTRA_INFO_TEXT
        addIfNew(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))

        val content = parts.joinToString(" ")

        Log.d(TAG, "extractNotificationText: title='$title', content='$content' (from ${parts.size} parts)")

        return title to content
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "收支检测",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "检测到收入或支出行为时显示通知"
            enableVibration(true)
            setShowBadge(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showPaymentNotification(
        amount: Double,
        merchant: String?,
        paymentMethod: String,
        type: String,
        transactionId: Long
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openTransactionId", transactionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val merchantText = if (!merchant.isNullOrBlank()) " · $merchant" else ""
        val typeText = if (type == "income") "收入" else "支出"
        val emoji = if (type == "income") "💰" else "💸"

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji 检测到$typeText：￥${String.format("%.2f", amount)}")
            .setContentText("$paymentMethod$merchantText · 点击打开记账")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + transactionId.toInt(), notification)
    }

    private fun isDebugEnabled(): Boolean {
        return getSharedPreferences("smart_ledger", MODE_PRIVATE).getBoolean("debug_toasts", false)
    }
}
