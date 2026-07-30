package com.smartledger.service

import android.util.Log
import com.smartledger.data.db.dao.TransactionDao
import com.smartledger.data.db.entity.Transaction

/**
 * 通知+短信统一去重
 * 按「金额（分）+ type + 时间窗」查近 60 秒是否已有流水
 * 商户互相包含时窗口扩大到 120 秒
 */
object DedupHelper {

    private const val TAG = "DedupHelper"
    private const val BASE_WINDOW_MS = 60_000L    // 60秒基础窗口
    private const val MERCHANT_WINDOW_MS = 120_000L // 120秒商户匹配窗口

    /**
     * 检查是否重复交易
     * @return 已有的重复交易（若有），null 表示不重复
     */
    suspend fun findDuplicate(
        dao: TransactionDao,
        amountCents: Long,
        type: String,
        merchant: String?,
        now: Long = System.currentTimeMillis()
    ): Transaction? {
        val recentList = dao.getByTimeRangeOnce(now - MERCHANT_WINDOW_MS, now)
        return recentList.find { existing ->
            val existingCents = (existing.amount * 100).toLong()
            if (existingCents != amountCents || existing.type != type) return@find false

            val timeDiff = now - existing.transactionTime
            val sameMerchant = merchantMatch(merchant, existing.merchant)
            val window = if (sameMerchant) MERCHANT_WINDOW_MS else BASE_WINDOW_MS
            timeDiff < window
        }
    }

    /**
     * 商户名互相包含即视为相同
     */
    fun merchantMatch(new: String?, old: String?): Boolean {
        if (new.isNullOrBlank() || old.isNullOrBlank()) return false
        return new.contains(old) || old.contains(new)
    }

    /**
     * 去重命中时：保留更具体 paymentMethod，更新更长 merchant
     */
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
            dao.update(existing.copy(
                paymentMethod = if (shouldUpdateMethod) newMethod else existing.paymentMethod,
                merchant = if (shouldUpdateMerchant) newMerchant else existing.merchant
            ))
            Log.d(TAG, "Updated duplicate: method=${if (shouldUpdateMethod) newMethod else existing.paymentMethod}, merchant=${if (shouldUpdateMerchant) newMerchant else existing.merchant}")
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
