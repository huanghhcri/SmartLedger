package com.smartledger.util

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * 自动备份调度器：每周备份到「下载/SmartLedger」（卸载后可恢复）
 */
object AutoBackupScheduler {

    private const val WORK_NAME = "auto_backup_weekly"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            7, TimeUnit.DAYS
        )
            .setInitialDelay(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d("AutoBackup", "Weekly backup scheduled")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d("AutoBackup", "Weekly backup cancelled")
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val uri = CsvExporter.export(applicationContext)
            if (uri != null) {
                Log.d("AutoBackup", "Backup completed: $uri")
            } else {
                Log.d("AutoBackup", "Skip backup: no data")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("AutoBackup", "Backup failed", e)
            Result.retry()
        }
    }
}
