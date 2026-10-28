package com.smartledger.service

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 待确认账单暂存（进程内）。确认前不写数据库，避免误记后还要删。
 */
object PendingConfirmStore {

    data class Pending(
        val id: Long,
        val amount: Double,
        val type: String,
        val merchant: String?,
        val paymentMethod: String,
        val notificationKey: String?,
        val transactionTime: Long,
        val reason: String?,
        val rawSnippet: String?,
        val channelHint: String? = null,
        val notifyOrigin: String? = null,
    )

    private val seq = AtomicLong(1)
    private val map = ConcurrentHashMap<Long, Pending>()

    fun put(
        amount: Double,
        type: String,
        merchant: String?,
        paymentMethod: String,
        notificationKey: String?,
        transactionTime: Long,
        reason: String?,
        rawSnippet: String?,
        channelHint: String? = null,
        notifyOrigin: String? = null,
    ): Long {
        val id = seq.getAndIncrement()
        map[id] = Pending(
            id = id,
            amount = amount,
            type = type,
            merchant = merchant,
            paymentMethod = paymentMethod,
            notificationKey = notificationKey,
            transactionTime = transactionTime,
            reason = reason,
            rawSnippet = rawSnippet,
            channelHint = channelHint,
            notifyOrigin = notifyOrigin,
        )
        // 防止堆积：最多保留 20 条
        if (map.size > 20) {
            map.keys.sorted().take(map.size - 20).forEach { map.remove(it) }
        }
        return id
    }

    fun get(id: Long): Pending? = map[id]

    fun remove(id: Long) {
        map.remove(id)
    }

    /** 同一笔付款的另一条通知还在等确认时，不要再弹一次。 */
    fun findSimilar(
        amountCents: Long,
        type: String,
        merchant: String?,
        paymentMethod: String?,
        transactionTime: Long,
        channelHint: String?,
    ): Pending? {
        val incoming = DedupHelper.PaymentSignature(
            amountCents, type, merchant, paymentMethod, transactionTime, channelHint
        )
        return map.values.firstOrNull { pending ->
            DedupHelper.paymentsAreDuplicate(
                DedupHelper.PaymentSignature(
                    amountCents = com.smartledger.util.CurrencyUtil.toCents(pending.amount),
                    type = pending.type,
                    merchant = pending.merchant,
                    paymentMethod = pending.paymentMethod,
                    transactionTime = pending.transactionTime,
                    channelHint = pending.channelHint,
                ),
                incoming
            )
        }
    }
}
