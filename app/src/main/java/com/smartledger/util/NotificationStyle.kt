package com.smartledger.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartledger.MainActivity
import com.smartledger.R

/**
 * 系统通知统一样式：与主页暖白 Linear / 紫色强调色一致。
 * 文案克制、无表情堆砌；小图标用应用矢量；color 用 accent。
 */
object NotificationStyle {

    const val CHANNEL_PAYMENT = "payment_detected"
    const val CHANNEL_KEEP_ALIVE = "keep_alive"
    const val CHANNEL_FLOATING = "floating_window_channel"

    private const val ID_PAYMENT_BASE = 2001
    private const val ID_CONFIRM_BASE = 3001
    const val ID_KEEP_ALIVE = 1002
    const val ID_FLOATING = 1001
    const val CHANNEL_CONFIRM = "payment_confirm"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PAYMENT,
                "收支检测",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "自动记账成功后的提醒"
                enableVibration(false)
                setShowBadge(true)
                lightColor = ContextCompat.getColor(context, R.color.accent)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONFIRM,
                "账单确认",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "模糊或可疑账单，需确认后才记入"
                enableVibration(true)
                setShowBadge(true)
                lightColor = ContextCompat.getColor(context, R.color.accent)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_KEEP_ALIVE,
                "后台自动记账",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持支付监听，可在系统设置中隐藏"
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FLOATING,
                "支付确认",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "支付确认相关前台服务"
                setShowBadge(false)
            }
        )
    }

    fun accentColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.accent)

    fun openAppPendingIntent(
        context: Context,
        requestCode: Int = 0,
        transactionId: Long? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (transactionId != null && transactionId > 0) {
                putExtra("openTransactionId", transactionId)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 自动记账成功通知
     */
    fun buildPaymentDetected(
        context: Context,
        amount: Double,
        merchant: String?,
        paymentMethod: String,
        type: String,
        transactionId: Long
    ): Notification {
        ensureChannels(context)
        val isIncome = type == "income"
        val typeLabel = if (isIncome) "收入" else "支出"
        val amountText = "¥${String.format("%.2f", amount)}"
        val detail = buildString {
            append(paymentMethod.ifBlank { "自动记账" })
            if (!merchant.isNullOrBlank()) {
                append(" · ")
                append(merchant)
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_PAYMENT)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(accentColor(context))
            .setContentTitle("$typeLabel  $amountText")
            .setContentText(detail)
            .setSubText("智能记账")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$detail\n已记入账单，点击查看详情")
                    .setBigContentTitle("$typeLabel  $amountText")
                    .setSummaryText("智能记账")
            )
            .setContentIntent(openAppPendingIntent(context, transactionId.toInt(), transactionId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun notifyPaymentDetected(
        context: Context,
        amount: Double,
        merchant: String?,
        paymentMethod: String,
        type: String,
        transactionId: Long
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = buildPaymentDetected(
            context, amount, merchant, paymentMethod, type, transactionId
        )
        manager.notify(ID_PAYMENT_BASE + (transactionId % 100000).toInt(), notification)
    }

    /**
     * 模糊账单：点击打开确认页（可改金额）。
     */
    fun notifyNeedsConfirm(
        context: Context,
        pendingId: Long,
        amount: Double,
        paymentMethod: String,
        reason: String?
    ) {
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val amountText = if (amount > 0) "¥${String.format("%.2f", amount)}" else "金额待确认"
        val detail = buildString {
            append(paymentMethod)
            if (!reason.isNullOrBlank()) {
                append(" · ")
                append(reason)
            }
            append(" · 点此确认或修改后入账")
        }
        val intent = Intent(context, com.smartledger.ConfirmPaymentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.smartledger.ConfirmPaymentActivity.EXTRA_PENDING_ID, pendingId)
        }
        val pi = PendingIntent.getActivity(
            context,
            (ID_CONFIRM_BASE + pendingId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CONFIRM)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(accentColor(context))
            .setContentTitle("待确认 $amountText")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(ID_CONFIRM_BASE + (pendingId % 100000).toInt(), notification)
    }

    fun cancelConfirm(context: Context, pendingId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(ID_CONFIRM_BASE + (pendingId % 100000).toInt())
    }

    /**
     * 保活前台通知（低调、与主页语气一致）
     */
    fun buildKeepAlive(
        context: Context,
        state: com.smartledger.service.ListenerStatus.DisplayState
    ): Notification {
        ensureChannels(context)
        val text = when (state) {
            com.smartledger.service.ListenerStatus.DisplayState.CONNECTED ->
                "正在自动记录微信 / 支付宝 / 银行卡"
            com.smartledger.service.ListenerStatus.DisplayState.NEED_PERMISSION ->
                "请开启通知使用权后才能自动记账"
            com.smartledger.service.ListenerStatus.DisplayState.RECOVERING ->
                "正在重新连接支付监听…"
        }
        return NotificationCompat.Builder(context, CHANNEL_KEEP_ALIVE)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(accentColor(context))
            .setContentTitle("智能记账")
            .setContentText(text)
            .setContentIntent(openAppPendingIntent(context, ID_KEEP_ALIVE))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 兼容旧调用 */
    fun buildKeepAlive(context: Context, connected: Boolean): Notification {
        val state = when {
            connected -> com.smartledger.service.ListenerStatus.DisplayState.CONNECTED
            com.smartledger.service.ListenerStatus.isEnabledInSettings(context) ->
                com.smartledger.service.ListenerStatus.DisplayState.RECOVERING
            else -> com.smartledger.service.ListenerStatus.DisplayState.NEED_PERMISSION
        }
        return buildKeepAlive(context, state)
    }

    fun buildForegroundPlaceholder(context: Context, text: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_FLOATING)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(accentColor(context))
            .setContentTitle("智能记账")
            .setContentText(text)
            .setContentIntent(openAppPendingIntent(context, ID_FLOATING))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
