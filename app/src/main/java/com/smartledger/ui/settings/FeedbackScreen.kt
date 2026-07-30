package com.smartledger.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.ui.theme.SmartLedgerColors

@Composable
fun FeedbackScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部栏
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
                    text = "反馈建议",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 联系方式卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.accentDim)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = null,
                        tint = SmartLedgerColors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "开发者邮箱",
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartLedgerColors.fgSecondary
                        )
                        Text(
                            "joah45@qq.com",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = SmartLedgerColors.accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 提示卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.accentDim)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "💡 反馈提示",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.accent
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "如遇「支付了却没有自动记账」的问题，请在反馈中附上：\n" +
                        "1. 支付方式（微信/支付宝/银行等）\n" +
                        "2. 系统通知栏中该条支付通知的截图\n\n" +
                        "我们会根据真实通知格式优化解析规则，提升识别率。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartLedgerColors.fgSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 反馈内容
            Text(
                text = "反馈内容",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = SmartLedgerColors.fgSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = feedbackText,
                onValueChange = { feedbackText = it },
                placeholder = { Text("请描述您遇到的问题或建议...", color = SmartLedgerColors.fgSecondary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(min = 150.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SmartLedgerColors.accent,
                    focusedLabelColor = SmartLedgerColors.accent,
                    cursorColor = SmartLedgerColors.accent,
                    unfocusedContainerColor = SmartLedgerColors.surface,
                    focusedContainerColor = SmartLedgerColors.surface
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 联系方式
            Text(
                text = "联系方式（可选）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = SmartLedgerColors.fgSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = contactInfo,
                onValueChange = { contactInfo = it },
                placeholder = { Text("QQ / 微信 / 邮箱", color = SmartLedgerColors.fgSecondary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SmartLedgerColors.accent,
                    focusedLabelColor = SmartLedgerColors.accent,
                    cursorColor = SmartLedgerColors.accent,
                    unfocusedContainerColor = SmartLedgerColors.surface,
                    focusedContainerColor = SmartLedgerColors.surface
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 发送按钮
            Button(
                onClick = {
                    if (feedbackText.isBlank()) {
                        Toast.makeText(context, "请输入反馈内容", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    try {
                        val subject = "SmartLedger 反馈建议"
                        val body = buildString {
                            appendLine("反馈内容：")
                            appendLine(feedbackText)
                            if (contactInfo.isNotBlank()) {
                                appendLine()
                                appendLine("联系方式：$contactInfo")
                            }
                            appendLine()
                            appendLine("---")
                            appendLine("设备信息：${android.os.Build.MODEL}")
                            appendLine("系统版本：Android ${android.os.Build.VERSION.RELEASE}")
                            appendLine("App版本：v1.0.2")
                        }
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("joah45@qq.com"))
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 如果没有邮件客户端，复制到剪贴板
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("feedback", "joah45@qq.com\n$feedbackText")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制邮箱和反馈内容到剪贴板", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent)
            ) {
                Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("发送邮件", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
