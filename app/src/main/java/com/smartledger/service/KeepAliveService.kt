package com.smartledger.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.smartledger.util.NotificationStyle

/**
 * 前台保活：防止 OEM 杀进程后 NotificationListenerService 长期无法收到回调。
 * 需配合 ListenerStatus.requestRebind 使用。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAlive"

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
    }

    override fun onCreate() {
        super.onCreate()
        NotificationStyle.ensureChannels(this)
        Log.d(TAG, "KeepAlive service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                return START_NOT_STICKY
            }
        }
        ListenerStatus.requestRebind(applicationContext, force = true)
        Log.d(TAG, "KeepAlive foreground running")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
