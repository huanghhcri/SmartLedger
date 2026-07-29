package com.smartledger.ui.settings

import android.content.Context
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.ui.theme.SmartLedgerColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var darkMode by remember { mutableStateOf(false) }
    var autoBackup by remember { mutableStateOf(true) }
    var reminderEnabled by remember { mutableStateOf(true) }

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

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ═══ 通用 ═══
            item {
                SettingsGroupHeader("通用")
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Outlined.Language,
                        label = "语言",
                        value = "简体中文",
                        onClick = { Toast.makeText(context, "暂不支持切换语言", Toast.LENGTH_SHORT).show() }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Outlined.AttachMoney,
                        label = "货币格式",
                        value = "CNY (¥)",
                        onClick = { Toast.makeText(context, "暂不支持切换货币", Toast.LENGTH_SHORT).show() }
                    )
                    SettingsDivider()
                    SettingsToggleItem(
                        icon = Icons.Outlined.Notifications,
                        label = "记账提醒",
                        value = if (reminderEnabled) "每天 21:00" else "已关闭",
                        checked = reminderEnabled,
                        onToggle = { reminderEnabled = it }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══ 显示 ═══
            item {
                SettingsGroupHeader("显示")
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Outlined.DarkMode,
                        label = "深色模式",
                        checked = darkMode,
                        onToggle = {
                            darkMode = it
                            Toast.makeText(context, "深色模式${if (it) "开启" else "关闭"}（重启生效）", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Outlined.TextFields,
                        label = "字体大小",
                        value = "标准",
                        onClick = { Toast.makeText(context, "暂不支持调整字体大小", Toast.LENGTH_SHORT).show() }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══ 数据 ═══
            item {
                SettingsGroupHeader("数据")
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Outlined.Backup,
                        label = "自动备份",
                        checked = autoBackup,
                        onToggle = { autoBackup = it }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Outlined.DeleteSweep,
                        label = "清除缓存",
                        value = "24.6 MB",
                        valueColor = SmartLedgerColors.expense,
                        onClick = {
                            Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══ 关于 ═══
            item {
                SettingsGroupHeader("关于")
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        label = "版本号",
                        value = "v1.0.8",
                        onClick = { }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Outlined.Policy,
                        label = "隐私政策",
                        onClick = { Toast.makeText(context, "隐私政策页面开发中", Toast.LENGTH_SHORT).show() }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Outlined.Feedback,
                        label = "反馈建议",
                        onClick = { Toast.makeText(context, "反馈功能开发中", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = SmartLedgerColors.fgSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(SmartLedgerColors.surface, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    value: String? = null,
    valueColor: androidx.compose.ui.graphics.Color = SmartLedgerColors.fgSecondary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = SmartLedgerColors.fg,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = SmartLedgerColors.fg,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = SmartLedgerColors.fgSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    label: String,
    value: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = SmartLedgerColors.fg
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SmartLedgerColors.accent,
                checkedTrackColor = SmartLedgerColors.accentDim
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = SmartLedgerColors.border,
        thickness = 0.5.dp
    )
}
