package com.smartledger.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.data.db.dao.CategoryTotal
import com.smartledger.data.db.entity.Category
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.CurrencyUtil
import com.smartledger.util.DateUtil

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = viewModel()) {
    val selectedPeriod by viewModel.selectedPeriod.collectAsState(initial = "month")
    val periodExpense by viewModel.periodExpense.collectAsState(initial = 0.0)
    val periodIncome by viewModel.periodIncome.collectAsState(initial = 0.0)
    val expenseByCategory by viewModel.expenseByCategory.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())

    val categoryMap = categories.associateBy { it.id }

    // 灰度图表色系
    val chartColors = SmartLedgerColors.chartColors

    // 周期标签映射
    val periodLabel = when (selectedPeriod) {
        "day" -> "今日支出"
        "week" -> "本周支出"
        "year" -> "本年支出"
        else -> "本月支出"
    }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ═══ 周期切换 ═══
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf("日" to "day", "周" to "week", "月" to "month", "年" to "year").forEach { (label, value) ->
                        PeriodTab(
                            text = label,
                            selected = selectedPeriod == value,
                            onClick = { viewModel.setPeriod(value) }
                        )
                        if (value != "year") Spacer(modifier = Modifier.width(32.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // ═══ 环形图 + 总支出 ═══
            item {
                DonutChartSection(
                    periodExpense = periodExpense,
                    periodLabel = periodLabel,
                    expenseByCategory = expenseByCategory,
                    categoryMap = categoryMap,
                    chartColors = chartColors
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // ═══ 分类排行标题 ═══
            item {
                Text(
                    text = "分类排行",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ═══ 分类排行列表 ═══
            if (expenseByCategory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                    }
                }
            } else {
                items(expenseByCategory.take(8).mapIndexed { index, ct ->
                    Triple(ct, categoryMap[ct.categoryId], index)
                }) { (categoryTotal, category, index) ->
                    CategoryRankingItem(
                        categoryTotal = categoryTotal,
                        categoryName = category?.name ?: "未分类",
                        categoryColor = chartColors[index % chartColors.size],
                        totalExpense = periodExpense,
                        maxExpense = expenseByCategory.firstOrNull()?.total ?: 1.0
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 周期切换标签
// ═══════════════════════════════════════════════════════

@Composable
private fun PeriodTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SmartLedgerColors.fg else SmartLedgerColors.fgSecondary
        )
        if (selected) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .background(SmartLedgerColors.fg, RoundedCornerShape(1.dp))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// 环形图区域
// ═══════════════════════════════════════════════════════

@Composable
private fun DonutChartSection(
    periodExpense: Double,
    periodLabel: String,
    expenseByCategory: List<CategoryTotal>,
    categoryMap: Map<Long, Category>,
    chartColors: List<Color>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // 捕获颜色值（Canvas 内不能调用 @Composable）
            val borderColor = SmartLedgerColors.border

            // 环形图
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 24.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                val rect = Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)

                var startAngle = -90f
                val total = expenseByCategory.sumOf { it.total }.toFloat()

                if (total > 0) {
                    expenseByCategory.take(6).forEachIndexed { index, ct ->
                        val sweep = (ct.total.toFloat() / total) * 360f
                        drawArc(
                            color = chartColors[index % chartColors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = rect,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }
                } else {
                    drawArc(
                        color = borderColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = rect,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // 中心文字
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = SmartLedgerColors.fgSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${CurrencyUtil.format(periodExpense)}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = SmartLedgerColors.fg
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 分类排行项
// ═══════════════════════════════════════════════════════

@Composable
private fun CategoryRankingItem(
    categoryTotal: CategoryTotal,
    categoryName: String,
    categoryColor: Color,
    totalExpense: Double,
    maxExpense: Double
) {
    val percentage = if (totalExpense > 0) (categoryTotal.total / totalExpense * 100) else 0.0
    val barWidth = if (maxExpense > 0) (categoryTotal.total / maxExpense).toFloat() else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SmartLedgerColors.surfaceHover, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                getCategoryIcon(categoryName),
                contentDescription = categoryName,
                tint = categoryColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 名称 + 进度条
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = SmartLedgerColors.fg
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(SmartLedgerColors.surfaceHover, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = barWidth.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(categoryColor, RoundedCornerShape(2.dp))
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 金额 + 百分比
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "¥${CurrencyUtil.format(categoryTotal.total)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.fg
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.fgSecondary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// 分类图标映射
// ═══════════════════════════════════════════════════════

private fun getCategoryIcon(name: String): ImageVector {
    return when (name) {
        "餐饮" -> Icons.Outlined.Restaurant
        "交通" -> Icons.Outlined.DirectionsBus
        "购物" -> Icons.Outlined.ShoppingBag
        "娱乐" -> Icons.Outlined.OndemandVideo
        "居住" -> Icons.Outlined.Home
        "医疗" -> Icons.Outlined.FavoriteBorder
        "教育" -> Icons.Outlined.MenuBook
        "通讯" -> Icons.Outlined.Phone
        "日用" -> Icons.Outlined.ShoppingCart
        "工资" -> Icons.Outlined.AttachMoney
        "理财" -> Icons.Outlined.TrendingUp
        "红包" -> Icons.Outlined.CardGiftcard
        "转账" -> Icons.Outlined.SwapHoriz
        else -> Icons.Outlined.MoreHoriz
    }
}

// ═══════════════════════════════════════════════════════
// Preview
// ═══════════════════════════════════════════════════════

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    name = "StatisticsScreen"
)
@Composable
private fun StatisticsScreenPreview() {
    com.smartledger.ui.theme.SmartLedgerTheme {
        StatisticsScreen()
    }
}
