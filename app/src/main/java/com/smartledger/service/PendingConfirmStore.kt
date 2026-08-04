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
        val rawSnippet: String?
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
        rawSnippet: String?
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
            rawSnippet = rawSnippet
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
}
