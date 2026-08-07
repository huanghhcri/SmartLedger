package com.smartledger.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 通知监听连接状态：区分「设置里已勾选」与「binder 是否真正连着」。
 *
 * 说明（Android 系统限制）：
 * - 安装 / 覆盖更新后，系统会撤销通知使用权，应用无法静默恢复，必须用户重新打开一次。
 * - 进程被杀、binder 断开但设置仍勾选时，可用 requestRebind 自动恢复，无需用户操作。
 * - 禁止频繁 disable/enable 监听组件，部分机型会导致永久断连。
 */
object ListenerStatus {

    private const val TAG = "ListenerStatus"
    private const val PREFS = "smart_ledger"
    private const val KEY_CONNECTED = "nls_connected"
    private const val KEY_LAST_VERSION = "last_version_code"
    private const val KEY_SHOW_AFTER_UPDATE = "show_nls_after_update"
    private const val KEY_EVER_ENABLED = "nls_ever_enabled"
    private const val KEY_PENDING_IN_APP_PROMPT = "nls_pending_in_app_prompt"
    private const val KEY_LAST_FORCE_RECONNECT = "nls_last_force_reconnect"

    /** 设置里已关闭 */
    const val PROMPT_DISABLED = "disabled"
    /** 设置仍开着但连接断开 */
    const val PROMPT_RECONNECT = "reconnect"

    /** 进程内实时状态，避免仅依赖 SharedPreferences 误判 */
    private val binderConnected = AtomicBoolean(false)
    private val lastForceAt = AtomicLong(0L)

    fun isEnabledInSettings(context: Context): Boolean {
        // API 27+：官方接口更可靠
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val nm = context.getSystemService(NotificationManager::class.java)
                val cn = ComponentName(context, PaymentNotificationListener::class.java)
                if (nm != null && nm.isNotificationListenerAccessGranted(cn)) {
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "isNotificationListenerAccessGranted failed", e)
            }
        }
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (flat.isNullOrEmpty()) return false
        val pkg = context.packageName
        return flat.contains(pkg) && (
            flat.contains("PaymentNotificationListener") ||
                flat.contains("$pkg/.service.PaymentNotificationListener") ||
                flat.contains("$pkg/com.smartledger.service.PaymentNotificationListener")
            )
    }

    fun setConnected(context: Context, connected: Boolean) {
        binderConnected.set(connected)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(KEY_CONNECTED, connected)
        if (connected) {
            editor.putBoolean(KEY_EVER_ENABLED, true)
        }
        editor.apply()
        Log.d(TAG, "connected=$connected")
        try {
            KeepAliveService.refreshNotification(context)
        } catch (_: Exception) {
        }
    }

    /**
     * 收到任意支付相关通知即证明 binder 已通，用于自愈「假断开」文案。
     */
    fun markAliveFromNotification(context: Context) {
        if (!binderConnected.get()) {
            Log.d(TAG, "heal: notification received → mark connected")
        }
        setConnected(context, true)
        clearPendingInAppPrompt(context)
    }

    fun markEverEnabled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EVER_ENABLED, true)
            .apply()
    }

    fun shouldMonitorPermission(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_launch", true)) return false
        if (prefs.getBoolean(KEY_EVER_ENABLED, false)) return true
        if (prefs.getBoolean(KEY_SHOW_AFTER_UPDATE, false)) return true
        return isEnabledInSettings(context)
    }

    /** 本进程内 binder 是否确认已连接（最可靠） */
    fun isBinderConnected(): Boolean = binderConnected.get()

    /**
     * 是否视为「已连接」。
     * 优先看本进程 binder；SharedPreferences 仅作辅助，避免冷启动误判。
     *
     * 注意：进程被杀后 prefs 可能仍为 true，但 binder 已断 ——
     * 此时不能仅凭 prefs 显示「正在自动记录」，否则会出现假运行。
     */
    fun isConnected(context: Context): Boolean {
        if (binderConnected.get()) return true
        // 冷启动后 binder 尚未回调时：不信任旧 prefs，交给 KeepAlive 主动 rebind
        return false
    }

    /** 曾成功连接过（prefs），用于判断是否值得继续自动重连 */
    fun wasConnectedBefore(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONNECTED, false)
    }

    /**
     * 保活通知用的展示态（比单纯 isConnected 更准确）。
     */
    enum class DisplayState {
        /** 设置未开 */
        NEED_PERMISSION,
        /** 已连接 */
        CONNECTED,
        /** 设置已开，正在重连 */
        RECOVERING
    }

    fun displayState(context: Context): DisplayState {
        if (!isEnabledInSettings(context)) return DisplayState.NEED_PERMISSION
        // 仅 binder 确认后才显示「正在自动记录」，避免假阳性
        if (binderConnected.get()) return DisplayState.CONNECTED
        return DisplayState.RECOVERING
    }

    fun requestRebind(context: Context, force: Boolean = false): Boolean {
        if (!isEnabledInSettings(context)) {
            setConnected(context, false)
            return false
        }
        if (!force && isConnected(context)) return false
        return try {
            val cn = ComponentName(context, PaymentNotificationListener::class.java)
            // 确保组件处于启用（曾被误 disable 时恢复）
            ensureComponentEnabled(context, cn)
            NotificationListenerService.requestRebind(cn)
            Log.d(TAG, "requestRebind issued force=$force")
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind failed", e)
            false
        }
    }

    fun requestRebindIfNeeded(context: Context): Boolean = requestRebind(context, force = false)

    private fun ensureComponentEnabled(context: Context, cn: ComponentName) {
        try {
            val pm = context.packageManager
            val state = pm.getComponentEnabledSetting(cn)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            ) {
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.w(TAG, "NLS component was disabled, re-enabled")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ensureComponentEnabled failed", e)
        }
    }

    /**
     * 强恢复：仅作最后手段，且全局限流（默认 30 分钟最多一次）。
     * 频繁 toggle 组件会在部分机型上导致监听永久失效。
     */
    fun forceReconnect(context: Context, bypassCooldown: Boolean = false): Boolean {
        if (!isEnabledInSettings(context)) {
            setConnected(context, false)
            return false
        }
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = maxOf(lastForceAt.get(), prefs.getLong(KEY_LAST_FORCE_RECONNECT, 0L))
        if (!bypassCooldown && now - last < 30 * 60 * 1000L) {
            Log.d(TAG, "forceReconnect skipped (cooldown)")
            return false
        }
        return try {
            val pm = context.packageManager
            val cn = ComponentName(context, PaymentNotificationListener::class.java)
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            // 稍等再启用，给系统消化 disable
            Thread.sleep(400)
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Thread.sleep(200)
            NotificationListenerService.requestRebind(cn)
            lastForceAt.set(now)
            prefs.edit().putLong(KEY_LAST_FORCE_RECONNECT, now).apply()
            Log.d(TAG, "forceReconnect: component toggled + requestRebind")
            true
        } catch (e: Exception) {
            Log.e(TAG, "forceReconnect failed", e)
            false
        }
    }

    fun checkAppUpdated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = currentVersionCode(context)
        val last = prefs.getLong(KEY_LAST_VERSION, -1L)
        if (last < 0) {
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

    fun shouldPromptAfterUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SHOW_AFTER_UPDATE, false)) return false
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

    fun checkAndRecoverIfNeeded(context: Context): Boolean {
        if (isEnabledInSettings(context)) {
            markEverEnabled(context)
            if (!isConnected(context)) {
                requestRebind(context, force = true)
                try {
                    Thread.sleep(2500)
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

    fun consumePendingInAppPrompt(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reason = prefs.getString(KEY_PENDING_IN_APP_PROMPT, null) ?: return null
        prefs.edit().remove(KEY_PENDING_IN_APP_PROMPT).apply()
        return reason
    }

    private fun currentVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
