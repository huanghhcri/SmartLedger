package com.smartledger.ui.backup

import android.app.Application
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import com.smartledger.ui.components.SmartLedgerDialog
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.CsvExporter
import com.smartledger.util.CsvImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupInfo(
    val fileName: String,
    val date: String,
    val size: String,
    val recordCount: Int
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application

    private fun backupDirs(): List<File> = listOf(
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "SmartLedger"
        ),
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SmartLedger"),
        File(context.filesDir, "SmartLedger")
    )

    private val _backupHistory = MutableStateFlow<List<BackupInfo>>(emptyList())
    val backupHistory: StateFlow<List<BackupInfo>> = _backupHistory

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring

    private val _restoreResult = MutableStateFlow<Pair<Boolean, Int>?>(null)
    val restoreResult: StateFlow<Pair<Boolean, Int>?> = _restoreResult

    init {
        loadBackupHistory()
    }

    fun loadBackupHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = mutableListOf<File>()
            for (dir in backupDirs()) {
                if (!dir.exists()) continue
                dir.listFiles()?.filter { it.name.endsWith(".csv") }?.let { files.addAll(it) }
            }
            // 同名只保留最新修改的一份
            val latestByName = files
                .groupBy { it.name }
                .mapValues { (_, list) -> list.maxByOrNull { it.lastModified() }!! }
                .values
                .sortedByDescending { it.lastModified() }

            _backupHistory.value = latestByName.map { file ->
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(Date(file.lastModified()))
                val size = formatFileSize(file.length())
                val lines = try {
                    file.useLines { it.count() } - 1
                } catch (_: Exception) {
                    0
                }
                BackupInfo(file.name, date, size, lines.coerceAtLeast(0))
            }
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
                Toast.makeText(context, "备份失败：无数据或无法写入存储", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restore(context: Context, fileName: String) {
        viewModelScope.launch {
            _isRestoring.value = true
            val count = CsvImporter.restore(context, fileName)
            _isRestoring.value = false
            _restoreResult.value = (count > 0) to count
        }
    }

    fun clearRestoreResult() {
        _restoreResult.value = null
    }

    fun deleteBackup(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            backupDirs().forEach { dir ->
                val file = File(dir, fileName)
                if (file.exists()) file.delete()
            }
            loadBackupHistory()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }
}

@Composable
fun BackupScreen(
    onBack: () -> Unit = {},
    viewModel: BackupViewModel = viewModel()
) {
    val context = LocalContext.current
    val backupHistory by viewModel.backupHistory.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val restoreResult by viewModel.restoreResult.collectAsState()

    val lastBackup = backupHistory.firstOrNull()

    // 恢复确认弹窗
    var showRestoreConfirm by remember { mutableStateOf<BackupInfo?>(null) }
    // 删除确认弹窗
    var showDeleteConfirm by remember { mutableStateOf<BackupInfo?>(null) }

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
                        onClick = {
                            val first = backupHistory.firstOrNull()
                            if (first != null) {
                                showRestoreConfirm = first
                            } else {
                                Toast.makeText(context, "暂无备份文件", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isRestoring && backupHistory.isNotEmpty()
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = SmartLedgerColors.accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("恢复备份")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ═══ 备份历史标题 ═══
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "备份历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                    IconButton(onClick = { viewModel.loadBackupHistory() }) {
                        Icon(Icons.Outlined.Info, contentDescription = "刷新", tint = SmartLedgerColors.fgSecondary)
                    }
                }
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
                        onRestore = { showRestoreConfirm = backup },
                        onDelete = { showDeleteConfirm = backup }
                    )
                }
            }
        }
    }

    // ═══ 恢复确认弹窗 ═══
    showRestoreConfirm?.let { backup ->
        SmartLedgerDialog(
            onDismissRequest = { showRestoreConfirm = null },
            icon = Icons.Outlined.Info,
            iconTint = SmartLedgerColors.accent,
            title = "恢复备份",
            text = "将从「${backup.fileName}」恢复 ${backup.recordCount} 条记录。\n\n已有数据不会被覆盖，恢复的记录将追加到现有数据中。",
            confirmText = "开始恢复",
            onConfirm = {
                showRestoreConfirm = null
                viewModel.restore(context, backup.fileName)
            },
            dismissText = "取消"
        )
    }

    // ═══ 恢复结果弹窗 ═══
    restoreResult?.let { (success, count) ->
        SmartLedgerDialog(
            onDismissRequest = { viewModel.clearRestoreResult() },
            iconTint = if (success) SmartLedgerColors.income else SmartLedgerColors.expense,
            title = if (success) "恢复成功" else "恢复失败",
            text = if (success) "成功恢复 $count 条记录" else "备份文件格式错误或为空",
            confirmText = "确定",
            onConfirm = { viewModel.clearRestoreResult() }
        )
    }

    // ═══ 删除确认弹窗 ═══
    showDeleteConfirm?.let { backup ->
        SmartLedgerDialog(
            onDismissRequest = { showDeleteConfirm = null },
            iconTint = SmartLedgerColors.expense,
            title = "删除备份",
            text = "确定要删除「${backup.date}」的备份文件吗？\n\n删除后无法恢复。",
            confirmText = "删除",
            confirmColor = SmartLedgerColors.expense,
            onConfirm = {
                viewModel.deleteBackup(backup.fileName)
                showDeleteConfirm = null
            },
            dismissText = "取消"
        )
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
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(SmartLedgerColors.income, RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

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

        TextButton(onClick = onRestore) {
            Text(
                text = "恢复",
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.accent
            )
        }
        TextButton(onClick = onDelete) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.expense
            )
        }
    }
}
