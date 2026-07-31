package com.smartledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, starting KeepAlive + rebind NLS")
            val app = context.applicationContext
            KeepAliveService.start(app)
            ListenerStatus.requestRebind(app, force = true)
            com.smartledger.util.ListenerRebindScheduler.schedule(app)
        }
    }
}
