package com.smartledger.util

import android.content.Context
import android.os.Environment
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object CsvImporter {

    /**
     * 从备份目录恢复数据（幂等：相同时间+金额+类型+商户跳过）
     * @return 新插入条数；失败返回 -1
     */
    suspend fun restore(context: Context, fileName: String): Int = withContext(Dispatchers.IO) {
        try {
            val file = resolveBackupFile(context, fileName) ?: return@withContext -1

            val db = AppDatabase.getInstance(context)
            val categories = db.categoryDao().getAllOnce()
            // 支出/收入都有「其他」，必须用 type+name 区分
            val categoryMap = categories.associateBy { "${it.type}:${it.name}" }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

            var count = 0
            BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.mark(3)
                val bom = CharArray(3)
                reader.read(bom)
                if (!(bom[0] == '\uFEFF')) {
                    reader.reset()
                }

                reader.readLine() // header

                reader.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach
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
                        // 幂等：附近已有同键记录则跳过，避免重复恢复翻倍
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
                        // 跳过解析失败的行
                    }
                }
            }

            count
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun resolveBackupFile(context: Context, fileName: String): File? {
        val publicFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "SmartLedger/$fileName"
        )
        if (publicFile.exists()) return publicFile

        val appFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SmartLedger/$fileName")
        if (appFile.exists()) return appFile

        val internal = File(context.filesDir, "SmartLedger/$fileName")
        return if (internal.exists()) internal else null
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
