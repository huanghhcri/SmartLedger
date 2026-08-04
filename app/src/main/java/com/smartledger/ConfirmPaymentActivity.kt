package com.smartledger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.data.db.AppDatabase
import com.smartledger.data.db.entity.Transaction
import com.smartledger.service.PendingConfirmStore
import com.smartledger.service.SmartCategorizer
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.ui.theme.SmartLedgerTheme
import com.smartledger.ui.theme.ThemeManager
import com.smartledger.ui.theme.ThemeMode
import com.smartledger.util.CurrencyUtil
import com.smartledger.util.NotificationStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模糊/可疑账单确认：样式与应用内 SmartLedgerDialog 一致，跟随亮/暗/系统主题。
 * 确认前不落库。
 */
class ConfirmPaymentActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 与主界面同一套主题偏好，避免冷启动弹窗颜色违和
        val prefs = getSharedPreferences("smart_ledger", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", "SYSTEM")
        ThemeManager.init(
            try {
                ThemeMode.valueOf(savedTheme ?: "SYSTEM")
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        )

        val pendingId = intent.getLongExtra(EXTRA_PENDING_ID, -1L)
        val pending = PendingConfirmStore.get(pendingId)
        if (pending == null) {
            Toast.makeText(this, "确认信息已失效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            SmartLedgerTheme(syncSystemBars = false) {
                val scope = rememberCoroutineScope()
                var amountText by remember {
                    mutableStateOf(
                        if (pending.amount > 0) {
                            if (pending.amount == pending.amount.toLong().toDouble()) {
                                pending.amount.toLong().toString()
                            } else {
                                String.format("%.2f", pending.amount)
                            }
                        } else ""
                    )
                }
                var type by remember { mutableStateOf(pending.type) }
                var merchant by remember { mutableStateOf(pending.merchant ?: "") }
                var saving by remember { mutableStateOf(false) }

                fun dismissIgnore() {
                    PendingConfirmStore.remove(pendingId)
                    NotificationStyle.cancelConfirm(this@ConfirmPaymentActivity, pendingId)
                    finish()
                }

                AlertDialog(
                    onDismissRequest = { dismissIgnore() },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = SmartLedgerColors.surface,
                    titleContentColor = SmartLedgerColors.fg,
                    textContentColor = SmartLedgerColors.fgSecondary,
                    icon = {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = SmartLedgerColors.accent
                        )
                    },
                    title = {
                        Text(
                            "请确认这笔账单",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val subtitle = buildString {
                                append(pending.paymentMethod)
                                if (!pending.reason.isNullOrBlank()) {
                                    append(" · ")
                                    append(pending.reason)
                                }
                            }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SmartLedgerColors.fgSecondary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = type == "expense",
                                    onClick = { type = "expense" },
                                    label = { Text("支出") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SmartLedgerColors.expenseDim,
                                        selectedLabelColor = SmartLedgerColors.expense,
                                        containerColor = SmartLedgerColors.surfaceHover,
                                        labelColor = SmartLedgerColors.fgSecondary
                                    )
                                )
                                FilterChip(
                                    selected = type == "income",
                                    onClick = { type = "income" },
                                    label = { Text("收入") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SmartLedgerColors.incomeDim,
                                        selectedLabelColor = SmartLedgerColors.income,
                                        containerColor = SmartLedgerColors.surfaceHover,
                                        labelColor = SmartLedgerColors.fgSecondary
                                    )
                                )
                            }

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { raw ->
                                    if (raw.isEmpty() || raw.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$"))) {
                                        amountText = raw
                                    }
                                },
                                label = { Text("金额") },
                                prefix = { Text("¥") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SmartLedgerColors.accent,
                                    focusedLabelColor = SmartLedgerColors.accent,
                                    cursorColor = SmartLedgerColors.accent,
                                    focusedTextColor = SmartLedgerColors.fg,
                                    unfocusedTextColor = SmartLedgerColors.fg
                                )
                            )

                            OutlinedTextField(
                                value = merchant,
                                onValueChange = { merchant = it.take(40) },
                                label = { Text("商户（可选）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SmartLedgerColors.accent,
                                    focusedLabelColor = SmartLedgerColors.accent,
                                    cursorColor = SmartLedgerColors.accent,
                                    focusedTextColor = SmartLedgerColors.fg,
                                    unfocusedTextColor = SmartLedgerColors.fg
                                )
                            )

                            if (!pending.rawSnippet.isNullOrBlank()) {
                                Text(
                                    pending.rawSnippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SmartLedgerColors.fgSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(0.dp))
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull()
                                if (amount == null || amount <= 0) {
                                    Toast.makeText(
                                        this@ConfirmPaymentActivity,
                                        "请输入有效金额",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                saving = true
                                scope.launch {
                                    val ok = saveConfirmed(
                                        pending = pending,
                                        amount = amount,
                                        type = type,
                                        merchant = merchant.trim().ifBlank { null }
                                    )
                                    withContext(Dispatchers.Main) {
                                        saving = false
                                        PendingConfirmStore.remove(pendingId)
                                        NotificationStyle.cancelConfirm(
                                            this@ConfirmPaymentActivity,
                                            pendingId
                                        )
                                        Toast.makeText(
                                            this@ConfirmPaymentActivity,
                                            if (ok) "已记入 ¥${CurrencyUtil.format(amount)}"
                                            else "保存失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        finish()
                                    }
                                }
                            },
                            enabled = !saving,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SmartLedgerColors.accent
                            )
                        ) {
                            Text(
                                if (saving) "保存中…" else "计入账单",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { dismissIgnore() },
                            enabled = !saving,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("忽略", color = SmartLedgerColors.fgSecondary)
                        }
                    }
                )
            }
        }
    }

    private suspend fun saveConfirmed(
        pending: PendingConfirmStore.Pending,
        amount: Double,
        type: String,
        merchant: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val amountCents = CurrencyUtil.toCents(amount)
            val duplicate = com.smartledger.service.DedupHelper.findDuplicate(
                db.transactionDao(),
                amountCents,
                type,
                merchant,
                pending.paymentMethod,
                pending.transactionTime
            )
            if (duplicate != null) {
                com.smartledger.service.DedupHelper.mergeIfDuplicate(
                    db.transactionDao(),
                    duplicate,
                    pending.paymentMethod,
                    merchant
                )
                return@withContext true
            }

            var categoryId: Long? = null
            try {
                val categories = db.categoryDao().getAllOnce()
                categoryId = SmartCategorizer.categorize(
                    merchant = merchant,
                    paymentMethod = pending.paymentMethod,
                    note = null,
                    categories = categories,
                    type = type
                )
            } catch (_: Exception) {
            }

            val id = db.transactionDao().insert(
                Transaction(
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    merchant = merchant,
                    paymentMethod = pending.paymentMethod,
                    note = pending.reason?.let { "确认入账：$it" },
                    source = "auto",
                    notificationKey = pending.notificationKey,
                    transactionTime = pending.transactionTime
                )
            )
            NotificationStyle.notifyPaymentDetected(
                applicationContext,
                amount,
                merchant,
                pending.paymentMethod,
                type,
                id
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
