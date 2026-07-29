package com.smartledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════
// SmartLedger Design Tokens — 暖白 Linear 风格
// 从 Open Design 原型 HTML CSS 变量直接映射
// ═══════════════════════════════════════════════════════

// 基础色
val Background = Color(0xFFFAFAF8)       // --bg: 奶白暖调背景
val Surface = Color(0xFFFFFFFF)           // --surface: 卡片白
val SurfaceHover = Color(0xFFF5F5F3)      // --surface-hover
val Foreground = Color(0xFF1A1A1A)        // --fg: 主文字（深灰，非纯黑）
val ForegroundSecondary = Color(0xFF8B8B8B) // --fg-secondary: 次要文字
val Border = Color(0xFFE8E8E4)            // --border: 分割线
val BorderSubtle = Color(0xFFF0F0EC)      // 更细分割线

// 强调色
val Accent = Color(0xFF6C63FF)            // --accent: 蓝紫强调色
val AccentDim = Color(0x1F6C63FF)         // --accent-dim: 12% 透明

// 收支配色（降饱和）
val ExpenseRed = Color(0xFFD94848)        // --expense: 支出红（降饱和）
val ExpenseRedDim = Color(0x1AD94848)     // --expense-dim: 10% 透明
val ExpenseRedLight = Color(0x1AD94848)   // 浅底色
val IncomeGreen = Color(0xFF2D9D63)       // --income: 收入绿（降饱和）
val IncomeGreenDim = Color(0x1A2D9D63)    // --income-dim: 10% 透明
val IncomeGreenLight = Color(0x1A2D9D63)  // 浅底色

// 图表色（柔灰度系列，用于统计页饼图）
val ChartGray1 = Color(0xFF94A3B8)
val ChartGray2 = Color(0xFFA8B8CC)
val ChartGray3 = Color(0xFFBCC8DA)
val ChartGray4 = Color(0xFFCBD5E1)
val ChartGray5 = Color(0xFFDDE4ED)
val ChartGray6 = Color(0xFFE8EDF3)

// 底部导航
val NavUnselected = Color(0xFFB0B0B0)     // 未选中
val NavSelected = Foreground               // 选中：深灰

// ═══════════════════════════════════════════════════════
// 自定义颜色扩展（通过 CompositionLocal 传递）
// ═══════════════════════════════════════════════════════

@Immutable
data class ExtendedColors(
    val expense: Color = ExpenseRed,
    val expenseDim: Color = ExpenseRedDim,
    val income: Color = IncomeGreen,
    val incomeDim: Color = IncomeGreenDim,
    val accent: Color = Accent,
    val accentDim: Color = AccentDim,
    val background: Color = Background,
    val surface: Color = Surface,
    val surfaceHover: Color = SurfaceHover,
    val foreground: Color = Foreground,
    val foregroundSecondary: Color = ForegroundSecondary,
    val border: Color = Border,
    val navUnselected: Color = NavUnselected,
    val navSelected: Color = NavSelected,
    val chartColors: List<Color> = listOf(
        ChartGray1, ChartGray2, ChartGray3, ChartGray4, ChartGray5, ChartGray6
    )
)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

// ═══════════════════════════════════════════════════════
// Material 3 配色方案
// ═══════════════════════════════════════════════════════

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FF),
    onPrimaryContainer = Color(0xFF3D2DB5),
    secondary = ForegroundSecondary,
    onSecondary = Color.White,
    secondaryContainer = SurfaceHover,
    onSecondaryContainer = Foreground,
    tertiary = IncomeGreen,
    onTertiary = Color.White,
    background = Background,
    onBackground = Foreground,
    surface = Surface,
    onSurface = Foreground,
    surfaceVariant = SurfaceHover,
    onSurfaceVariant = ForegroundSecondary,
    outline = Border,
    outlineVariant = BorderSubtle,
)

@Composable
fun SmartLedgerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    CompositionLocalProvider(LocalExtendedColors provides ExtendedColors()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// 便捷访问扩展色
object SmartLedgerColors {
    val expense: Color
        @Composable get() = LocalExtendedColors.current.expense
    val expenseDim: Color
        @Composable get() = LocalExtendedColors.current.expenseDim
    val income: Color
        @Composable get() = LocalExtendedColors.current.income
    val incomeDim: Color
        @Composable get() = LocalExtendedColors.current.incomeDim
    val accent: Color
        @Composable get() = LocalExtendedColors.current.accent
    val accentDim: Color
        @Composable get() = LocalExtendedColors.current.accentDim
    val bg: Color
        @Composable get() = LocalExtendedColors.current.background
    val surface: Color
        @Composable get() = LocalExtendedColors.current.surface
    val surfaceHover: Color
        @Composable get() = LocalExtendedColors.current.surfaceHover
    val fg: Color
        @Composable get() = LocalExtendedColors.current.foreground
    val fgSecondary: Color
        @Composable get() = LocalExtendedColors.current.foregroundSecondary
    val border: Color
        @Composable get() = LocalExtendedColors.current.border
    val navUnselected: Color
        @Composable get() = LocalExtendedColors.current.navUnselected
    val navSelected: Color
        @Composable get() = LocalExtendedColors.current.navSelected
    val chartColors: List<Color>
        @Composable get() = LocalExtendedColors.current.chartColors
}
