package com.smartledger.data.db.dao

import androidx.room.*
import com.smartledger.data.db.entity.Budget
import com.smartledger.data.db.entity.CategoryBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun getBudgetByMonth(yearMonth: String): Budget?

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth LIMIT 1")
    fun observeBudgetByMonth(yearMonth: String): Flow<Budget?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryBudget(categoryBudget: CategoryBudget): Long

    @Delete
    suspend fun deleteCategoryBudget(categoryBudget: CategoryBudget)

    @Query("SELECT * FROM category_budgets WHERE budgetId = :budgetId")
    fun getCategoryBudgets(budgetId: Long): Flow<List<CategoryBudget>>

    @Query("SELECT * FROM category_budgets WHERE budgetId = :budgetId")
    suspend fun getCategoryBudgetsList(budgetId: Long): List<CategoryBudget>
}
