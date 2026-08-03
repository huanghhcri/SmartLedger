package com.smartledger.ui.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smartledger.ui.theme.SmartLedgerColors

@Composable
fun PermissionGuideScreen(
    onBack: () -> Unit = {},
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationListenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var smsPermissionGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECEIVE_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var postNotificationsGranted by remember {
        mutableStateOf(isPostNotificationsGranted(context))
    }

    val postNotificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        postNotificationsGranted = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationListenerEnabled = isNotificationListenerEnabled(context)
                canDrawOverlays = Settings.canDrawOverlays(context)
                batteryOptimized = isBatteryOptimizationIgnored(context)
                smsPermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECEIVE_SMS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                postNotificationsGranted = isPostNotificationsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = SmartLedgerColors.bg,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(SmartLedgerColors.bg)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent),
                    enabled = notificationListenerEnabled
                ) {
                    Text(
                        "完成设置，开始使用",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (notificationListenerEnabled) "进入软件"
                        else "稍后设置，先手动记账",
                        color = SmartLedgerColors.fgSecondary
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.accentDim)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SmartLedgerColors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "欢迎使用智能记账",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.fg
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "自动识别微信、支付宝、云闪付支付\n静默记入账单，风格与主页一致",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = SmartLedgerColors.fgSecondary
                    )
                }
            }

            Text(
                text = "请开启以下权限：",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = SmartLedgerColors.fg,
                modifier = Modifier.padding(top = 4.dp)
            )

            PermissionItem(
                icon = Icons.Default.Notifications,
                title = "通知使用权（必须）",
                description = "允许读取支付通知，自动识别交易",
                isGranted = notificationListenerEnabled,
                onClick = { openNotificationListenerSettings(context) }
            )

            PermissionItem(
                icon = Icons.Default.Notifications,
                title = "发送通知（推荐）",
                description = "显示记账成功与后台保活通知",
                isGranted = postNotificationsGranted,
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        postNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            PermissionItem(
                icon = Icons.Default.Layers,
                title = "悬浮窗权限（可选）",
                description = "允许弹出支付确认窗口；不开启仍可自动记账",
                isGranted = canDrawOverlays,
                onClick = { openOverlaySettings(context) }
            )

            PermissionItem(
                icon = Icons.Default.Notifications,
                title = "短信权限",
                description = "读取银行短信作为兜底通道（通知监听漏掉时补充）",
                isGranted = smsPermissionGranted,
                onClick = { requestSmsPermission(context) }
            )

            PermissionItem(
                icon = Icons.Default.Add,
                title = "自启动权限",
                description = "允许App后台自启（vivo/iQOO必须）",
                isGranted = false,
                onClick = { openAutoStartSettings(context) }
            )

            PermissionItem(
                icon = Icons.Default.Notifications,
                title = "电池优化",
                description = "关闭电池优化，防止后台被杀",
                isGranted = batteryOptimized,
                onClick = { openBatteryOptimization(context) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isGranted) SmartLedgerColors.accentDim
                        else SmartLedgerColors.surfaceHover
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = if (isGranted) SmartLedgerColors.accent
                    else SmartLedgerColors.fgSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = SmartLedgerColors.fg
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )
            }
            if (isGranted) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已开启",
                    tint = SmartLedgerColors.accent,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SmartLedgerColors.fgSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    return com.smartledger.service.ListenerStatus.isEnabledInSettings(context)
}

private fun isPostNotificationsGranted(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openNotificationListenerSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

private fun openOverlaySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

private fun openAutoStartSettings(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e2: Exception) {
            openAppDetails(context)
        }
    }
}

private fun openBatteryOptimization(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        openAppDetails(context)
    }
}

private fun requestSmsPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

private fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
