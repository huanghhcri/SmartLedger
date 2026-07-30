package com.smartledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.CurrencyUtil
import com.smartledger.util.DateUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    var searchText by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())

    // 筛选状态
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedPayment by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchText) {
        viewModel.search(searchText)
    }

    // 支付方式列表
    val paymentMethods = listOf("微信", "支付宝", "云闪付", "现金", "银行卡")

    // 应用筛选
    val filteredResults = searchResults.filter { t ->
        (selectedType == null || t.type == selectedType) &&
        (selectedCategory == null || categories.find { it.id == t.categoryId }?.name == selectedCategory) &&
        (selectedPayment == null || t.paymentMethod == selectedPayment)
    }

    Scaffold(
        containerColor = SmartLedgerColors.bg,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("搜索商户名、备注、金额...", color = SmartLedgerColors.fgSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空")
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = SmartLedgerColors.fgSecondary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SmartLedgerColors.accent,
                            cursorColor = SmartLedgerColors.accent,
                            unfocusedContainerColor = SmartLedgerColors.surface,
                            focusedContainerColor = SmartLedgerColors.surface
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = SmartLedgerColors.fg)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SmartLedgerColors.bg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ═══ 筛选栏 ═══
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 类型筛选
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterPill("全部", selectedType == null) { selectedType = null }
                    }
                    item {
                        FilterPill("支出", selectedType == "expense") { selectedType = "expense" }
                    }
                    item {
                        FilterPill("收入", selectedType == "income") { selectedType = "income" }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 支付方式筛选
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterPill("全部渠道", selectedPayment == null) { selectedPayment = null }
                    }
                    items(paymentMethods) { method ->
                        FilterPill(method, selectedPayment == method) { selectedPayment = method }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 分类筛选
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterPill("全部分类", selectedCategory == null) { selectedCategory = null }
                    }
                    items(categories) { category ->
                        FilterPill(category.name, selectedCategory == category.name) {
                            selectedCategory = if (selectedCategory == category.name) null else category.name
                        }
                    }
                }
            }

            HorizontalDivider(color = SmartLedgerColors.border, thickness = 0.5.dp)

            // ═══ 搜索结果 ═══
            if (searchText.isBlank() && selectedType == null && selectedCategory == null && selectedPayment == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "输入关键词或选择筛选条件",
                        color = SmartLedgerColors.fgSecondary
                    )
                }
            } else if (filteredResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有找到匹配的记录",
                        color = SmartLedgerColors.fgSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    item {
                        Text(
                            text = "找到 ${filteredResults.size} 条记录",
                            style = MaterialTheme.typography.labelMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                    }

                    items(filteredResults) { transaction ->
                        SearchResultItem(
                            transaction = transaction,
                            categoryName = viewModel.getCategoryName(transaction.categoryId, categories)
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) SmartLedgerColors.accent
                else SmartLedgerColors.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Color.White else SmartLedgerColors.fg
        )
    }
}

@Composable
private fun SearchResultItem(transaction: Transaction, categoryName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = SmartLedgerColors.surfaceHover,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryName.first().toString(),
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.fg
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = SmartLedgerColors.fg
                )
                Row {
                    val subtitle = buildString {
                        transaction.merchant?.let { append(it) }
                        if (transaction.merchant != null && transaction.note != null) append(" · ")
                        transaction.note?.let { append(it) }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartLedgerColors.fgSecondary
                        )
                    }
                    if (transaction.paymentMethod != null) {
                        Text(
                            text = " · ${transaction.paymentMethod}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartLedgerColors.accent
                        )
                    }
                }
                Text(
                    text = DateUtil.formatDateTime(transaction.transactionTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.fgSecondary
                )
            }
            Text(
                text = "${if (transaction.type == "expense") "-" else "+"}￥${CurrencyUtil.format(transaction.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == "expense") SmartLedgerColors.expense else SmartLedgerColors.income
            )
        }
    }
}
