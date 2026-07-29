package com.smartledger.data.repository

import com.smartledger.data.db.dao.TransactionDao
import com.smartledger.data.db.dao.CategoryTotal
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val dao: TransactionDao) {

    val allTransactions: Flow<List<Transaction>> = dao.getAll()

    suspend fun insert(transaction: Transaction): Long = dao.insert(transaction)

    suspend fun update(transaction: Transaction) = dao.update(transaction)

    suspend fun delete(transaction: Transaction) = dao.delete(transaction)

    suspend fun getById(id: Long): Transaction? = dao.getById(id)

    suspend fun getByNotificationKey(key: String): Transaction? = dao.getByNotificationKey(key)

    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<Transaction>> =
        dao.getByTimeRange(startTime, endTime)

    fun getExpenseSum(startTime: Long, endTime: Long): Flow<Double> =
        dao.getExpenseSum(startTime, endTime)

    fun getIncomeSum(startTime: Long, endTime: Long): Flow<Double> =
        dao.getIncomeSum(startTime, endTime)

    fun getExpenseByCategory(categoryId: Long, startTime: Long, endTime: Long): Flow<Double> =
        dao.getExpenseByCategory(categoryId, startTime, endTime)

    fun getExpenseGroupByCategory(startTime: Long, endTime: Long): Flow<List<CategoryTotal>> =
        dao.getExpenseGroupByCategory(startTime, endTime)

    fun search(keyword: String): Flow<List<Transaction>> = dao.search(keyword)
}
