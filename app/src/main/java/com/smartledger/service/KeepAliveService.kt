package com.smartledger.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.smartledger.util.NotificationStyle

/**
 * 前台保活：防止 OEM 杀进程后 NotificationListenerService 长期无法收到回调。
 * 周期巡检连接状态，刷新通知文案，并在断开时 requestRebind / forceReconnect。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAlive"
        private const val RECOVER_INTERVAL_MS = 90_000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }

        /** 连接状态变化时刷新保活通知文案（无需重启 Service） */
        fun refreshNotification(context: Context) {
            try {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    ?: return
                val connected = ListenerStatus.isConnected(context)
                nm.notify(
                    NotificationStyle.ID_KEEP_ALIVE,
                    NotificationStyle.buildKeepAlive(context, connected)
                )
            } catch (e: Exception) {
                Log.w(TAG, "refreshNotification failed", e)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var failStreak = 0

    private val recoverRunnable = object : Runnable {
        override fun run() {
            try {
                tickRecover()
            } finally {
                handler.postDelayed(this, RECOVER_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationStyle.ensureChannels(this)
        Log.d(TAG, "KeepAlive service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteForeground()
        handler.removeCallbacks(recoverRunnable)
        handler.postDelayed(recoverRunnable, 5_000L)
        Log.d(TAG, "KeepAlive foreground running")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(recoverRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteForeground() {
        val notification = NotificationStyle.buildKeepAlive(
            this,
            ListenerStatus.isConnected(this)
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationStyle.ID_KEEP_ALIVE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationStyle.ID_KEEP_ALIVE, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            try {
                startForeground(NotificationStyle.ID_KEEP_ALIVE, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground fallback failed", e2)
                stopSelf()
            }
        }
    }

    private fun tickRecover() {
        val enabled = ListenerStatus.isEnabledInSettings(this)
        val connected = ListenerStatus.isConnected(this)
        refreshNotification(this)

        if (!enabled) {
            failStreak = 0
            Log.d(TAG, "NLS not enabled in settings, skip rebind")
            return
        }

        if (connected) {
            failStreak = 0
            return
        }

        failStreak++
        Log.w(TAG, "NLS disconnected, recover attempt #$failStreak")
        ListenerStatus.requestRebind(this, force = true)

        // requestRebind 多次无效时，用组件开关强拉 binder（设置里仍勾选时有效）
        if (failStreak >= 2) {
            ListenerStatus.forceReconnect(this)
        }
    }
}
