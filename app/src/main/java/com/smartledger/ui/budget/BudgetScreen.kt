package com.smartledger.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.data.db.entity.Category
import com.smartledger.ui.components.SmartLedgerDialog
import com.smartledger.ui.components.SmartLedgerInputDialog
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.CurrencyUtil
import com.smartledger.util.DateUtil

@Composable
fun BudgetScreen(
    onBack: () -> Unit = {},
    viewModel: BudgetViewModel = viewModel()
) {
    val budget by viewModel.budget.collectAsState()
    val monthExpense by viewModel.monthExpense.collectAsState(initial = 0.0)
    val monthIncome by viewModel.monthIncome.collectAsState(initial = 0.0)
    val expenseByCategory by viewModel.expenseByCategory.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())

    var showEditDialog by remember { mutableStateOf(false) }
    val categoryMap = categories.associateBy { it.id }

    val totalBudget = budget?.totalExpenseLimit ?: 8000.0
    val remaining = totalBudget - monthExpense
    val usedPercent = if (totalBudget > 0) (monthExpense / totalBudget * 100) else 0.0

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ═══ 顶部栏 ═══
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = SmartLedgerColors.fg
                        )
                    }
                    Text(
                        text = "预算管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                }
            }

            // ═══ 预算总览卡片 ═══
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "本月总预算",
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartLedgerColors.fgSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "¥",
                                style = MaterialTheme.typography.titleLarge,
                                color = SmartLedgerColors.fgSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = CurrencyUtil.format(totalBudget),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp
                                ),
                                color = SmartLedgerColors.fg
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已用 ¥${CurrencyUtil.format(monthExpense)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SmartLedgerColors.fgSecondary
                            )
                            Text(
                                text = "剩余 ¥${CurrencyUtil.format(remaining)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (remaining >= 0) SmartLedgerColors.income else SmartLedgerColors.expense
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 进度条
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(SmartLedgerColors.surfaceHover, RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (usedPercent / 100).toFloat().coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .background(
                                        if (usedPercent > 80) SmartLedgerColors.expense else SmartLedgerColors.accent,
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${String.format("%.1f", usedPercent)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.fgSecondary
                            )
                            Text(
                                text = "剩余 ${getDaysLeftInMonth()} 天",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.fgSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 编辑按钮
                        TextButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("编辑预算")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ═══ 分类预算标题 ═══
            item {
                Text(
                    text = "分类预算",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // ═══ 分类预算列表 ═══
            val categoryBudgets = expenseByCategory.take(6)
            if (categoryBudgets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无分类支出数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                    }
                }
            } else {
                items(categoryBudgets) { ct ->
                    val category = categoryMap[ct.categoryId]
                    val categoryBudget = totalBudget * 0.3 // 简化：平均分配
                    val spent = ct.total
                    val percent = if (categoryBudget > 0) (spent / categoryBudget * 100) else 0.0

                    CategoryBudgetItem(
                        categoryName = category?.name ?: "未分类",
                        categoryIcon = getCategoryIcon(category?.name ?: ""),
                        spent = spent,
                        budget = categoryBudget,
                        percent = percent
                    )
                }
            }
        }
    }

    // ═══ 编辑预算弹窗 ═══
    if (showEditDialog) {
        EditBudgetDialog(
            currentBudget = totalBudget,
            onSave = { newBudget ->
                viewModel.saveBudget(null, newBudget)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
private fun CategoryBudgetItem(
    categoryName: String,
    categoryIcon: ImageVector,
    spent: Double,
    budget: Double,
    percent: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SmartLedgerColors.surfaceHover, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIcon,
                contentDescription = categoryName,
                tint = SmartLedgerColors.fgSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = SmartLedgerColors.fg
                )
                Row {
                    Text(
                        text = "¥${CurrencyUtil.format(spent)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = SmartLedgerColors.fg
                    )
                    Text(
                        text = " / ¥${CurrencyUtil.format(budget)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = SmartLedgerColors.fgSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(SmartLedgerColors.surfaceHover, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (percent / 100).toFloat().coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(
                            if (percent > 80) SmartLedgerColors.expense else SmartLedgerColors.accent,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun EditBudgetDialog(
    currentBudget: Double,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetText by remember { mutableStateOf(currentBudget.toLong().toString()) }

    SmartLedgerInputDialog(
        onDismissRequest = onDismiss,
        title = "编辑月度预算",
        label = "预算金额",
        value = budgetText,
        onValueChange = { budgetText = it },
        prefix = "¥",
        confirmText = "保存",
        onConfirm = {
            budgetText.toDoubleOrNull()?.let { onSave(it) }
        }
    )
}

private fun getDaysLeftInMonth(): Int {
    val cal = java.util.Calendar.getInstance()
    val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    return maxDay - today
}

private fun getCategoryIcon(name: String): ImageVector {
    return when (name) {
        "餐饮" -> Icons.Outlined.Restaurant
        "交通" -> Icons.Outlined.DirectionsBus
        "购物" -> Icons.Outlined.ShoppingBag
        "娱乐" -> Icons.Outlined.OndemandVideo
        "居住" -> Icons.Outlined.Home
        "医疗" -> Icons.Outlined.FavoriteBorder
        "教育" -> Icons.Outlined.MenuBook
        else -> Icons.Outlined.MoreHoriz
    }
}
