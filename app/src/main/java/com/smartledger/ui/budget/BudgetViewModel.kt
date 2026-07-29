package com.smartledger.ui.budget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.dao.CategoryTotal
import com.smartledger.data.db.entity.Budget
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.CategoryBudget
import com.smartledger.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val budgetRepo = (application as SmartLedgerApp).budgetRepository
    private val transactionRepo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    private val currentYearMonth = DateUtil.getCurrentYearMonth()
    private val monthStart = DateUtil.getMonthStartTime(currentYearMonth)
    private val monthEnd = DateUtil.getMonthEndTime(currentYearMonth)

    private val _budget = MutableStateFlow<Budget?>(null)
    val budget: StateFlow<Budget?> = _budget

    val monthExpense: Flow<Double> = transactionRepo.getExpenseSum(monthStart, monthEnd)
    val monthIncome: Flow<Double> = transactionRepo.getIncomeSum(monthStart, monthEnd)

    val expenseByCategory: Flow<List<CategoryTotal>> =
        transactionRepo.getExpenseGroupByCategory(monthStart, monthEnd)

    val categories: Flow<List<Category>> = categoryRepo.getAll()

    init {
        loadBudget()
    }

    private fun loadBudget() {
        viewModelScope.launch {
            _budget.value = budgetRepo.getBudgetByMonth(currentYearMonth)
        }
    }

    fun saveBudget(totalIncomeTarget: Double?, totalExpenseLimit: Double?) {
        viewModelScope.launch {
            val existing = budgetRepo.getBudgetByMonth(currentYearMonth)
            if (existing != null) {
                budgetRepo.updateBudget(
                    existing.copy(
                        totalIncomeTarget = totalIncomeTarget,
                        totalExpenseLimit = totalExpenseLimit
                    )
                )
            } else {
                budgetRepo.insertBudget(
                    Budget(
                        yearMonth = currentYearMonth,
                        totalIncomeTarget = totalIncomeTarget,
                        totalExpenseLimit = totalExpenseLimit
                    )
                )
            }
            loadBudget()
        }
    }
}
