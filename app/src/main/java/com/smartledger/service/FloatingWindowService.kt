package com.smartledger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.smartledger.MainActivity
import com.smartledger.R
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Transaction
import com.smartledger.data.db.entity.Category
import com.smartledger.ui.theme.SmartLedgerTheme
import com.smartledger.util.CurrencyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingWindow"
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_MERCHANT = "extra_merchant"
        const val EXTRA_PAYMENT_METHOD = "extra_payment_method"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"

        fun show(context: Context, amount: Double, merchant: String?, paymentMethod: String, transactionId: Long) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_MERCHANT, merchant)
                putExtra(EXTRA_PAYMENT_METHOD, paymentMethod)
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val scope = CoroutineScope(Dispatchers.Main)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        val amount = intent?.getDoubleExtra(EXTRA_AMOUNT, 0.0) ?: 0.0
        val merchant = intent?.getStringExtra(EXTRA_MERCHANT)
        val paymentMethod = intent?.getStringExtra(EXTRA_PAYMENT_METHOD) ?: "未知"
        val transactionId = intent?.getLongExtra(EXTRA_TRANSACTION_ID, -1) ?: -1

        if (amount > 0) {
            showFloatingWindow(amount, merchant, paymentMethod, transactionId)
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_NOT_STICKY
    }

    private fun showFloatingWindow(amount: Double, merchant: String?, paymentMethod: String, transactionId: Long) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted, skipping floating window")
            stopSelf()
            return
        }

        // 移除旧的悬浮窗
        dismissFloatingWindow()

        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = 100
            }

            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
                setContent {
                    SmartLedgerTheme {
                        FloatingPaymentDialog(
                            amount = amount,
                            merchant = merchant,
                            paymentMethod = paymentMethod,
                            onConfirm = {
                                scope.launch {
                                    confirmTransaction(transactionId)
                                    dismissFloatingWindow()
                                }
                            },
                            onIgnore = {
                                scope.launch {
                                    deleteTransaction(transactionId)
                                    dismissFloatingWindow()
                                }
                            },
                            onDismiss = {
                                dismissFloatingWindow()
                            }
                        )
                    }
                }
            }

            floatingView = composeView
            windowManager.addView(composeView, layoutParams)
            Log.d(TAG, "Floating window shown for amount=$amount")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating window", e)
            floatingView = null
            stopSelf()
        }
    }

    private suspend fun confirmTransaction(transactionId: Long) {
        // 交易已经在通知监听时写入了，这里只是标记为已确认
        // 可以后续扩展：弹出分类选择
        stopSelf()
    }

    private suspend fun deleteTransaction(transactionId: Long) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val transaction = db.transactionDao().getById(transactionId)
            if (transaction != null) {
                db.transactionDao().delete(transaction)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    private fun dismissFloatingWindow() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
                Log.d(TAG, "Floating window dismissed")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dismiss floating window", e)
            }
        }
        floatingView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "支付确认悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示支付确认悬浮窗"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("智能记账")
                .setContentText("正在处理支付确认...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("智能记账")
                .setContentText("正在处理支付确认...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        dismissFloatingWindow()
        super.onDestroy()
    }
}

@Composable
private fun FloatingPaymentDialog(
    amount: Double,
    merchant: String?,
    paymentMethod: String,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("💰", fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "检测到支付",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 金额
            Text(
                text = "￥${CurrencyUtil.format(amount)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 商户和支付方式
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!merchant.isNullOrBlank()) {
                    Text(
                        text = merchant,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = paymentMethod,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 忽略按钮
                OutlinedButton(
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("忽略")
                }

                // 确认记账按钮
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("⚠", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("记这笔", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
