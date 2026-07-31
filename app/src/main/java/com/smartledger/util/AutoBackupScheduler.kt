package com.smartledger.util

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.*
import com.smartledger.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 自动备份调度器
 * 每周自动备份一次数据到 Documents/SmartLedger/
 */
object AutoBackupScheduler {

    private const val WORK_NAME = "auto_backup_weekly"

    /**
     * 注册每周自动备份任务
     */
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

    /**
     * 取消自动备份
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d("AutoBackup", "Weekly backup cancelled")
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val db = AppDatabase.getInstance(context)
            val categories = db.categoryDao().getAll().first()
            val categoryMap = categories.associateBy { it.id }

            val transactions = db.transactionDao().getAll().first()
            if (transactions.isEmpty()) return@withContext Result.success()

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
            val fileName = "SmartLedger_Auto_${dateFormat.format(Date())}.csv"

            val backupDir = resolveBackupDir(context) ?: return@withContext Result.retry()
            val file = File(backupDir, fileName)
            FileWriter(file).use { writer ->
                writer.write("\uFEFF")
                writer.write("日期,时间,类型,金额,分类,商户,支付方式,备注,来源\n")

                val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

                for (t in transactions) {
                    val date = dateFmt.format(Date(t.transactionTime))
                    val time = timeFmt.format(Date(t.transactionTime))
                    val type = if (t.type == "expense") "支出" else "收入"
                    val category = t.categoryId?.let { categoryMap[it]?.name } ?: "未分类"
                    val merchant = escapeCsv(t.merchant ?: "")
                    val method = escapeCsv(t.paymentMethod ?: "")
                    val note = escapeCsv(t.note ?: "")
                    val source = if (t.source == "auto") "自动" else "手动"
                    writer.write("$date,$time,$type,${t.amount},$category,$merchant,$method,$note,$source\n")
                }
            }

            // 清理旧的自动备份（保留最近4个）
            val autoBackups = backupDir.listFiles()
                ?.filter { it.name.startsWith("SmartLedger_Auto_") && it.name.endsWith(".csv") }
                ?.sortedByDescending { it.lastModified() }

            if (autoBackups != null && autoBackups.size > 4) {
                autoBackups.drop(4).forEach { it.delete() }
            }

            Log.d("AutoBackup", "Backup completed: $fileName, ${transactions.size} records")
            Result.success()
        } catch (e: Exception) {
            Log.e("AutoBackup", "Backup failed", e)
            Result.retry()
        }
    }

    private fun resolveBackupDir(context: Context): File? {
        val candidates = listOf(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "SmartLedger"
            ),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SmartLedger"),
            File(context.filesDir, "SmartLedger")
        )
        for (dir in candidates) {
            try {
                if (!dir.exists()) dir.mkdirs()
                if (dir.exists() && dir.canWrite()) return dir
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
