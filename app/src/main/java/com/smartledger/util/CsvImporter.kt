package com.smartledger.util

import android.content.Context
import android.net.Uri
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object CsvImporter {

    /**
     * 按文件名恢复（扫描 MediaStore + 各备份目录）
     */
    suspend fun restore(context: Context, fileName: String): Int = withContext(Dispatchers.IO) {
        val entry = BackupStorage.listBackups(context).firstOrNull { it.fileName == fileName }
            ?: return@withContext -1
        restoreFromEntry(context, entry)
    }

    suspend fun restoreFromEntry(context: Context, entry: BackupStorage.Entry): Int =
        withContext(Dispatchers.IO) {
            val reader = BackupStorage.openReader(context, entry) ?: return@withContext -1
            reader.use { restoreFromReader(context, it) }
        }

    /** 从用户选择的文件 Uri 恢复（SAF） */
    suspend fun restoreFromUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext -1
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                restoreFromReader(context, reader)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private suspend fun restoreFromReader(context: Context, reader: BufferedReader): Int {
        return try {
            val db = AppDatabase.getInstance(context)
            val categories = db.categoryDao().getAllOnce()
            val categoryMap = categories.associateBy { "${it.type}:${it.name}" }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

            var count = 0
            var headerSkipped = false

            reader.lineSequence().forEach { raw ->
                val line = raw.removePrefix("\uFEFF").trim()
                if (line.isBlank()) return@forEach
                if (!headerSkipped) {
                    headerSkipped = true
                    if (line.contains("日期") || line.contains("金额")) return@forEach
                }
                try {
                    val fields = parseCsvLine(line)
                    if (fields.size < 7) return@forEach

                    val dateStr = fields[0]
                    val timeStr = fields[1]
                    val type = fields[2]
                    val amountStr = fields[3]
                    val categoryName = fields[4]
                    val merchant = fields[5]
                    val method = fields[6]
                    val note = if (fields.size > 7) fields[7] else ""
                    val source = if (fields.size > 8) fields[8] else "手动"

                    val amount = amountStr.toDoubleOrNull() ?: return@forEach
                    if (amount <= 0) return@forEach
                    val typeEn = if (type == "收入") "income" else "expense"

                    val dateTime = try {
                        val date = dateFormat.parse(dateStr)
                        val time = timeFormat.parse(timeStr)
                        if (date != null && time != null) {
                            val cal = Calendar.getInstance()
                            cal.time = date
                            val timeCal = Calendar.getInstance()
                            timeCal.time = time
                            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                            cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.timeInMillis
                        } else {
                            System.currentTimeMillis()
                        }
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    val merchantVal = merchant.ifBlank { null }
                    val near = db.transactionDao().getByTimeRangeOnce(dateTime - 1000, dateTime + 1000)
                    val amountCents = CurrencyUtil.toCents(amount)
                    val already = near.any { existing ->
                        CurrencyUtil.toCents(existing.amount) == amountCents &&
                            existing.type == typeEn &&
                            (existing.merchant ?: "") == (merchantVal ?: "")
                    }
                    if (already) return@forEach

                    val categoryId = categoryMap["$typeEn:$categoryName"]?.id
                        ?: categoryMap["$typeEn:其他"]?.id

                    val transaction = Transaction(
                        amount = amount,
                        type = typeEn,
                        categoryId = categoryId,
                        merchant = merchantVal,
                        paymentMethod = method.ifBlank { null },
                        note = note.ifBlank { null },
                        source = if (source == "自动") "auto" else "manual",
                        notificationKey = null,
                        transactionTime = dateTime
                    )

                    db.transactionDao().insert(transaction)
                    count++
                } catch (_: Exception) {
                }
            }

            count
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    fields.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        fields.add(current.toString().trim())
        return fields
    }
}
