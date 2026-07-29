package com.smartledger.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val transactionRepo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword

    val searchResults: Flow<List<Transaction>> = _keyword.flatMapLatest { kw ->
        if (kw.isBlank()) flowOf(emptyList())
        else transactionRepo.search(kw)
    }

    val categories: Flow<List<Category>> = categoryRepo.getAll()

    fun search(keyword: String) {
        _keyword.value = keyword
    }

    fun getCategoryName(categoryId: Long?, categories: List<Category>): String {
        return categories.find { it.id == categoryId }?.name ?: "其他"
    }
}
