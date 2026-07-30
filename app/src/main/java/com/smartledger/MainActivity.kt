package com.smartledger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartledger.ui.home.HomeScreen
import com.smartledger.ui.home.SearchScreen
import com.smartledger.ui.home.SearchViewModel
import com.smartledger.ui.components.SmartLedgerDialog
import com.smartledger.ui.navigation.Screen
import com.smartledger.ui.navigation.bottomNavItems
import com.smartledger.ui.profile.ProfileScreen
import com.smartledger.ui.record.RecordScreen
import com.smartledger.ui.statistics.StatisticsScreen
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.ui.theme.SmartLedgerTheme
import com.smartledger.ui.theme.ThemeManager
import com.smartledger.ui.theme.ThemeMode
import com.smartledger.util.CsvExporter
import android.widget.Toast
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化主题模式
        val prefs = getSharedPreferences("smart_ledger", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", "SYSTEM")
        ThemeManager.init(ThemeMode.valueOf(savedTheme ?: "SYSTEM"))

        // 注册自动备份（如果已开启）
        val autoBackupEnabled = prefs.getBoolean("auto_backup", true)
        if (autoBackupEnabled) {
            com.smartledger.util.AutoBackupScheduler.schedule(this)
        }

        setContent {
            SmartLedgerTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 检查是否首次启动
    var isFirstLaunch by remember {
        mutableStateOf(
            context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)
                .getBoolean("first_launch", true)
        )
    }

    // 重装恢复检测：首次启动 + 有备份文件 + 数据库为空
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupFileToRestore by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isFirstLaunch) {
        if (isFirstLaunch) {
            val prefs = context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)
            val hasShownRestore = prefs.getBoolean("has_shown_restore_prompt", false)
            if (!hasShownRestore) {
                // 检查是否有备份文件
                val backupDir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                    "SmartLedger"
                )
                val backupFiles = backupDir.listFiles()?.filter {
                    it.name.endsWith(".csv") && it.name.startsWith("SmartLedger")
                }?.sortedByDescending { it.lastModified() }

                if (!backupFiles.isNullOrEmpty()) {
                    // 检查数据库是否为空（重装后数据库是空的）
                    val db = com.smartledger.data.db.AppDatabase.getInstance(context)
                    val count = db.transactionDao().getCount()
                    if (count == 0) {
                        backupFileToRestore = backupFiles.first().name
                        showRestoreDialog = true
                    }
                }
                prefs.edit().putBoolean("has_shown_restore_prompt", true).apply()
            }
        }
    }

    // 权限状态（每次 resume 时刷新）
    var notificationListenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showPermissionWarning by remember { mutableStateOf(false) }

    // 监听生命周期，每次回到前台时检查权限
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                val newNotification = isNotificationListenerEnabled(context)
                val newOverlay = Settings.canDrawOverlays(context)
                notificationListenerEnabled = newNotification
                canDrawOverlays = newOverlay
                // 权限失效时显示警告
                if (!newNotification) {
                    showPermissionWarning = true
                }
            }
        })
    }

    // 首次启动且权限未开启，跳转到权限引导
    LaunchedEffect(isFirstLaunch) {
        if (isFirstLaunch && (!notificationListenerEnabled || !canDrawOverlays)) {
            navController.navigate("permission") {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
    }

    // 通知深链：读取 openTransactionId
    val activity = context as? ComponentActivity
    val openTransactionId = activity?.intent?.getLongExtra("openTransactionId", -1) ?: -1
    LaunchedEffect(openTransactionId) {
        if (openTransactionId > 0) {
            // 清除 extra 避免重复触发
            activity?.intent?.removeExtra("openTransactionId")
            // 导航到首页（交易记录在首页可见）
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
    }

    // CSV导出状态
    var showExportDialog by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<Uri?>(null) }

    Scaffold(
        containerColor = SmartLedgerColors.bg,
        bottomBar = {
            if (currentRoute != "permission" && currentRoute != "search") {
                NavigationBar(
                    containerColor = SmartLedgerColors.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SmartLedgerColors.navSelected,
                                selectedTextColor = SmartLedgerColors.navSelected,
                                unselectedIconColor = SmartLedgerColors.navUnselected,
                                unselectedTextColor = SmartLedgerColors.navUnselected,
                                indicatorColor = SmartLedgerColors.accentDim
                            ),
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToRecord = {
                        navController.navigate(Screen.Record.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    },
                    onExport = {
                        scope.launch {
                            val uri = CsvExporter.export(context)
                            if (uri != null) {
                                exportResult = uri
                                showExportDialog = true
                            }
                        }
                    }
                )
            }
            composable(Screen.Record.route) {
                RecordScreen(
                    onSaved = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Statistics.route) {
                StatisticsScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToBudget = {
                        navController.navigate("budget")
                    },
                    onNavigateToCategory = {
                        navController.navigate("category")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onNavigateToBackup = {
                        navController.navigate("backup")
                    },
                    onExport = {
                        navController.navigate("export")
                    }
                )
            }
            composable("budget") {
                com.smartledger.ui.budget.BudgetScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("category") {
                com.smartledger.ui.category.CategoryManageScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("export") {
                com.smartledger.ui.export.ExportScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("backup") {
                com.smartledger.ui.backup.BackupScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                com.smartledger.ui.settings.SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToFeedback = { navController.navigate("feedback") },
                    onNavigateToPrivacy = { navController.navigate("privacy") }
                )
            }
            composable("feedback") {
                com.smartledger.ui.settings.FeedbackScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("privacy") {
                com.smartledger.ui.settings.PrivacyPolicyScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("permission") {
                com.smartledger.ui.permission.PermissionGuideScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onComplete = {
                        // 标记首次启动完成
                        context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)
                            .edit().putBoolean("first_launch", false).apply()
                        isFirstLaunch = false
                        navController.navigate(Screen.Home.route) {
                            popUpTo("permission") { inclusive = true }
                        }
                    }
                )
            }
        }
    }

    // CSV导出成功弹窗
    if (showExportDialog && exportResult != null) {
        SmartLedgerDialog(
            onDismissRequest = { showExportDialog = false },
            iconTint = SmartLedgerColors.income,
            title = "导出成功",
            text = "记账数据已导出到 Documents/SmartLedger/ 目录",
            confirmText = "打开文件",
            onConfirm = {
                exportResult?.let { uri ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "text/csv")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开文件", Toast.LENGTH_SHORT).show()
                    }
                }
                showExportDialog = false
            },
            dismissText = "确定",
            onDismiss = { showExportDialog = false }
        )
    }

    // ═══ 权限失效警告弹窗 ═══
    if (showPermissionWarning) {
        SmartLedgerDialog(
            onDismissRequest = { showPermissionWarning = false },
            iconTint = SmartLedgerColors.expense,
            title = "通知监听权限已失效",
            text = "重新编译安装后权限会被撤销。\n\n开启权限后才能自动识别微信、支付宝、银行卡的支付通知。",
            confirmText = "去开启",
            onConfirm = {
                showPermissionWarning = false
                navController.navigate("permission")
            },
            dismissText = "稍后再说",
            onDismiss = { showPermissionWarning = false }
        )
    }

    // ═══ 重装恢复备份弹窗 ═══
    if (showRestoreDialog && backupFileToRestore != null) {
        val coroutineScope = rememberCoroutineScope()
        var isRestoring by remember { mutableStateOf(false) }
        SmartLedgerDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = "发现历史备份",
            text = "检测到您之前有备份数据「${backupFileToRestore}」。\n\n是否恢复这些数据？恢复后您的历史账单将重新出现。",
            confirmText = if (isRestoring) "恢复中..." else "恢复数据",
            onConfirm = {
                if (!isRestoring) {
                    isRestoring = true
                    coroutineScope.launch {
                        val count = com.smartledger.util.CsvImporter.restore(context, backupFileToRestore!!)
                        isRestoring = false
                        showRestoreDialog = false
                        if (count > 0) {
                            android.widget.Toast.makeText(context, "已恢复 $count 条记录", android.widget.Toast.LENGTH_SHORT).show()
                            // 触发首页刷新
                            isFirstLaunch = false
                        }
                    }
                }
            },
            dismissText = "暂不恢复",
            onDismiss = { showRestoreDialog = false }
        )
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (flat.isNullOrEmpty()) return false
    return flat.contains("PaymentNotificationListener")
}
