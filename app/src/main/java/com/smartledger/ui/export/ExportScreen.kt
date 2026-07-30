package com.smartledger.ui.export

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.SmartLedgerApp
import com.smartledger.ui.components.SmartLedgerDialog
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.CsvExporter
import com.smartledger.util.DateUtil
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

    fun export(startTime: Long, endTime: Long) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportResult.value = CsvExporter.export(context, startTime, endTime)
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // 自定义日期范围
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val cal = remember { Calendar.getInstance() }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // 计算时间跨度文本
    val timeSpanText = when (selectedRange) {
        "month" -> {
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            "$year.${String.format("%02d", month)}.01 — ${String.format("%02d", month)}.${String.format("%02d", day)}"
        }
        "year" -> {
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            "$year.01.01 — ${year}.${String.format("%02d", month)}.${String.format("%02d", day)}"
        }
        "all" -> "全部记录"
        "custom" -> {
            if (customStartDate != null && customEndDate != null) {
                "${dateFormat.format(Date(customStartDate!!))} — ${dateFormat.format(Date(customEndDate!!))}"
            } else {
                "请选择日期范围"
            }
        }
        else -> ""
    }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RangePill("本月", selectedRange == "month") { selectedRange = "month" }
                RangePill("本年", selectedRange == "year") { selectedRange = "year" }
                RangePill("全部", selectedRange == "all") { selectedRange = "all" }
                RangePill("自选", selectedRange == "custom") { selectedRange = "custom" }
            }

            // ═══ 自定义日期选择 ═══
            if (selectedRange == "custom") {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "选择日期范围",
                            style = MaterialTheme.typography.labelMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 开始日期
                            OutlinedCard(
                                onClick = { showStartPicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("开始日期", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.fgSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = customStartDate?.let { dateFormat.format(Date(it)) } ?: "点击选择",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (customStartDate != null) SmartLedgerColors.fg else SmartLedgerColors.accent
                                    )
                                }
                            }

                            Text("至", color = SmartLedgerColors.fgSecondary)

                            // 结束日期
                            OutlinedCard(
                                onClick = { showEndPicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("结束日期", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.fgSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = customEndDate?.let { dateFormat.format(Date(it)) } ?: "点击选择",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (customEndDate != null) SmartLedgerColors.fg else SmartLedgerColors.accent
                                    )
                                }
                            }
                        }

                        // 快捷选项
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickDateChip("最近7天") {
                                customEndDate = System.currentTimeMillis()
                                customStartDate = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                            }
                            QuickDateChip("最近30天") {
                                customEndDate = System.currentTimeMillis()
                                customStartDate = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
                            }
                            QuickDateChip("最近90天") {
                                customEndDate = System.currentTimeMillis()
                                customStartDate = System.currentTimeMillis() - 90 * 24 * 60 * 60 * 1000L
                            }
                        }
                    }
                }
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
                    PreviewRow("时间跨度", timeSpanText)
                    PreviewRow("包含字段", "日期 · 分类 · 金额 · 商户 · 备注")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ═══ 导出按钮 ═══
            Button(
                onClick = {
                    if (!isExporting) {
                        when (selectedRange) {
                            "month" -> viewModel.export(currentYearMonth)
                            "custom" -> {
                                if (customStartDate != null && customEndDate != null) {
                                    viewModel.export(customStartDate!!, customEndDate!! + 24 * 60 * 60 * 1000L - 1)
                                }
                            }
                            else -> viewModel.export(null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent),
                enabled = !isExporting && (selectedRange != "custom" || (customStartDate != null && customEndDate != null))
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

    // ═══ 日期选择器 ═══
    if (showStartPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customStartDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customStartDate = datePickerState.selectedDateMillis
                    // 如果结束日期早于开始日期，自动调整
                    if (customEndDate != null && customEndDate!! < customStartDate!!) {
                        customEndDate = customStartDate
                    }
                    showStartPicker = false
                }) {
                    Text("确定", color = SmartLedgerColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("取消", color = SmartLedgerColors.fgSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customEndDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customEndDate = datePickerState.selectedDateMillis
                    // 如果开始日期晚于结束日期，自动调整
                    if (customStartDate != null && customStartDate!! > customEndDate!!) {
                        customStartDate = customEndDate
                    }
                    showEndPicker = false
                }) {
                    Text("确定", color = SmartLedgerColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text("取消", color = SmartLedgerColors.fgSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ═══ 导出成功弹窗 ═══
    if (exportResult != null) {
        SmartLedgerDialog(
            onDismissRequest = { viewModel.clearResult() },
            
            
            title = "导出成功",
            text = "文件已保存到 Documents/SmartLedger/ 目录",
            confirmText = "打开文件",
            onConfirm = {
                exportResult?.let { uri ->
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "text/csv")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "无法打开文件", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                viewModel.clearResult()
            },
            dismissText = "打开文件路径",
            onDismiss = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        val dirUri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents%2FSmartLedger")
                        setDataAndType(dirUri, "vnd.android.document/directory")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e2: Exception) {
                        android.widget.Toast.makeText(context, "Documents/SmartLedger/", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                viewModel.clearResult()
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
private fun QuickDateChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SmartLedgerColors.surfaceHover)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = SmartLedgerColors.accent,
            fontWeight = FontWeight.Medium
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
