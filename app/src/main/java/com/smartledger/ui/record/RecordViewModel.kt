package com.smartledger.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val transactionRepo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    fun getCategories(type: String): Flow<List<Category>> = categoryRepo.getByType(type)

    fun saveTransaction(
        amount: Double,
        type: String,
        categoryId: Long?,
        merchant: String?,
        paymentMethod: String?,
        note: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = amount,
                type = type,
                categoryId = categoryId,
                merchant = merchant,
                paymentMethod = paymentMethod,
                note = note,
                source = "manual",
                transactionTime = System.currentTimeMillis()
            )
            transactionRepo.insert(transaction)
            onSuccess()
        }
    }
}
