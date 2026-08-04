package com.smartledger

import android.app.Application
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.repository.BudgetRepository
import com.smartledger.data.repository.CategoryRepository
import com.smartledger.data.repository.TransactionRepository
import com.smartledger.service.ListenerStatus
import com.smartledger.service.SmartCategorizer
import com.smartledger.util.ListenerRebindScheduler
import com.smartledger.util.NotificationStyle

class SmartLedgerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }
    val categoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val budgetRepository by lazy { BudgetRepository(database.budgetDao()) }

    override fun onCreate() {
        super.onCreate()
        // 主题与主界面一致，供确认弹窗等独立 Activity 使用
        val prefs = getSharedPreferences("smart_ledger", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", "SYSTEM")
        com.smartledger.ui.theme.ThemeManager.init(
            try {
                com.smartledger.ui.theme.ThemeMode.valueOf(savedTheme ?: "SYSTEM")
            } catch (_: Exception) {
                com.smartledger.ui.theme.ThemeMode.SYSTEM
            }
        )
        // 勿在冷启动时强行标「未连接」：若系统已连上但未再回调 onListenerConnected，
        // 会一直显示「监听已断开」。以 onListenerConnected / 收到通知为准更新状态。
        ListenerStatus.checkAppUpdated(this)
        SmartCategorizer.init(this)
        NotificationStyle.ensureChannels(this)
        ListenerRebindScheduler.schedule(this)
        if (ListenerStatus.isEnabledInSettings(this)) {
            com.smartledger.service.KeepAliveService.start(this)
            ListenerStatus.requestRebind(this, force = true)
        }
    }
}
