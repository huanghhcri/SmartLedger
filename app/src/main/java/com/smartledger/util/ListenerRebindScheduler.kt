package com.smartledger.util

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.smartledger.service.KeepAliveService
import com.smartledger.service.ListenerStatus
import java.util.concurrent.TimeUnit

/**
 * 定时巡检通知监听（约每 15 分钟）：
 * 1. 设置仍开着但 binder 断了 → 静默 requestRebind
 * 2. 权限失效 / 重连失败 → 仅打标，等用户打开 App 再弹应用内提示（不发系统通知）
 *
 * 无法绕过「安装/更新后系统撤销权限」，那一步仍需用户点一次。
 */
object ListenerRebindScheduler {

    private const val WORK_NAME = "nls_rebind_periodic"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ListenerRebindWorker>(
            15, TimeUnit.MINUTES
        )
            .addTag(WORK_NAME)
            .build()

        // UPDATE：覆盖旧 Worker 逻辑（含失效提醒）
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.d("ListenerRebind", "Periodic permission check scheduled (15min)")
    }
}

class ListenerRebindWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        return try {
            NotificationStyle.ensureChannels(ctx)

            if (ListenerStatus.isEnabledInSettings(ctx)) {
                KeepAliveService.start(ctx)
            }

            // 检测失效：能重连则静默恢复；否则打标，打开 App 再提示
            val invalid = ListenerStatus.checkAndRecoverIfNeeded(ctx)
            if (invalid && ListenerStatus.isEnabledInSettings(ctx)) {
                ListenerStatus.forceReconnect(ctx)
            }
            KeepAliveService.refreshNotification(ctx)
            Log.d("ListenerRebind", "permission check done, invalid=$invalid")
            Result.success()
        } catch (e: Exception) {
            Log.e("ListenerRebind", "permission check worker failed", e)
            Result.retry()
        }
    }
}
