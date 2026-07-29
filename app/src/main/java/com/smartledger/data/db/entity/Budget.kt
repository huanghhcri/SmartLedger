package com.smartledger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val yearMonth: String,
    val totalIncomeTarget: Double? = null,
    val totalExpenseLimit: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)
