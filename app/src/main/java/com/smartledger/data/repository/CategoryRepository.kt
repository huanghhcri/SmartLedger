package com.smartledger.data.repository

import com.smartledger.data.db.dao.CategoryDao
import com.smartledger.data.db.entity.Category
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val dao: CategoryDao) {

    fun getByType(type: String): Flow<List<Category>> = dao.getByType(type)

    fun getAll(): Flow<List<Category>> = dao.getAll()

    suspend fun getById(id: Long): Category? = dao.getById(id)

    suspend fun insert(category: Category): Long = dao.insert(category)

    suspend fun update(category: Category) = dao.update(category)

    suspend fun delete(category: Category) = dao.delete(category)

    suspend fun count(): Int = dao.count()
}
