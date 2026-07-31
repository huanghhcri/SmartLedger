package com.smartledger.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.service.SmartCategorizer
import com.smartledger.util.DateUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    /** 跨日打开仍刷新「今日/本月」区间 */
    private val dateTick = MutableStateFlow(System.currentTimeMillis())

    val todayExpense: Flow<Double> = dateTick.flatMapLatest {
        repo.getExpenseSum(DateUtil.getTodayStartTime(), DateUtil.getTodayEndTime())
    }
    val monthExpense: Flow<Double> = dateTick.flatMapLatest {
        val ym = DateUtil.getCurrentYearMonth()
        repo.getExpenseSum(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
    }
    val monthIncome: Flow<Double> = dateTick.flatMapLatest {
        val ym = DateUtil.getCurrentYearMonth()
        repo.getIncomeSum(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
    }

    val recentTransactions: Flow<List<Transaction>> = dateTick.flatMapLatest {
        val ym = DateUtil.getCurrentYearMonth()
        repo.getByTimeRange(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
    }

    val categories: Flow<List<Category>> = categoryRepo.getAll()

    fun refreshDateRange() {
        dateTick.value = System.currentTimeMillis()
    }

    fun getCategoryName(categoryId: Long?, categories: List<Category>): String {
        return categories.find { it.id == categoryId }?.name ?: "其他"
    }

    fun getCategoryColor(categoryId: Long?, categories: List<Category>): Long? {
        return categories.find { it.id == categoryId }?.color
    }

    fun updateTransactionCategory(transaction: Transaction, categoryId: Long?) {
        viewModelScope.launch {
            repo.update(transaction.copy(categoryId = categoryId))
            if (categoryId != null && !transaction.merchant.isNullOrBlank()) {
                SmartCategorizer.saveMerchantCategory(
                    getApplication(),
                    transaction.merchant!!,
                    categoryId
                )
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repo.update(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repo.delete(transaction)
        }
    }
}
