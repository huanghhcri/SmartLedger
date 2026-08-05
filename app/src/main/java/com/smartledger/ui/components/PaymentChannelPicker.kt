package com.smartledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.PaymentMethods

/**
 * 支付渠道选择：预设芯片 + 自定义输入
 *
 * @param selected 当前渠道名（预设或自定义文案）
 * @param onSelected 选择变化
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentChannelPicker(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "渠道"
) {
    var customMode by remember {
        mutableStateOf(selected.isNotBlank() && !PaymentMethods.isPreset(selected))
    }
    var customText by remember {
        mutableStateOf(
            if (selected.isNotBlank() && !PaymentMethods.isPreset(selected)) selected else ""
        )
    }

    // 外部切换账单时同步（编辑弹窗）
    LaunchedEffect(selected) {
        if (PaymentMethods.isPreset(selected)) {
            customMode = false
        } else if (selected.isNotBlank()) {
            customMode = true
            customText = selected
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SmartLedgerColors.fgSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PaymentMethods.PRESETS.forEach { method ->
                FilterChip(
                    selected = !customMode && selected == method,
                    onClick = {
                        customMode = false
                        onSelected(method)
                    },
                    label = { Text(method, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SmartLedgerColors.accent,
                        selectedLabelColor = Color.White
                    )
                )
            }
            FilterChip(
                selected = customMode,
                onClick = {
                    customMode = true
                    onSelected(customText.trim())
                },
                label = { Text(PaymentMethods.CUSTOM_LABEL, fontSize = 12.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SmartLedgerColors.accent,
                    selectedLabelColor = Color.White
                )
            )
        }
        if (customMode) {
            OutlinedTextField(
                value = customText,
                onValueChange = {
                    customText = it
                    onSelected(it.trim())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text("输入渠道名称，如招商银行") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SmartLedgerColors.accent,
                    cursorColor = SmartLedgerColors.accent
                )
            )
        }
    }
}
