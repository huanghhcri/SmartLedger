package com.smartledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartledger.data.db.dao.BudgetDao
import com.smartledger.data.db.dao.CategoryDao
import com.smartledger.data.db.dao.TransactionDao
import com.smartledger.data.db.entity.Budget
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.CategoryBudget
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Transaction::class, Category::class, Budget::class, CategoryBudget::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_ledger.db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDefaultCategories(database.categoryDao())
                }
            }
        }

        private suspend fun populateDefaultCategories(categoryDao: CategoryDao) {
            val defaultCategories = listOf(
                // 支出分类
                Category(name = "餐饮", icon = "Restaurant", color = 0xFFFF5722, type = "expense", sortOrder = 0, isDefault = true),
                Category(name = "交通", icon = "DirectionsCar", color = 0xFF2196F3, type = "expense", sortOrder = 1, isDefault = true),
                Category(name = "购物", icon = "ShoppingBag", color = 0xFFE91E63, type = "expense", sortOrder = 2, isDefault = true),
                Category(name = "娱乐", icon = "SportsEsports", color = 0xFF9C27B0, type = "expense", sortOrder = 3, isDefault = true),
                Category(name = "居住", icon = "Home", color = 0xFF795548, type = "expense", sortOrder = 4, isDefault = true),
                Category(name = "通讯", icon = "Phone", color = 0xFF00BCD4, type = "expense", sortOrder = 5, isDefault = true),
                Category(name = "医疗", icon = "LocalHospital", color = 0xFFF44336, type = "expense", sortOrder = 6, isDefault = true),
                Category(name = "教育", icon = "School", color = 0xFF3F51B5, type = "expense", sortOrder = 7, isDefault = true),
                Category(name = "日用", icon = "ShoppingCart", color = 0xFF4CAF50, type = "expense", sortOrder = 8, isDefault = true),
                Category(name = "其他", icon = "MoreHoriz", color = 0xFF607D8B, type = "expense", sortOrder = 9, isDefault = true),
                // 收入分类
                Category(name = "工资", icon = "Work", color = 0xFF4CAF50, type = "income", sortOrder = 0, isDefault = true),
                Category(name = "理财", icon = "TrendingUp", color = 0xFF00897B, type = "income", sortOrder = 1, isDefault = true),
                Category(name = "红包", icon = "CardGiftcard", color = 0xFFF44336, type = "income", sortOrder = 2, isDefault = true),
                Category(name = "转账", icon = "SwapHoriz", color = 0xFF2196F3, type = "income", sortOrder = 3, isDefault = true),
                Category(name = "其他", icon = "MoreHoriz", color = 0xFF607D8B, type = "income", sortOrder = 4, isDefault = true),
            )
            categoryDao.insertAll(defaultCategories)
        }
    }
}
