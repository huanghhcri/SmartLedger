package com.smartledger.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

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
    val context = LocalContext.current

    val nickname = context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)
        .getString("nickname", "记账用户") ?: "记账用户"

    var showMenu by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategorySelect by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // 问候语头部
            item {
                HeaderSection(
                    nickname = nickname,
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
                                showActionMenu = true
                            },
                            onClick = {
                                editingTransaction = transaction
                                showEditDialog = true
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

    // ═══ 长按操作弹窗 ═══
    if (showActionMenu && editingTransaction != null) {
        AlertDialog(
            onDismissRequest = { showActionMenu = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = SmartLedgerColors.surface,
            titleContentColor = SmartLedgerColors.fg,
            title = { Text("操作", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = { showActionMenu = false; showEditDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = SmartLedgerColors.fg, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("编辑账单", color = SmartLedgerColors.fg, modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { showActionMenu = false; showCategorySelect = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.GridView, contentDescription = null, tint = SmartLedgerColors.fg, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("修改分类", color = SmartLedgerColors.fg, modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { showActionMenu = false; showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = SmartLedgerColors.expense, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("删除账单", color = SmartLedgerColors.expense, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showActionMenu = false }) {
                    Text("取消", color = SmartLedgerColors.fgSecondary)
                }
            }
        )
    }

    // ═══ 删除确认弹窗 ═══
    if (showDeleteConfirm && editingTransaction != null) {
        com.smartledger.ui.components.SmartLedgerDialog(
            onDismissRequest = { showDeleteConfirm = false },
            iconTint = SmartLedgerColors.expense,
            title = "删除账单",
            text = "确定要删除这条 ${if (editingTransaction!!.type == "expense") "支出" else "收入"} ¥${CurrencyUtil.format(editingTransaction!!.amount)} 的记录吗？\n\n删除后无法恢复。",
            confirmText = "删除",
            confirmColor = SmartLedgerColors.expense,
            onConfirm = {
                viewModel.deleteTransaction(editingTransaction!!)
                showDeleteConfirm = false
                editingTransaction = null
            },
            dismissText = "取消",
            onDismiss = { showDeleteConfirm = false }
        )
    }

    // ═══ 编辑账单弹窗 ═══
    if (showEditDialog && editingTransaction != null) {
        EditTransactionDialog(
            transaction = editingTransaction!!,
            categories = categories,
            onSave = { updated ->
                viewModel.updateTransaction(updated)
                showEditDialog = false
                editingTransaction = null
            },
            onDismiss = { showEditDialog = false }
        )
    }

    // ═══ 分类选择弹窗 ═══
    if (showCategorySelect && editingTransaction != null) {
        CategoryEditDialog(
            transaction = editingTransaction!!,
            categories = categories,
            onSelect = { newCategoryId ->
                viewModel.updateTransactionCategory(editingTransaction!!, newCategoryId)
                showCategorySelect = false
                editingTransaction = null
            },
            onDismiss = { showCategorySelect = false }
        )
    }
}

// ═══════════════════════════════════════════════════════
// 问候语头部
// ═══════════════════════════════════════════════════════

@Composable
private fun HeaderSection(
    nickname: String,
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
                    text = nickname.first().toString(),
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
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
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
                onClick = { onClick?.invoke() },
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = SmartLedgerColors.surface,
        titleContentColor = SmartLedgerColors.fg,
        title = { Text("选择分类", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                categories.forEach { category ->
                    val isSelected = category.id == transaction.categoryId
                    TextButton(
                        onClick = { onSelect(category.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            category.name,
                            color = if (isSelected) SmartLedgerColors.accent else SmartLedgerColors.fg,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text("✓", color = SmartLedgerColors.accent, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = SmartLedgerColors.fgSecondary)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTransactionDialog(
    transaction: Transaction,
    categories: List<Category>,
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(transaction.amount.toLong().toString()) }
    var merchant by remember { mutableStateOf(transaction.merchant ?: "") }
    var note by remember { mutableStateOf(transaction.note ?: "") }
    var selectedCategoryId by remember { mutableStateOf(transaction.categoryId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = SmartLedgerColors.surface,
        titleContentColor = SmartLedgerColors.fg,
        title = { Text("编辑账单", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 金额
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amountText = it },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SmartLedgerColors.accent,
                        cursorColor = SmartLedgerColors.accent
                    )
                )
                // 商户
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商户名称") },
                    placeholder = { Text("如：蒙牛、美团") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SmartLedgerColors.accent,
                        cursorColor = SmartLedgerColors.accent
                    )
                )
                // 备注
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SmartLedgerColors.accent,
                        cursorColor = SmartLedgerColors.accent
                    )
                )
                // 分类选择
                Text("分类", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.fgSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id == selectedCategoryId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = category.id },
                            label = { Text(category.name, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SmartLedgerColors.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        onSave(
                            transaction.copy(
                                amount = amount,
                                merchant = merchant.ifBlank { null },
                                note = note.ifBlank { null },
                                categoryId = selectedCategoryId
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent)
            ) { Text("保存", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SmartLedgerColors.fgSecondary) }
        }
    )
}
