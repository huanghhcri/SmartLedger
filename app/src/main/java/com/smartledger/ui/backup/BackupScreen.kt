package com.smartledger.ui.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.CsvExporter
import com.smartledger.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════
// Data
// ═══════════════════════════════════════════════════════

data class BackupInfo(
    val fileName: String,
    val date: String,
    val size: String,
    val recordCount: Int
)

// ═══════════════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════════════

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val backupDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "SmartLedger"
    )

    private val _backupHistory = MutableStateFlow<List<BackupInfo>>(emptyList())
    val backupHistory: StateFlow<List<BackupInfo>> = _backupHistory

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp

    init {
        loadBackupHistory()
    }

    private fun loadBackupHistory() {
        viewModelScope.launch {
            val history = mutableListOf<BackupInfo>()
            if (backupDir.exists()) {
                backupDir.listFiles()?.filter { it.name.endsWith(".csv") }?.sortedByDescending { it.lastModified() }?.forEach { file ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(file.lastModified()))
                    val size = formatFileSize(file.length())
                    val lines = file.readLines().size - 1 // 减去标题行
                    history.add(BackupInfo(file.name, date, size, lines.coerceAtLeast(0)))
                }
            }
            _backupHistory.value = history
        }
    }

    fun backup(context: Context) {
        viewModelScope.launch {
            _isBackingUp.value = true
            val uri = CsvExporter.export(context)
            _isBackingUp.value = false
            if (uri != null) {
                Toast.makeText(context, "备份成功", Toast.LENGTH_SHORT).show()
                loadBackupHistory()
            } else {
                Toast.makeText(context, "备份失败：没有数据", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restore(context: Context, fileName: String) {
        Toast.makeText(context, "恢复功能开发中", Toast.LENGTH_SHORT).show()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }
}

// ═══════════════════════════════════════════════════════
// Screen
// ═══════════════════════════════════════════════════════

@Composable
fun BackupScreen(
    onBack: () -> Unit = {},
    viewModel: BackupViewModel = viewModel()
) {
    val context = LocalContext.current
    val backupHistory by viewModel.backupHistory.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()

    // 备份状态
    val lastBackup = backupHistory.firstOrNull()

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
                        text = "数据备份",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ═══ 备份状态卡片 ═══
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        StatusRow("备份状态", "正常", SmartLedgerColors.income)
                        StatusRow("上次备份", lastBackup?.date ?: "暂无", SmartLedgerColors.fg)
                        StatusRow("文件大小", lastBackup?.size ?: "-", SmartLedgerColors.fg)
                        StatusRow("记录总数", "${lastBackup?.recordCount ?: 0} 笔", SmartLedgerColors.fg)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══ 操作按钮 ═══
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.backup(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent),
                        enabled = !isBackingUp
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("立即备份")
                        }
                    }
                    OutlinedButton(
                        onClick = { Toast.makeText(context, "恢复功能开发中", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("恢复备份")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ═══ 备份历史标题 ═══
            item {
                Text(
                    text = "备份历史",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // ═══ 备份历史列表 ═══
            if (backupHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无备份记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                    }
                }
            } else {
                items(backupHistory) { backup ->
                    BackupHistoryItem(
                        backup = backup,
                        onRestore = { viewModel.restore(context, backup.fileName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SmartLedgerColors.fgSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = valueColor
        )
    }
}

@Composable
private fun BackupHistoryItem(
    backup: BackupInfo,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆点
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(SmartLedgerColors.income, RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        // 信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backup.date,
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.fg
            )
            Text(
                text = "${backup.size} · ${backup.recordCount} 笔",
                style = MaterialTheme.typography.bodySmall,
                color = SmartLedgerColors.fgSecondary
            )
        }

        // 恢复按钮
        TextButton(onClick = onRestore) {
            Text(
                text = "恢复",
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.fgSecondary
            )
        }
    }
}
