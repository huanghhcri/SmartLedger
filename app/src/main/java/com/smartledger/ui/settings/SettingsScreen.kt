package com.smartledger.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.ui.theme.ThemeManager
import com.smartledger.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)

    // 深色模式状态
    val themeMode by ThemeManager.themeMode
    var autoBackupEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_backup", true))
    }
    var debugToastsEnabled by remember {
        mutableStateOf(prefs.getBoolean("debug_toasts", false))
    }

    // 检查更新状态
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<com.smartledger.util.UpdateChecker.UpdateInfo?>(null) }
    var showNoUpdate by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
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
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = SmartLedgerColors.fg)
                    }
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ═══ 外观 ═══
            item {
                SectionTitle("外观")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
                ) {
                    Column {
                        ThemeOption(
                            label = "跟随系统",
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = {
                                ThemeManager.setTheme(ThemeMode.SYSTEM)
                                prefs.edit().putString("theme_mode", "SYSTEM").apply()
                            }
                        )
                        DividerLine()
                        ThemeOption(
                            label = "浅色模式",
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = {
                                ThemeManager.setTheme(ThemeMode.LIGHT)
                                prefs.edit().putString("theme_mode", "LIGHT").apply()
                            }
                        )
                        DividerLine()
                        ThemeOption(
                            label = "深色模式",
                            selected = themeMode == ThemeMode.DARK,
                            onClick = {
                                ThemeManager.setTheme(ThemeMode.DARK)
                                prefs.edit().putString("theme_mode", "DARK").apply()
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══ 数据 ═══
            item {
                SectionTitle("数据")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
                ) {
                    Column {
                        SwitchItem(
                            icon = Icons.Outlined.Download,
                            label = "自动备份",
                            description = "每周自动备份一次数据",
                            checked = autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                autoBackupEnabled = enabled
                                prefs.edit().putBoolean("auto_backup", enabled).apply()
                                if (enabled) {
                                    com.smartledger.util.AutoBackupScheduler.schedule(context)
                                    Toast.makeText(context, "已开启每周自动备份", Toast.LENGTH_SHORT).show()
                                } else {
                                    com.smartledger.util.AutoBackupScheduler.cancel(context)
                                    Toast.makeText(context, "已关闭自动备份", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DividerLine()
                        SwitchItem(
                            icon = Icons.Outlined.Info,
                            label = "调试提示",
                            description = "自动记账时弹出 Toast 提示（排查问题用）",
                            checked = debugToastsEnabled,
                            onCheckedChange = { enabled ->
                                debugToastsEnabled = enabled
                                prefs.edit().putBoolean("debug_toasts", enabled).apply()
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══ 自动记账 ═══
            item {
                SectionTitle("自动记账")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
                ) {
                    Column {
                        MenuSettingItem(
                            icon = Icons.Outlined.Notifications,
                            label = "通知使用权",
                            onClick = {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            }
                        )
                        DividerLine()
                        MenuSettingItem(
                            icon = Icons.Outlined.Layers,
                            label = "悬浮窗权限",
                            onClick = {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            }
                        )
                        DividerLine()
                        MenuSettingItem(
                            icon = Icons.Outlined.BatteryStd,
                            label = "电池优化",
                            onClick = {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══ 关于 ═══
            item {
                SectionTitle("关于")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
                ) {
                    Column {
                        MenuSettingItem(
                            icon = Icons.Outlined.Email,
                            label = "反馈建议",
                            onClick = onNavigateToFeedback
                        )
                        DividerLine()
                        MenuSettingItem(
                            icon = Icons.Outlined.Description,
                            label = "隐私政策",
                            onClick = onNavigateToPrivacy
                        )
                        DividerLine()
                        MenuSettingItem(
                            icon = Icons.Outlined.Refresh,
                            label = "检查更新",
                            subtitle = if (isCheckingUpdate) "检查中..." else null,
                            onClick = {
                                if (!isCheckingUpdate) {
                                    isCheckingUpdate = true
                                    coroutineScope.launch {
                                        val info = com.smartledger.util.UpdateChecker.checkForUpdate(context)
                                        isCheckingUpdate = false
                                        if (info != null) {
                                            updateResult = info
                                        } else {
                                            showNoUpdate = true
                                        }
                                    }
                                }
                            }
                        )
                        DividerLine()
                        MenuSettingItem(
                            icon = Icons.Outlined.Info,
                            label = "版本号",
                            subtitle = "v1.0.2",
                            onClick = { }
                        )
                    }
                }
            }
        }
    }

    // ═══ 检查更新结果弹窗 ═══
    updateResult?.let { info ->
        com.smartledger.ui.components.SmartLedgerDialog(
            onDismissRequest = { updateResult = null },
            iconTint = SmartLedgerColors.accent,
            title = "发现新版本 ${info.versionName}",
            text = if (info.releaseNotes.isNotBlank()) info.releaseNotes.take(300) else "有新版本可用，建议更新。",
            confirmText = "立即更新",
            onConfirm = {
                com.smartledger.util.UpdateChecker.openDownloadPage(context, info)
                updateResult = null
            },
            dismissText = "稍后再说",
            onDismiss = { updateResult = null }
        )
    }

    if (showNoUpdate) {
        com.smartledger.ui.components.SmartLedgerDialog(
            onDismissRequest = { showNoUpdate = false },
            iconTint = SmartLedgerColors.income,
            title = "已是最新版本",
            text = "当前版本 v1.0.2 已是最新，无需更新。",
            confirmText = "好的",
            onConfirm = { showNoUpdate = false }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = SmartLedgerColors.fgSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = SmartLedgerColors.accent,
                unselectedColor = SmartLedgerColors.fgSecondary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = SmartLedgerColors.fg
        )
    }
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = SmartLedgerColors.fg,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = SmartLedgerColors.fg)
            Text(description, style = MaterialTheme.typography.bodySmall, color = SmartLedgerColors.fgSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SmartLedgerColors.accent,
                checkedTrackColor = SmartLedgerColors.accentDim
            )
        )
    }
}

@Composable
private fun MenuSettingItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = SmartLedgerColors.fg, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = SmartLedgerColors.fg, modifier = Modifier.weight(1f))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.fgSecondary)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SmartLedgerColors.fgSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = SmartLedgerColors.border,
        thickness = 0.5.dp
    )
}
