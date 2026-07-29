package com.smartledger

import android.app.Application
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.repository.BudgetRepository
import com.smartledger.data.repository.CategoryRepository
import com.smartledger.data.repository.TransactionRepository

class SmartLedgerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }
    val categoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val budgetRepository by lazy { BudgetRepository(database.budgetDao()) }
}
