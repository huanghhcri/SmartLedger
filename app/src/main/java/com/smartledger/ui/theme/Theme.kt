package com.smartledger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════
// SmartLedger Design Tokens — 暖白 Linear 风格
// ═══════════════════════════════════════════════════════

// 亮色基础色
val Background = Color(0xFFFAFAF8)
val Surface = Color(0xFFFFFFFF)
val SurfaceHover = Color(0xFFF5F5F3)
val Foreground = Color(0xFF1A1A1A)
val ForegroundSecondary = Color(0xFF8B8B8B)
val Border = Color(0xFFE8E8E4)
val BorderSubtle = Color(0xFFF0F0EC)

// 深色基础色
val DarkBackground = Color(0xFF111113)
val DarkSurface = Color(0xFF1A1A1E)
val DarkSurfaceHover = Color(0xFF232328)
val DarkForeground = Color(0xFFE8E8EA)
val DarkForegroundSecondary = Color(0xFF8A8A8E)
val DarkBorder = Color(0xFF2A2A2E)
val DarkBorderSubtle = Color(0xFF222226)

// 强调色
val Accent = Color(0xFF6C63FF)
val AccentDim = Color(0x1F6C63FF)
val DarkAccentDim = Color(0x336C63FF)

// 收支配色
val ExpenseRed = Color(0xFFD94848)
val ExpenseRedDim = Color(0x1AD94848)
val IncomeGreen = Color(0xFF2D9D63)
val IncomeGreenDim = Color(0x1A2D9D63)

// 图表色
val ChartGray1 = Color(0xFF94A3B8)
val ChartGray2 = Color(0xFFA8B8CC)
val ChartGray3 = Color(0xFFBCC8DA)
val ChartGray4 = Color(0xFFCBD5E1)
val ChartGray5 = Color(0xFFDDE4ED)
val ChartGray6 = Color(0xFFE8EDF3)

val DarkChartGray1 = Color(0xFF475569)
val DarkChartGray2 = Color(0xFF526275)
val DarkChartGray3 = Color(0xFF5D6F85)
val DarkChartGray4 = Color(0xFF687C95)
val DarkChartGray5 = Color(0xFF7389A5)
val DarkChartGray6 = Color(0xFF7E96B5)

// 底部导航
val NavUnselected = Color(0xFFB0B0B0)
val NavSelected = Foreground
val DarkNavUnselected = Color(0xFF666668)
val DarkNavSelected = DarkForeground

// ═══════════════════════════════════════════════════════
// 自定义颜色扩展
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

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D2466),
    onPrimaryContainer = Color(0xFFCBC2FF),
    secondary = DarkForegroundSecondary,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceHover,
    onSecondaryContainer = DarkForeground,
    tertiary = IncomeGreen,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkSurface,
    onSurface = DarkForeground,
    surfaceVariant = DarkSurfaceHover,
    onSurfaceVariant = DarkForegroundSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
)

// ═══════════════════════════════════════════════════════
// 主题模式管理
// ═══════════════════════════════════════════════════════

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeManager {
    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: State<ThemeMode> = _themeMode

    fun setTheme(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun init(mode: ThemeMode) {
        _themeMode.value = mode
    }
}

// ═══════════════════════════════════════════════════════
// 主题入口
// ═══════════════════════════════════════════════════════

@Composable
fun SmartLedgerTheme(
    content: @Composable () -> Unit
) {
    val themeMode by ThemeManager.themeMode
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val extendedColors = if (isDark) ExtendedColors(
        accentDim = DarkAccentDim,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceHover = DarkSurfaceHover,
        foreground = DarkForeground,
        foregroundSecondary = DarkForegroundSecondary,
        border = DarkBorder,
        navUnselected = DarkNavUnselected,
        navSelected = DarkNavSelected,
        chartColors = listOf(DarkChartGray1, DarkChartGray2, DarkChartGray3, DarkChartGray4, DarkChartGray5, DarkChartGray6)
    ) else ExtendedColors()

    // 状态栏颜色（仅 Activity 场景；悬浮窗等非 Activity Context 不可强转）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            activity.window.statusBarColor = extendedColors.background.toArgb()
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
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
