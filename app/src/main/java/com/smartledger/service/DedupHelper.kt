package com.smartledger.service

import android.util.Log
import com.smartledger.data.db.dao.TransactionDao
import com.smartledger.data.db.entity.Transaction
import com.smartledger.util.CurrencyUtil

/**
 * 通知+短信统一去重
 * - 跨渠道（微信+银行）同金额：60s 内合并
 * - 同渠道且双方无商户：仅 10s，避免连续两笔同额消费被吞
 * - 商户互相包含：窗口扩到 120s
 */
object DedupHelper {

    private const val TAG = "DedupHelper"
    private const val BASE_WINDOW_MS = 60_000L
    private const val MERCHANT_WINDOW_MS = 120_000L
    private const val SAME_CHANNEL_NO_MERCHANT_MS = 10_000L

    /**
     * @return 已有的重复交易（若有），null 表示不重复
     */
    suspend fun findDuplicate(
        dao: TransactionDao,
        amountCents: Long,
        type: String,
        merchant: String?,
        paymentMethod: String? = null,
        now: Long = System.currentTimeMillis()
    ): Transaction? {
        val recentList = dao.getByTimeRangeOnce(now - MERCHANT_WINDOW_MS, now)
        return recentList.find { existing ->
            val existingCents = CurrencyUtil.toCents(existing.amount)
            if (existingCents != amountCents || existing.type != type) return@find false

            val timeDiff = now - existing.transactionTime
            if (timeDiff < 0) return@find false

            val sameMerchant = merchantMatch(merchant, existing.merchant)
            val sameChannel = !paymentMethod.isNullOrBlank() &&
                paymentMethod == existing.paymentMethod
            val bothNoMerchant = merchant.isNullOrBlank() && existing.merchant.isNullOrBlank()

            val window = when {
                sameMerchant -> MERCHANT_WINDOW_MS
                // 同渠道、无商户：短窗，防止 30s 内两笔 ¥15 被误并
                sameChannel && bothNoMerchant -> SAME_CHANNEL_NO_MERCHANT_MS
                else -> BASE_WINDOW_MS
            }
            timeDiff < window
        }
    }

    fun merchantMatch(new: String?, old: String?): Boolean {
        if (new.isNullOrBlank() || old.isNullOrBlank()) return false
        return new.contains(old) || old.contains(new)
    }

    suspend fun mergeIfDuplicate(
        dao: TransactionDao,
        existing: Transaction,
        newMethod: String,
        newMerchant: String?
    ) {
        val shouldUpdateMethod = isMoreSpecificMethod(newMethod, existing.paymentMethod ?: "")
        val shouldUpdateMerchant = newMerchant != null &&
            newMerchant.length > (existing.merchant?.length ?: 0) &&
            !newMerchant.contains(existing.merchant ?: "§§§")

        if (shouldUpdateMethod || shouldUpdateMerchant) {
            dao.update(
                existing.copy(
                    paymentMethod = if (shouldUpdateMethod) newMethod else existing.paymentMethod,
                    merchant = if (shouldUpdateMerchant) newMerchant else existing.merchant
                )
            )
            Log.d(
                TAG,
                "Updated duplicate: method=${if (shouldUpdateMethod) newMethod else existing.paymentMethod}, " +
                    "merchant=${if (shouldUpdateMerchant) newMerchant else existing.merchant}"
            )
        }
    }

    private fun isMoreSpecificMethod(new: String, existing: String): Boolean {
        val priority = mapOf(
            "工商银行" to 10, "邮政储蓄" to 10, "建设银行" to 10,
            "中国银行" to 10, "农业银行" to 10, "招商银行" to 10,
            "平安银行" to 10, "浦发银行" to 10, "民生银行" to 10,
            "光大银行" to 10, "兴业银行" to 10, "交通银行" to 10,
            "银行卡" to 5, "支付宝" to 3, "微信" to 3, "云闪付" to 3, "抖音" to 2
        )
        return (priority[new] ?: 0) > (priority[existing] ?: 0)
    }
}
