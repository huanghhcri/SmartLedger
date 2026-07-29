package com.smartledger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 柱状图组件
 */
@Composable
fun BarChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    animate: Boolean = true
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.value }.toFloat()
    if (maxValue <= 0) return

    var animationProgress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 800),
        label = "bar_anim"
    )

    LaunchedEffect(data) {
        animationProgress = 1f
    }

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val barCount = data.size
        val totalWidth = size.width
        val totalHeight = size.height
        val bottomPadding = 40f
        val topPadding = 16f
        val chartHeight = totalHeight - bottomPadding - topPadding
        val barWidth = (totalWidth / barCount) * 0.6f
        val gap = (totalWidth / barCount) * 0.4f

        data.forEachIndexed { index, entry ->
            val barHeight = (entry.value.toFloat() / maxValue) * chartHeight * animatedProgress
            val x = index * (barWidth + gap) + gap / 2
            val y = topPadding + chartHeight - barHeight

            // 画圆角柱子
            drawRoundRect(
                color = entry.color ?: barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // 画标签
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(
                    entry.label,
                    x + barWidth / 2,
                    totalHeight - 4f,
                    paint
                )
            }
        }
    }
}

/**
 * 折线图组件
 */
@Composable
fun LineChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    animate: Boolean = true
) {
    if (data.size < 2) return

    val maxValue = data.maxOf { it.value }.toFloat()
    if (maxValue <= 0) return

    var animationProgress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 1000),
        label = "line_anim"
    )

    LaunchedEffect(data) {
        animationProgress = 1f
    }

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val bottomPadding = 40f
        val topPadding = 16f
        val chartHeight = totalHeight - bottomPadding - topPadding
        val stepX = totalWidth / (data.size - 1)

        // 画网格线
        for (i in 0..4) {
            val y = topPadding + chartHeight * i / 4
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(totalWidth, y),
                strokeWidth = 1f
            )
        }

        // 画折线
        val path = Path()
        data.forEachIndexed { index, entry ->
            val x = index * stepX
            val y = topPadding + chartHeight - (entry.value.toFloat() / maxValue) * chartHeight * animatedProgress
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f)
        )

        // 画圆点
        data.forEachIndexed { index, entry ->
            val x = index * stepX
            val y = topPadding + chartHeight - (entry.value.toFloat() / maxValue) * chartHeight * animatedProgress
            drawCircle(
                color = lineColor,
                radius = 5f,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = Offset(x, y)
            )
        }

        // 画标签
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = textColor.hashCode()
                textSize = 26f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            // 只显示首尾和中间的标签
            val labelIndices = if (data.size <= 5) {
                data.indices.toList()
            } else {
                listOf(0, data.size / 4, data.size / 2, data.size * 3 / 4, data.size - 1)
            }
            labelIndices.forEach { index ->
                if (index < data.size) {
                    drawText(
                        data[index].label,
                        index * stepX,
                        totalHeight - 4f,
                        paint
                    )
                }
            }
        }
    }
}

/**
 * 饼图组件
 */
@Composable
fun PieChart(
    data: List<ChartEntry>,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0) return

    var animationProgress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 800),
        label = "pie_anim"
    )

    LaunchedEffect(data) {
        animationProgress = 1f
    }

    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height)
        val radius = diameter / 2
        val center = Offset(size.width / 2, size.height / 2)
        val rect = androidx.compose.ui.geometry.Rect(
            center.x - radius,
            center.y - radius,
            center.x + radius,
            center.y + radius
        )

        var startAngle = -90f
        data.forEach { entry ->
            val sweepAngle = (entry.value.toFloat() / total) * 360f * animatedProgress
            drawArc(
                color = entry.color ?: Color.Gray,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height)
            )
            startAngle += sweepAngle
        }
    }
}

data class ChartEntry(
    val label: String,
    val value: Double,
    val color: Color? = null
)
