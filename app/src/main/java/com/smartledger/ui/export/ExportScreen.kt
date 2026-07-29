package com.smartledger.ui.export

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.smartledger.SmartLedgerApp
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.CsvExporter
import com.smartledger.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════════════

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val transactionRepo = (application as SmartLedgerApp).transactionRepository
    private val context = application

    private val _exportResult = MutableStateFlow<Uri?>(null)
    val exportResult: StateFlow<Uri?> = _exportResult

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    fun export(yearMonth: String?) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportResult.value = CsvExporter.export(context, yearMonth)
            _isExporting.value = false
        }
    }

    fun shareExport(context: Context, uri: Uri) {
        CsvExporter.shareFile(context, uri)
    }

    fun clearResult() {
        _exportResult.value = null
    }
}

// ═══════════════════════════════════════════════════════
// Screen
// ═══════════════════════════════════════════════════════

@Composable
fun ExportScreen(
    onBack: () -> Unit = {},
    viewModel: ExportViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf("csv") }
    var selectedRange by remember { mutableStateOf("month") }

    val exportResult by viewModel.exportResult.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val currentYearMonth = DateUtil.getCurrentYearMonth()
    val yearMonth = when (selectedRange) {
        "month" -> currentYearMonth
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ═══ 顶部栏 ═══
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
                    text = "数据导出",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══ 导出格式 ═══
            Text(
                text = "导出格式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.fg,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormatOption(
                    label = "CSV",
                    sublabel = "通用格式",
                    selected = selectedFormat == "csv",
                    onClick = { selectedFormat = "csv" },
                    modifier = Modifier.weight(1f)
                )
                FormatOption(
                    label = "Excel",
                    sublabel = ".xlsx",
                    selected = selectedFormat == "xlsx",
                    onClick = { selectedFormat = "xlsx" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══ 时间范围 ═══
            Text(
                text = "时间范围",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.fg,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RangePill("本月", selectedRange == "month") { selectedRange = "month" }
                RangePill("本年", selectedRange == "year") { selectedRange = "year" }
                RangePill("全部", selectedRange == "all") { selectedRange = "all" }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══ 导出预览 ═══
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    PreviewRow("记录数", "47 笔")
                    PreviewRow("时间跨度", getTimeSpan(selectedRange))
                    PreviewRow("预计文件大小", "~12 KB")
                    PreviewRow("包含字段", "日期 · 分类 · 金额 · 备注")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ═══ 导出按钮 ═══
            Button(
                onClick = {
                    if (!isExporting) {
                        viewModel.export(yearMonth)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "导出文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 导出成功弹窗
    if (exportResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearResult() },
            title = { Text("导出成功") },
            text = { Text("文件已保存到 Documents/SmartLedger/ 目录") },
            confirmButton = {
                TextButton(onClick = {
                    exportResult?.let { viewModel.shareExport(context, it) }
                    viewModel.clearResult()
                }) { Text("分享") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearResult() }) { Text("确定") }
            }
        )
    }
}

@Composable
private fun FormatOption(
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SmartLedgerColors.accentDim else SmartLedgerColors.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, SmartLedgerColors.accent) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) SmartLedgerColors.accent else SmartLedgerColors.fg
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = SmartLedgerColors.fgSecondary
            )
        }
    }
}

@Composable
private fun RangePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) SmartLedgerColors.fg else SmartLedgerColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) SmartLedgerColors.bg else SmartLedgerColors.fg
        )
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
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
            color = SmartLedgerColors.fg
        )
    }
}

private fun getTimeSpan(range: String): String {
    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH) + 1
    val year = cal.get(Calendar.YEAR)
    val day = cal.get(Calendar.DAY_OF_MONTH)
    return when (range) {
        "month" -> "$year.${String.format("%02d", month)}.01 — ${String.format("%02d", month)}.${String.format("%02d", day)}"
        "year" -> "$year.01.01 — ${year}.${String.format("%02d", month)}.${String.format("%02d", day)}"
        else -> "全部记录"
    }
}
