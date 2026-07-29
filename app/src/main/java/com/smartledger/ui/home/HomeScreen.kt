package com.smartledger.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
fun HomeScreen(
    onNavigateToRecord: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onExport: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val todayExpense by viewModel.todayExpense.collectAsState(initial = 0.0)
    val monthExpense by viewModel.monthExpense.collectAsState(initial = 0.0)
    val monthIncome by viewModel.monthIncome.collectAsState(initial = 0.0)
    val recentTransactions by viewModel.recentTransactions.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())

    var showMenu by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // 问候语头部
            item {
                HeaderSection(
                    showMenu = showMenu,
                    onMenuToggle = { showMenu = it },
                    onNavigateToSearch = onNavigateToSearch,
                    onExport = onExport
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 本月支出大字 + 收入/结余
            item {
                BalanceSection(
                    monthExpense = monthExpense,
                    monthIncome = monthIncome,
                    todayExpense = todayExpense
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 最近交易标题
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近交易",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                    if (recentTransactions.isNotEmpty()) {
                        Text(
                            text = "${recentTransactions.size} 笔",
                            style = MaterialTheme.typography.labelMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // 交易列表
            if (recentTransactions.isEmpty()) {
                item {
                    EmptyStateCard()
                }
            } else {
                val grouped = recentTransactions.groupBy {
                    DateUtil.formatDate(it.transactionTime)
                }
                grouped.forEach { (date, transactions) ->
                    item {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelMedium,
                            color = SmartLedgerColors.fgSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(transactions) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            categoryName = viewModel.getCategoryName(transaction.categoryId, categories),
                            categoryColor = viewModel.getCategoryColor(transaction.categoryId, categories)?.let { Color(it) },
                            onLongClick = {
                                editingTransaction = transaction
                                showCategoryDialog = true
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // 浮动记账按钮
        FloatingActionButton(
            onClick = onNavigateToRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
            containerColor = SmartLedgerColors.accent,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "记一笔")
        }
    }

    // 修改分类弹窗
    if (showCategoryDialog && editingTransaction != null) {
        CategoryEditDialog(
            transaction = editingTransaction!!,
            categories = categories,
            onSelect = { newCategoryId ->
                viewModel.updateTransactionCategory(editingTransaction!!, newCategoryId)
                showCategoryDialog = false
                editingTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(editingTransaction!!)
                showCategoryDialog = false
                editingTransaction = null
            },
            onDismiss = {
                showCategoryDialog = false
                editingTransaction = null
            }
        )
    }
}

// ═══════════════════════════════════════════════════════
// 问候语头部
// ═══════════════════════════════════════════════════════

@Composable
private fun HeaderSection(
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onNavigateToSearch: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "你好",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.fg
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = DateUtil.formatDate(System.currentTimeMillis()) + " · " + DateUtil.getDayOfWeek(System.currentTimeMillis()),
                style = MaterialTheme.typography.bodySmall,
                color = SmartLedgerColors.fgSecondary
            )
        }

        Row {
            IconButton(onClick = onNavigateToSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = SmartLedgerColors.fgSecondary
                )
            }
            Box {
                IconButton(onClick = { onMenuToggle(true) }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = SmartLedgerColors.fgSecondary
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onMenuToggle(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("导出CSV") },
                        onClick = {
                            onMenuToggle(false)
                            onExport()
                        },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                    )
                }
            }
            // 头像
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SmartLedgerColors.surfaceHover, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "明",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = SmartLedgerColors.fg
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 余额区域 — 大字支出 + 收入/结余小字
// ═══════════════════════════════════════════════════════

@Composable
private fun BalanceSection(
    monthExpense: Double,
    monthIncome: Double,
    todayExpense: Double
) {
    val balance = monthIncome - monthExpense

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // 本月支出标签
        Text(
            text = "本月支出",
            style = MaterialTheme.typography.bodySmall,
            color = SmartLedgerColors.fgSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 大字金额
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.titleLarge,
                color = SmartLedgerColors.fgSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = CurrencyUtil.format(monthExpense),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = (-1).sp
                ),
                color = SmartLedgerColors.fg
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 收入 + 结余 + 今日支出
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 收入
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SmartLedgerColors.income, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "收入",
                        style = MaterialTheme.typography.labelMedium,
                        color = SmartLedgerColors.fgSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${CurrencyUtil.format(monthIncome)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
            }

            // 结余
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SmartLedgerColors.accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "结余",
                        style = MaterialTheme.typography.labelMedium,
                        color = SmartLedgerColors.fgSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${CurrencyUtil.format(balance)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
            }

            // 今日支出
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "今日支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = SmartLedgerColors.fgSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${CurrencyUtil.format(todayExpense)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.expense
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 空状态
// ═══════════════════════════════════════════════════════

@Composable
private fun EmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "还没有记录",
            style = MaterialTheme.typography.titleMedium,
            color = SmartLedgerColors.fg
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右下角「+」开始记账",
            style = MaterialTheme.typography.bodySmall,
            color = SmartLedgerColors.fgSecondary
        )
    }
}

// ═══════════════════════════════════════════════════════
// 交易项 — 极简列表样式
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionItem(
    transaction: Transaction,
    categoryName: String,
    categoryColor: Color? = null,
    onLongClick: (() -> Unit)? = null
) {
    val dotColor = categoryColor ?: when (categoryName) {
        "餐饮" -> SmartLedgerColors.expense
        "交通" -> Color(0xFF3E63DD)
        "购物" -> Color(0xFF9C27B0)
        "娱乐" -> Color(0xFFFF7043)
        "居住" -> Color(0xFF795548)
        "医疗" -> Color(0xFFE91E63)
        "教育" -> Color(0xFF3F51B5)
        "工资" -> SmartLedgerColors.income
        "理财" -> SmartLedgerColors.income
        else -> SmartLedgerColors.fgSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 分类色点
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(14.dp))

        // 分类名 + 商户
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = SmartLedgerColors.fg
            )
            if (transaction.merchant != null || transaction.paymentMethod != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        transaction.merchant?.let { append(it) }
                        if (transaction.merchant != null && transaction.paymentMethod != null) append(" · ")
                        transaction.paymentMethod?.let { append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )
            }
        }

        // 金额 + 时间
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (transaction.type == "expense") "-" else "+"}¥${CurrencyUtil.format(transaction.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.type == "expense") SmartLedgerColors.expense else SmartLedgerColors.income
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateUtil.formatTime(transaction.transactionTime),
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.fgSecondary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// 分类编辑弹窗
// ═══════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════
// Preview
// ═══════════════════════════════════════════════════════

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    name = "HomeScreen"
)
@Composable
private fun HomeScreenPreview() {
    com.smartledger.ui.theme.SmartLedgerTheme {
        HomeScreen()
    }
}

@Composable
private fun CategoryEditDialog(
    transaction: Transaction,
    categories: List<Category>,
    onSelect: (Long?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改分类") },
        text = {
            Column {
                categories.forEach { category ->
                    TextButton(
                        onClick = { onSelect(category.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(category.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("删除", color = SmartLedgerColors.expense)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
