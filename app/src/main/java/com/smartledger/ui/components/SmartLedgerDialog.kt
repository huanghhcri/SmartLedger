package com.smartledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.ui.theme.SmartLedgerColors

/**
 * SmartLedger 统一弹窗样式
 * 所有弹窗都使用此组件，确保风格一致（暖白 Linear 风格）
 */
@Composable
fun SmartLedgerDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    confirmText: String? = null,
    confirmColor: Color = SmartLedgerColors.accent,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = SmartLedgerColors.surface,
        titleContentColor = SmartLedgerColors.fg,
        textContentColor = SmartLedgerColors.fgSecondary,
        icon = icon?.let {
            {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SmartLedgerColors.surfaceHover),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = iconTint ?: SmartLedgerColors.accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.fgSecondary
                )
            }
            if (content != null) {
                Column { content() }
            }
        },
        confirmButton = {
            if (confirmText != null && onConfirm != null) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = confirmColor),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(confirmText, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (dismissText.isNotBlank()) {
                TextButton(
                    onClick = { onDismiss?.invoke() ?: onDismissRequest() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(dismissText, color = SmartLedgerColors.fgSecondary)
                }
            }
        }
    )
}

/**
 * 带输入框的弹窗
 */
@Composable
fun SmartLedgerInputDialog(
    onDismissRequest: () -> Unit,
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    prefix: String? = null,
    confirmText: String = "保存",
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = SmartLedgerColors.surface,
        titleContentColor = SmartLedgerColors.fg,
        textContentColor = SmartLedgerColors.fgSecondary,
        title = {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                prefix = prefix?.let { { Text(it) } },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SmartLedgerColors.accent,
                    focusedLabelColor = SmartLedgerColors.accent,
                    cursorColor = SmartLedgerColors.accent
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("取消", color = SmartLedgerColors.fgSecondary)
            }
        }
    )
}
