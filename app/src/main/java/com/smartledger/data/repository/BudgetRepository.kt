package com.smartledger.data.repository

import com.smartledger.data.db.dao.BudgetDao
import com.smartledger.data.db.entity.Budget
import com.smartledger.data.db.entity.CategoryBudget
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val dao: BudgetDao) {

    suspend fun insertBudget(budget: Budget): Long = dao.insertBudget(budget)

    suspend fun updateBudget(budget: Budget) = dao.updateBudget(budget)

    suspend fun getBudgetByMonth(yearMonth: String): Budget? = dao.getBudgetByMonth(yearMonth)

    fun observeBudgetByMonth(yearMonth: String): Flow<Budget?> = dao.observeBudgetByMonth(yearMonth)

    suspend fun insertCategoryBudget(categoryBudget: CategoryBudget): Long =
        dao.insertCategoryBudget(categoryBudget)

    suspend fun deleteCategoryBudget(categoryBudget: CategoryBudget) =
        dao.deleteCategoryBudget(categoryBudget)

    fun getCategoryBudgets(budgetId: Long): Flow<List<CategoryBudget>> =
        dao.getCategoryBudgets(budgetId)

    suspend fun getCategoryBudgetsList(budgetId: Long): List<CategoryBudget> =
        dao.getCategoryBudgetsList(budgetId)
}
