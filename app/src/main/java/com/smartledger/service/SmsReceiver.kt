package com.smartledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Transaction
import com.smartledger.util.CurrencyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 短信兜底通道
 * 监听银行短信，当通知监听服务漏掉时作为补充
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    private val bankIdentifiers = mapOf(
        "工商银行" to "工商银行",
        "工行" to "工商银行",
        "建设银行" to "建设银行",
        "建行" to "建设银行",
        "中国银行" to "中国银行",
        "中行" to "中国银行",
        "农业银行" to "农业银行",
        "农行" to "农业银行",
        "招商银行" to "招商银行",
        "招行" to "招商银行",
        "邮政" to "邮政储蓄",
        "邮储" to "邮政储蓄",
        "浦发" to "浦发银行",
        "民生" to "民生银行",
        "光大" to "光大银行",
        "兴业" to "兴业银行",
        "平安" to "平安银行",
        "中信" to "中信银行",
        "交通银行" to "交通银行",
        "交行" to "交通银行"
    )

    private val expenseKeywords = listOf(
        "扣款", "支出", "消费", "付款", "转出", "扣费", "交易支出"
    )

    private val incomeKeywords = listOf(
        "到账", "收入", "转入", "存入", "退款", "收款"
    )

    private val amountPatterns = listOf(
        Regex("金额([\\d.]+)"),
        Regex("人民币([\\d.]+)元"),
        Regex("([\\d.]+)元"),
        Regex("[￥¥]([\\d.]+)")
    )

    private val merchantPatterns = listOf(
        Regex("账号(.+?)扣款"),
        Regex("账号(.+?)支出"),
        Regex("在(.+?)消费"),
        Regex("商户[：:]\\s*(.+?)(?:\\s|$)")
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (sms in messages) {
                    val sender = sms.displayOriginatingAddress ?: ""
                    val body = sms.messageBody ?: ""
                    Log.d(TAG, "SMS from $sender: $body")
                    if (!isBankSms(body)) continue
                    processBankSms(appContext, body)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS handle failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processBankSms(context: Context, body: String) {
        Log.d(TAG, "Bank SMS detected: $body")

        val isExpense = expenseKeywords.any { body.contains(it) }
        val isIncome = incomeKeywords.any { body.contains(it) }
        if (!isExpense && !isIncome) return

        var amount: Double? = null
        for (pattern in amountPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                amount = match.groupValues.lastOrNull { it.matches(Regex("[\\d.]+")) }?.toDoubleOrNull()
                if (amount != null && amount > 0) break
            }
        }
        if (amount == null || amount <= 0) return

        val bankName = identifyBank(body)

        var merchant: String? = null
        for (pattern in merchantPatterns) {
            val match = pattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                merchant = match.groupValues[1].trim()
                if (merchant.isNotBlank()) break
            }
        }

        // 与 NotificationParser 一致：退款/到账优先收入
        val type = when {
            isIncome && !isExpense -> "income"
            isExpense && !isIncome -> "expense"
            body.contains("退款") || body.contains("退回") || body.contains("到账") -> "income"
            else -> "expense"
        }

        Log.d(TAG, "SMS parsed: amount=$amount, bank=$bankName, merchant=$merchant, type=$type")

        try {
            val db = AppDatabase.getInstance(context)
            val amountCents = CurrencyUtil.toCents(amount)
            val now = System.currentTimeMillis()
            val duplicate = DedupHelper.findDuplicate(
                db.transactionDao(),
                amountCents,
                type,
                merchant,
                bankName,
                now
            )
            if (duplicate != null) {
                Log.d(TAG, "SMS duplicate detected, skipping")
                DedupHelper.mergeIfDuplicate(db.transactionDao(), duplicate, bankName, merchant)
                return
            }

            val transaction = Transaction(
                amount = amount,
                type = type,
                categoryId = null,
                merchant = merchant,
                paymentMethod = bankName,
                note = null,
                source = "sms",
                notificationKey = null,
                transactionTime = now
            )
            val id = db.transactionDao().insert(transaction)
            Log.d(TAG, "SMS transaction saved: id=$id")

            try {
                val categories = db.categoryDao().getAllOnce()
                val categoryId = SmartCategorizer.categorize(
                    merchant = merchant,
                    paymentMethod = bankName,
                    note = null,
                    categories = categories,
                    type = type
                )
                if (categoryId != null) {
                    val saved = db.transactionDao().getById(id)
                    if (saved != null) {
                        db.transactionDao().update(saved.copy(categoryId = categoryId))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS categorize failed", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save SMS transaction", e)
        }
    }

    private fun isBankSms(body: String): Boolean {
        if (body.contains("银行") || body.contains("工商") || body.contains("建设") ||
            body.contains("农业") || body.contains("招商") || body.contains("邮政") ||
            body.contains("浦发") || body.contains("民生") || body.contains("光大") ||
            body.contains("兴业") || body.contains("平安") || body.contains("中信") ||
            body.contains("交通")
        ) {
            return body.contains("元") || body.contains("￥") || body.contains("¥") || body.contains("金额")
        }
        return false
    }

    private fun identifyBank(body: String): String {
        for ((keyword, bankName) in bankIdentifiers) {
            if (body.contains(keyword)) return bankName
        }
        return "银行卡"
    }
}
