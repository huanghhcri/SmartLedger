package com.smartledger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId"), Index("transactionTime"), Index("notificationKey")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String,
    val categoryId: Long? = null,
    val merchant: String? = null,
    val paymentMethod: String? = null,
    val note: String? = null,
    val source: String,
    val notificationKey: String? = null,
    val transactionTime: Long,
    val createdAt: Long = System.currentTimeMillis()
)
