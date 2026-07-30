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
     * 从 Documents/SmartLedger/ 目录下的 CSV 文件恢复数据
     * @param fileName 文件名，如 "SmartLedger_20260729.csv"
     * @return 恢复的记录数，失败返回 -1
     */
    suspend fun restore(context: Context, fileName: String): Int = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "SmartLedger"
            )
            val file = File(backupDir, fileName)
            if (!file.exists()) return@withContext -1

            val db = AppDatabase.getInstance(context)
            val categories = db.categoryDao().getAllOnce()
            val categoryMap = categories.associateBy { it.name }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

            var count = 0
            BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8)).use { reader ->
                // 跳过 BOM
                reader.mark(3)
                val bom = CharArray(3)
                reader.read(bom)
                if (!bom.contentEquals(charArrayOf('\uFEFF', '\u0000', '\u0000')) &&
                    !bom.contentEquals(charArrayOf('\uFEFF', '\u0000')) &&
                    !bom.contentEquals(charArrayOf('\uFEFF'))) {
                    reader.reset()
                }

                // 跳过标题行
                reader.readLine()

                // 逐行解析
                reader.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val fields = parseCsvLine(line)
                        if (fields.size < 7) return@forEach

                        val dateStr = fields[0]    // 日期
                        val timeStr = fields[1]    // 时间
                        val type = fields[2]       // 类型：支出/收入
                        val amountStr = fields[3]  // 金额
                        val categoryName = fields[4] // 分类
                        val merchant = fields[5]   // 商户
                        val method = fields[6]     // 支付方式
                        val note = if (fields.size > 7) fields[7] else "" // 备注
                        val source = if (fields.size > 8) fields[8] else "手动" // 来源

                        val amount = amountStr.toDoubleOrNull() ?: return@forEach
                        val typeEn = if (type == "收入") "income" else "expense"

                        // 解析时间
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
                                cal.timeInMillis
                            } else {
                                System.currentTimeMillis()
                            }
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                        // 匹配分类
                        val categoryId = categoryMap[categoryName]?.id

                        val transaction = Transaction(
                            amount = amount,
                            type = typeEn,
                            categoryId = categoryId,
                            merchant = merchant.ifBlank { null },
                            paymentMethod = method.ifBlank { null },
                            note = note.ifBlank { null },
                            source = if (source == "自动") "auto" else "manual",
                            notificationKey = null,
                            transactionTime = dateTime
                        )

                        db.transactionDao().insert(transaction)
                        count++
                    } catch (e: Exception) {
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

    /**
     * 解析 CSV 行，处理引号内的逗号
     */
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
