package com.smartledger.service

import android.content.Context
import java.security.MessageDigest

/**
 * 记住已经处理过的支付通知。
 *
 * 用户不清除通知时，系统会在几小时后因为新通知、监听重连或分组刷新，
 * 把同一条旧通知再交给 [PaymentNotificationListener]。时间窗去重盖不住这个间隔，
 * 所以按「通知 key + 规范化正文」记指纹，进程重启后仍然有效。
 */
object ProcessedNoticeStore {

    private const val PREFS = "processed_notices"
    private const val KEY = "fps"
    private const val MAX = 400

    private val memory = LinkedHashSet<String>()
    private var loaded = false

    fun fingerprint(noticeKey: String, normalizedText: String): String {
        val raw = noticeKey + "\n" + normalizedText.trim()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    @Synchronized
    fun isProcessed(context: Context, fingerprint: String): Boolean {
        if (fingerprint.isBlank()) return false
        ensureLoaded(context)
        return fingerprint in memory
    }

    @Synchronized
    fun mark(context: Context, fingerprint: String) {
        if (fingerprint.isBlank()) return
        ensureLoaded(context)
        if (!memory.add(fingerprint)) return
        while (memory.size > MAX) {
            val oldest = memory.first()
            memory.remove(oldest)
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, memory.joinToString("\n"))
            .apply()
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        if (!saved.isNullOrBlank()) {
            saved.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { memory.add(it) }
        }
        loaded = true
    }
}
