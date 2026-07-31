package com.smartledger.ui.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.smartledger.data.db.AppDatabase
import com.smartledger.ui.components.SmartLedgerInputDialog
import com.smartledger.ui.theme.SmartLedgerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    onNavigateToBudget: () -> Unit = {},
    onNavigateToCategory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)

    // 昵称
    var nickname by remember { mutableStateOf(prefs.getString("nickname", "记账用户") ?: "记账用户") }
    var showEditName by remember { mutableStateOf(false) }

    // 陪伴天数
    var daysSinceFirst by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        daysSinceFirst = withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val firstTime = db.transactionDao().getFirstTransactionTime()
                if (firstTime != null && firstTime > 0) {
                    val diff = System.currentTimeMillis() - firstTime
                    maxOf(1, (diff / (1000 * 60 * 60 * 24)).toInt())
                } else {
                    1
                }
            } catch (e: Exception) {
                1
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ═══ 头像 ═══
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(SmartLedgerColors.surfaceHover, CircleShape)
                    .clickable { showEditName = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = nickname.first().toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══ 昵称（可点击修改）═══
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showEditName = true }
            ) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "修改昵称",
                    tint = SmartLedgerColors.fgSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ═══ 陪伴天数 ═══
            Text(
                text = "智能记账已陪伴您 $daysSinceFirst 天",
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.fgSecondary
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ═══ 功能列表 ═══
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(SmartLedgerColors.surface, RoundedCornerShape(16.dp))
            ) {
                MenuItem(
                    icon = Icons.Outlined.Schedule,
                    label = "预算管理",
                    onClick = onNavigateToBudget
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = SmartLedgerColors.border,
                    thickness = 0.5.dp
                )
                MenuItem(
                    icon = Icons.Outlined.GridView,
                    label = "分类管理",
                    onClick = onNavigateToCategory
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = SmartLedgerColors.border,
                    thickness = 0.5.dp
                )
                MenuItem(
                    icon = Icons.Outlined.Download,
                    label = "数据导出",
                    onClick = onExport
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = SmartLedgerColors.border,
                    thickness = 0.5.dp
                )
                MenuItem(
                    icon = Icons.Outlined.SaveAlt,
                    label = "数据备份",
                    onClick = onNavigateToBackup
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = SmartLedgerColors.border,
                    thickness = 0.5.dp
                )
                MenuItem(
                    icon = Icons.Outlined.Settings,
                    label = "设置",
                    onClick = onNavigateToSettings
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ═══ 版本号（读安装包，勿写死）═══
            val appVersionLabel = remember {
                com.smartledger.util.UpdateChecker.currentVersionLabel(context)
            }
            Text(
                text = appVersionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = SmartLedgerColors.fgSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ═══ 修改昵称弹窗 ═══
    if (showEditName) {
        var editName by remember { mutableStateOf(nickname) }
        SmartLedgerInputDialog(
            onDismissRequest = { showEditName = false },
            title = "修改昵称",
            label = "昵称",
            value = editName,
            onValueChange = { editName = it },
            confirmText = "保存",
            onConfirm = {
                val newName = editName.trim()
                if (newName.isNotBlank()) {
                    nickname = newName
                    prefs.edit().putString("nickname", newName).apply()
                }
                showEditName = false
            }
        )
    }
}

// ═══════════════════════════════════════════════════════
// 菜单项
// ═══════════════════════════════════════════════════════

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
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
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = SmartLedgerColors.fgSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
