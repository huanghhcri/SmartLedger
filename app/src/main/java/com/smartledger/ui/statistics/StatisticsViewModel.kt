package com.smartledger.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.dao.CategoryTotal
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val transactionRepo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    // 当前选择的周期
    private val _selectedPeriod = MutableStateFlow("month")
    val selectedPeriod: Flow<String> = _selectedPeriod

    // 根据周期计算时间范围
    private val _timeRange = MutableStateFlow(getTimeRange("month"))

    // 响应式数据 - 当周期变化时自动更新
    val expenseByCategory: Flow<List<CategoryTotal>> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getExpenseGroupByCategory(start, end)
    }

    val transactions: Flow<List<Transaction>> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getByTimeRange(start, end)
    }

    val periodExpense: Flow<Double> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getExpenseSum(start, end)
    }

    val periodIncome: Flow<Double> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getIncomeSum(start, end)
    }

    val categories: Flow<List<Category>> = categoryRepo.getAll()

    fun setPeriod(period: String) {
        _selectedPeriod.value = period
        _timeRange.value = getTimeRange(period)
    }

    private fun getTimeRange(period: String): Pair<Long, Long> {
        return when (period) {
            "day" -> Pair(DateUtil.getTodayStartTime(), DateUtil.getTodayEndTime())
            "week" -> Pair(DateUtil.getWeekStartTime(), DateUtil.getWeekEndTime())
            "year" -> Pair(DateUtil.getYearStartTime(), DateUtil.getYearEndTime())
            else -> Pair(
                DateUtil.getMonthStartTime(DateUtil.getCurrentYearMonth()),
                DateUtil.getMonthEndTime(DateUtil.getCurrentYearMonth())
            )
        }
    }
}
