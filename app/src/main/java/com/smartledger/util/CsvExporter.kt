package com.smartledger.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    suspend fun export(context: Context, yearMonth: String? = null): Uri? = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val categories = db.categoryDao().getAll().first()
            val categoryMap = categories.associateBy { it.id }

            val transactions = if (yearMonth != null) {
                val start = DateUtil.getMonthStartTime(yearMonth)
                val end = DateUtil.getMonthEndTime(yearMonth)
                db.transactionDao().getByTimeRange(start, end).first()
            } else {
                db.transactionDao().getAll().first()
            }

            if (transactions.isEmpty()) return@withContext null

            val fileName = if (yearMonth != null) {
                "SmartLedger_${yearMonth}.csv"
            } else {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                "SmartLedger_${dateFormat.format(Date())}.csv"
            }

            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "SmartLedger"
            )
            if (!exportDir.exists()) exportDir.mkdirs()

            val file = File(exportDir, fileName)
            FileWriter(file).use { writer ->
                // CSV Header (with BOM for Excel compatibility)
                writer.write("\uFEFF")
                writer.write("日期,时间,类型,金额,分类,商户,支付方式,备注,来源\n")

                // CSV Data
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

                for (t in transactions) {
                    val date = dateFormat.format(Date(t.transactionTime))
                    val time = timeFormat.format(Date(t.transactionTime))
                    val type = if (t.type == "expense") "支出" else "收入"
                    val category = t.categoryId?.let { categoryMap[it]?.name } ?: "未分类"
                    val merchant = escapeCsv(t.merchant ?: "")
                    val method = escapeCsv(t.paymentMethod ?: "")
                    val note = escapeCsv(t.note ?: "")
                    val source = if (t.source == "auto") "自动" else "手动"

                    writer.write("$date,$time,$type,${t.amount},$category,$merchant,$method,$note,$source\n")
                }
            }

            // Return content URI via FileProvider
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, file)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun shareFile(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出记账数据"))
    }
}
