package com.smartledger.ui.home

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS = "smart_ledger"
        private const val KEY_INITIAL_BALANCE = "initial_balance"
    }

    private val repo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository
    private val prefs =
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 跨日打开仍刷新「今日」区间 */
    private val dateTick = MutableStateFlow(System.currentTimeMillis())

    /** 当前查看的月份 yyyy-MM，可切换历史 */
    private val _selectedYearMonth = MutableStateFlow(DateUtil.getCurrentYearMonth())
    val selectedYearMonth: StateFlow<String> = _selectedYearMonth.asStateFlow()

    /** 期初余额：用户设定的起始资产，总余额 = 期初 + 全部收入 - 全部支出 */
    private val _initialBalance = MutableStateFlow(loadInitialBalance())
    val initialBalance: StateFlow<Double> = _initialBalance.asStateFlow()

    val totalIncome: Flow<Double> = repo.getTotalIncomeSum()
    val totalExpense: Flow<Double> = repo.getTotalExpenseSum()

    /** 当前总余额（存了多少钱） */
    val totalBalance: StateFlow<Double> = combine(
        _initialBalance,
        totalIncome,
        totalExpense
    ) { initial, income, expense ->
        initial + income - expense
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _initialBalance.value)

    val todayExpense: Flow<Double> = dateTick.flatMapLatest {
        repo.getExpenseSum(DateUtil.getTodayStartTime(), DateUtil.getTodayEndTime())
    }

    val monthExpense: Flow<Double> = combine(dateTick, _selectedYearMonth) { _, ym -> ym }
        .flatMapLatest { ym ->
            repo.getExpenseSum(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
        }

    val monthIncome: Flow<Double> = combine(dateTick, _selectedYearMonth) { _, ym -> ym }
        .flatMapLatest { ym ->
            repo.getIncomeSum(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
        }

    val recentTransactions: Flow<List<Transaction>> =
        combine(dateTick, _selectedYearMonth) { _, ym -> ym }
            .flatMapLatest { ym ->
                repo.getByTimeRange(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
            }

    val categories: Flow<List<Category>> = categoryRepo.getAll()

    fun refreshDateRange() {
        dateTick.value = System.currentTimeMillis()
    }

    fun setInitialBalance(amount: Double) {
        val safe = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        prefs.edit().putString(KEY_INITIAL_BALANCE, safe.toString()).apply()
        _initialBalance.value = safe
    }

    private fun loadInitialBalance(): Double {
        return prefs.getString(KEY_INITIAL_BALANCE, null)?.toDoubleOrNull()
            ?: prefs.getFloat(KEY_INITIAL_BALANCE, 0f).toDouble()
    }

    fun previousMonth() {
        _selectedYearMonth.value = DateUtil.shiftYearMonth(_selectedYearMonth.value, -1)
    }

    fun nextMonth() {
        val next = DateUtil.shiftYearMonth(_selectedYearMonth.value, 1)
        // 不允许翻到未来月
        if (next <= DateUtil.getCurrentYearMonth()) {
            _selectedYearMonth.value = next
        }
    }

    fun goToCurrentMonth() {
        _selectedYearMonth.value = DateUtil.getCurrentYearMonth()
    }

    fun isCurrentMonth(): Boolean =
        _selectedYearMonth.value == DateUtil.getCurrentYearMonth()

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
