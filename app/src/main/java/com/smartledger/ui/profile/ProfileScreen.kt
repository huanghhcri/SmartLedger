package com.smartledger.ui.profile

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.ui.theme.SmartLedgerColors

@Composable
fun ProfileScreen(
    onNavigateToBudget: () -> Unit = {},
    onNavigateToCategory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onExport: () -> Unit = {}
) {
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
                    .background(SmartLedgerColors.surfaceHover, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "明",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══ 昵称 ═══
            Text(
                text = "小明",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.fg
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ═══ 记账天数 ═══
            Text(
                text = "本月记账 23 天",
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
                    icon = Icons.Outlined.Backup,
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

            // ═══ 版本号 ═══
            Text(
                text = "v1.0.8",
                style = MaterialTheme.typography.labelMedium,
                color = SmartLedgerColors.fgSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
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

// ═══════════════════════════════════════════════════════
// Preview
// ═══════════════════════════════════════════════════════

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    name = "ProfileScreen"
)
@Composable
private fun ProfileScreenPreview() {
    com.smartledger.ui.theme.SmartLedgerTheme {
        ProfileScreen()
    }
}
