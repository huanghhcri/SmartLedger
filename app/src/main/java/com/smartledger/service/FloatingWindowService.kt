package com.smartledger.service

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.smartledger.data.db.AppDatabase
import com.smartledger.ui.theme.SmartLedgerTheme
import com.smartledger.util.CurrencyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "FloatingWindow"

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
    private val store = ViewModelStore()
    private val scope = CoroutineScope(Dispatchers.Main)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        com.smartledger.util.NotificationStyle.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fgNotification = com.smartledger.util.NotificationStyle.buildForegroundPlaceholder(
            this,
            "正在处理…"
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    com.smartledger.util.NotificationStyle.ID_FLOATING,
                    fgNotification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(com.smartledger.util.NotificationStyle.ID_FLOATING, fgNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val amount = intent?.getDoubleExtra(EXTRA_AMOUNT, 0.0) ?: 0.0
        val merchant = intent?.getStringExtra(EXTRA_MERCHANT)
        val paymentMethod = intent?.getStringExtra(EXTRA_PAYMENT_METHOD) ?: "未知"
        val transactionId = intent?.getLongExtra(EXTRA_TRANSACTION_ID, -1) ?: -1

        // Compose 悬浮窗需要 RESUMED，否则首次绘制易白屏/崩溃
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        if (amount > 0) {
            showFloatingWindow(amount, merchant, paymentMethod, transactionId)
        }

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
                setViewTreeViewModelStoreOwner(this@FloatingWindowService)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        dismissFloatingWindow()
        try {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lifecycle destroy failed", e)
        }
        store.clear()
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = com.smartledger.ui.theme.SmartLedgerColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        com.smartledger.ui.theme.SmartLedgerColors.accentDim,
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = com.smartledger.ui.theme.SmartLedgerColors.accent,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "检测到支出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = com.smartledger.ui.theme.SmartLedgerColors.fg
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "¥${CurrencyUtil.format(amount)}",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                color = com.smartledger.ui.theme.SmartLedgerColors.expense
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!merchant.isNullOrBlank()) {
                    Text(
                        text = merchant,
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.smartledger.ui.theme.SmartLedgerColors.fgSecondary
                    )
                }
                Text(
                    text = paymentMethod,
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.smartledger.ui.theme.SmartLedgerColors.accent
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = com.smartledger.ui.theme.SmartLedgerColors.fgSecondary
                    )
                ) {
                    Text("忽略")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.smartledger.ui.theme.SmartLedgerColors.accent
                    )
                ) {
                    Text("记这笔", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
