package com.smartledger.data.db.dao

import androidx.room.*
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY transactionTime DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE notificationKey = :key LIMIT 1")
    suspend fun getByNotificationKey(key: String): Transaction?

    @Query("SELECT * FROM transactions WHERE transactionTime BETWEEN :startTime AND :endTime ORDER BY transactionTime DESC")
    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transactionTime BETWEEN :startTime AND :endTime ORDER BY transactionTime DESC")
    suspend fun getByTimeRangeOnce(startTime: Long, endTime: Long): List<Transaction>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'expense' AND transactionTime BETWEEN :startTime AND :endTime")
    fun getExpenseSum(startTime: Long, endTime: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'income' AND transactionTime BETWEEN :startTime AND :endTime")
    fun getIncomeSum(startTime: Long, endTime: Long): Flow<Double>

    /** 全部支出合计（不限月份） */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'expense'")
    fun getTotalExpenseSum(): Flow<Double>

    /** 全部收入合计（不限月份） */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'income'")
    fun getTotalIncomeSum(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'expense' AND categoryId = :categoryId AND transactionTime BETWEEN :startTime AND :endTime")
    fun getExpenseByCategory(categoryId: Long, startTime: Long, endTime: Long): Flow<Double>

    @Query("SELECT categoryId, SUM(amount) as total FROM transactions WHERE type = 'expense' AND transactionTime BETWEEN :startTime AND :endTime GROUP BY categoryId ORDER BY total DESC")
    fun getExpenseGroupByCategory(startTime: Long, endTime: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM transactions WHERE merchant LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%' ORDER BY transactionTime DESC")
    fun search(keyword: String): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int

    @Query("SELECT MIN(transactionTime) FROM transactions")
    suspend fun getFirstTransactionTime(): Long?
}

data class CategoryTotal(
    val categoryId: Long?,
    val total: Double
)
