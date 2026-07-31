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
        // 进程冷启动后 binder 状态未知，先标未连接；
        // PaymentNotificationListener.onListenerConnected 成功后再标为已连接
        ListenerStatus.setConnected(this, false)
        ListenerStatus.checkAppUpdated(this)
        SmartCategorizer.init(this)
        NotificationStyle.ensureChannels(this)
        ListenerRebindScheduler.schedule(this)
        // 设置仍勾选时主动静默重绑（更新撤销权限时无效，日常杀后台有效）
        ListenerStatus.requestRebind(this, force = true)
    }
}
