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
 * 前台保活 + 监听重连巡检。
 * 仅使用 requestRebind；不强行反复 toggle 组件（会导致部分机型永久断连）。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAlive"
        /** 未连上时更勤快；已连上则放慢 */
        private const val RECOVER_FAST_MS = 15_000L
        private const val RECOVER_SLOW_MS = 90_000L
        /** 已连接时仍周期性轻量 rebind，防止 OEM 假死却仍显示「正在记录」 */
        private const val SOFT_REBIND_MS = 5 * 60_000L

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

        fun refreshNotification(context: Context) {
            try {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    ?: return
                nm.notify(
                    NotificationStyle.ID_KEEP_ALIVE,
                    NotificationStyle.buildKeepAlive(context, ListenerStatus.displayState(context))
                )
            } catch (e: Exception) {
                Log.w(TAG, "refreshNotification failed", e)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var failStreak = 0
    private var lastSoftRebindAt = 0L

    private val recoverRunnable = object : Runnable {
        override fun run() {
            var next = RECOVER_SLOW_MS
            try {
                next = tickRecover()
            } finally {
                handler.postDelayed(this, next)
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
        // 立刻尝试一次重绑，再进入周期巡检
        if (ListenerStatus.isEnabledInSettings(this) && !ListenerStatus.isConnected(this)) {
            ListenerStatus.requestRebind(this, force = true)
        }
        handler.removeCallbacks(recoverRunnable)
        handler.postDelayed(recoverRunnable, 3_000L)
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
            ListenerStatus.displayState(this)
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

    /** @return 下次巡检间隔 */
    private fun tickRecover(): Long {
        refreshNotification(this)

        if (!ListenerStatus.isEnabledInSettings(this)) {
            failStreak = 0
            Log.d(TAG, "NLS not enabled in settings")
            return RECOVER_SLOW_MS
        }

        // 本进程 binder 已确认连接：偶尔轻量 rebind 防假死
        if (ListenerStatus.isBinderConnected()) {
            failStreak = 0
            val now = System.currentTimeMillis()
            if (now - lastSoftRebindAt >= SOFT_REBIND_MS) {
                lastSoftRebindAt = now
                ListenerStatus.requestRebind(this, force = true)
                Log.d(TAG, "soft rebind while connected")
            }
            return RECOVER_SLOW_MS
        }

        // prefs 曾连接但本进程未收到 onListenerConnected → 显示「正在重新连接」并重绑
        failStreak++
        Log.w(
            TAG,
            "NLS binder not ready (wasConnected=${ListenerStatus.wasConnectedBefore(this)}), " +
                "attempt #$failStreak"
        )
        ListenerStatus.requestRebind(this, force = true)

        // 仅在连续失败较久后，且受冷却限制下尝试一次强恢复
        if (failStreak == 8) {
            ListenerStatus.forceReconnect(this, bypassCooldown = false)
        }

        return RECOVER_FAST_MS
    }
}
