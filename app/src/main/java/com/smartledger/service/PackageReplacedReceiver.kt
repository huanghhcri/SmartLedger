package com.smartledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * App 覆盖安装完成后触发：标记需重新开启通知使用权，并尝试拉起保活/重绑。
 * （更新后系统会撤销 NLS，重绑通常无效，但开机/下次启动会弹明确提示）
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageReplaced"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.d(TAG, "Package replaced, mark NLS re-grant")
        val app = context.applicationContext
        ListenerStatus.markNeedsRegrantAfterUpdate(app)
        ListenerStatus.checkAppUpdated(app)
        try {
            KeepAliveService.start(app)
            ListenerStatus.requestRebind(app, force = true)
        } catch (e: Exception) {
            Log.e(TAG, "post-replace start failed", e)
        }
    }
}
