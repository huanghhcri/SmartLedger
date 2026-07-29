package com.smartledger.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    private val todayStart = DateUtil.getTodayStartTime()
    private val todayEnd = DateUtil.getTodayEndTime()
    private val monthStart = DateUtil.getMonthStartTime(DateUtil.getCurrentYearMonth())
    private val monthEnd = DateUtil.getMonthEndTime(DateUtil.getCurrentYearMonth())

    val todayExpense: Flow<Double> = repo.getExpenseSum(todayStart, todayEnd)
    val monthExpense: Flow<Double> = repo.getExpenseSum(monthStart, monthEnd)
    val monthIncome: Flow<Double> = repo.getIncomeSum(monthStart, monthEnd)

    val recentTransactions: Flow<List<Transaction>> = repo.getByTimeRange(monthStart, monthEnd)

    val categories: Flow<List<Category>> = categoryRepo.getAll()

    fun getCategoryName(categoryId: Long?, categories: List<Category>): String {
        return categories.find { it.id == categoryId }?.name ?: "其他"
    }

    fun getCategoryColor(categoryId: Long?, categories: List<Category>): Long? {
        return categories.find { it.id == categoryId }?.color
    }

    fun updateTransactionCategory(transaction: Transaction, categoryId: Long?) {
        viewModelScope.launch {
            repo.update(transaction.copy(categoryId = categoryId))
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repo.delete(transaction)
        }
    }
}
