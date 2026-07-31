package com.smartledger.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * 通知监听连接状态：区分「设置里已勾选」与「binder 是否真正连着」。
 *
 * 说明（Android 系统限制）：
 * - 安装 / 覆盖更新后，系统会撤销通知使用权，应用无法静默恢复，必须用户重新打开一次。
 * - 进程被杀、binder 断开但设置仍勾选时，可用 requestRebind 自动恢复，无需用户操作。
 */
object ListenerStatus {

    private const val TAG = "ListenerStatus"
    private const val PREFS = "smart_ledger"
    private const val KEY_CONNECTED = "nls_connected"
    private const val KEY_LAST_VERSION = "last_version_code"
    private const val KEY_SHOW_AFTER_UPDATE = "show_nls_after_update"
    private const val KEY_EVER_ENABLED = "nls_ever_enabled"
    /** 后台巡检发现失效后，等用户打开 App 再弹应用内提示 */
    private const val KEY_PENDING_IN_APP_PROMPT = "nls_pending_in_app_prompt"

    /** 设置里已关闭 */
    const val PROMPT_DISABLED = "disabled"
    /** 设置仍开着但连接断开 */
    const val PROMPT_RECONNECT = "reconnect"

    fun isEnabledInSettings(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (flat.isNullOrEmpty()) return false
        return flat.contains(context.packageName) &&
            flat.contains("PaymentNotificationListener")
    }

    fun setConnected(context: Context, connected: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(KEY_CONNECTED, connected)
        if (connected) {
            editor.putBoolean(KEY_EVER_ENABLED, true)
        }
        editor.apply()
        Log.d(TAG, "connected=$connected")
    }

    fun markEverEnabled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EVER_ENABLED, true)
            .apply()
    }

    /**
     * 是否应对「权限失效」做提醒：曾开启过、或更新后待重开、或设置里仍勾着。
     * 首次安装尚未引导完成的用户不打扰。
     */
    fun shouldMonitorPermission(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_launch", true)) return false
        if (prefs.getBoolean(KEY_EVER_ENABLED, false)) return true
        if (prefs.getBoolean(KEY_SHOW_AFTER_UPDATE, false)) return true
        return isEnabledInSettings(context)
    }

    fun isConnected(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONNECTED, false)
    }

    /**
     * @param force true 时即使本地标记已连接也 requestRebind（定时巡检用）
     * @return 是否已发起 rebind
     */
    fun requestRebind(context: Context, force: Boolean = false): Boolean {
        if (!isEnabledInSettings(context)) {
            setConnected(context, false)
            return false
        }
        if (!force && isConnected(context)) return false
        return try {
            val cn = ComponentName(context, PaymentNotificationListener::class.java)
            NotificationListenerService.requestRebind(cn)
            Log.d(TAG, "requestRebind issued force=$force")
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind failed", e)
            false
        }
    }

    fun requestRebindIfNeeded(context: Context): Boolean = requestRebind(context, force = false)

    /**
     * 检测版本升级：更新后系统会撤销通知使用权，打标供下次打开 App 时提示。
     * @return true 表示刚发生升级
     */
    fun checkAppUpdated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = currentVersionCode(context)
        val last = prefs.getLong(KEY_LAST_VERSION, -1L)
        if (last < 0) {
            // 首次记录，不弹「更新」提示
            prefs.edit().putLong(KEY_LAST_VERSION, current).apply()
            return false
        }
        if (current != last) {
            prefs.edit()
                .putLong(KEY_LAST_VERSION, current)
                .putBoolean(KEY_SHOW_AFTER_UPDATE, true)
                .apply()
            setConnected(context, false)
            Log.d(TAG, "App updated $last → $current, mark NLS re-grant needed")
            return true
        }
        return false
    }

    fun markNeedsRegrantAfterUpdate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_AFTER_UPDATE, true)
            .apply()
        setConnected(context, false)
    }

    /** 是否应展示「更新后请重新开启通知使用权」 */
    fun shouldPromptAfterUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SHOW_AFTER_UPDATE, false)) return false
        // 用户已重新开启则清掉标记
        if (isEnabledInSettings(context)) {
            clearAfterUpdatePrompt(context)
            return false
        }
        return true
    }

    fun clearAfterUpdatePrompt(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_AFTER_UPDATE, false)
            .apply()
    }

    /**
     * 后台巡检：能恢复则静默重连；失效只打标，等用户打开 App 再弹窗（不发系统通知）。
     * @return true 表示当前处于失效状态
     */
    fun checkAndRecoverIfNeeded(context: Context): Boolean {
        if (isEnabledInSettings(context)) {
            markEverEnabled(context)
            if (!isConnected(context)) {
                requestRebind(context, force = true)
                try {
                    Thread.sleep(3000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            if (isConnected(context)) {
                clearPendingInAppPrompt(context)
                return false
            }
            markPendingInAppPrompt(context, PROMPT_RECONNECT)
            Log.w(TAG, "NLS enabled but disconnected, mark in-app prompt")
            return true
        }

        if (!shouldMonitorPermission(context)) {
            Log.d(TAG, "NLS off, skip mark (not monitoring yet)")
            return true
        }
        markPendingInAppPrompt(context, PROMPT_DISABLED)
        Log.w(TAG, "NLS disabled, mark in-app prompt")
        return true
    }

    fun markPendingInAppPrompt(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_IN_APP_PROMPT, reason)
            .apply()
    }

    fun clearPendingInAppPrompt(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_IN_APP_PROMPT)
            .apply()
    }

    /** 取出并清除「打开 App 后再提示」标记；无则返回 null */
    fun consumePendingInAppPrompt(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reason = prefs.getString(KEY_PENDING_IN_APP_PROMPT, null) ?: return null
        prefs.edit().remove(KEY_PENDING_IN_APP_PROMPT).apply()
        return reason
    }

    private fun currentVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
    }
}
