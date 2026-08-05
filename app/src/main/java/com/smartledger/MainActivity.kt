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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : ComponentActivity() {

    /** 通知点击深链：支持 onNewIntent 热启动 */
    var deepLinkTransactionId by mutableStateOf(-1L)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkTransactionId = intent?.getLongExtra("openTransactionId", -1) ?: -1

        // 初始化主题模式
        val prefs = getSharedPreferences("smart_ledger", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", "SYSTEM")
        ThemeManager.init(
            try {
                ThemeMode.valueOf(savedTheme ?: "SYSTEM")
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        )

        // 启动保活 + 若监听已授权但 binder 断开则 requestRebind
        try {
            com.smartledger.service.KeepAliveService.start(this)
            com.smartledger.service.ListenerStatus.requestRebindIfNeeded(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkTransactionId = intent.getLongExtra("openTransactionId", -1)
    }

    fun clearDeepLink() {
        deepLinkTransactionId = -1L
        intent?.removeExtra("openTransactionId")
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

    // 重装恢复：完成引导进入首页后，扫描「下载/SmartLedger」等持久目录
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupFileToRestore by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isFirstLaunch) {
        // 等权限引导结束后再提示，避免被挡住
        if (isFirstLaunch) return@LaunchedEffect
        val prefs = context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)
        if (prefs.getBoolean("has_shown_restore_prompt", false)) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val latest = com.smartledger.util.BackupStorage.findLatest(context)
            val db = com.smartledger.data.db.AppDatabase.getInstance(context)
            val count = db.transactionDao().getCount()
            if (latest != null && count == 0) {
                backupFileToRestore = latest.fileName
                showRestoreDialog = true
            }
        }
        prefs.edit().putBoolean("has_shown_restore_prompt", true).apply()
    }

    // 权限状态（每次 resume 时刷新）
    var notificationListenerEnabled by remember {
        mutableStateOf(com.smartledger.service.ListenerStatus.isEnabledInSettings(context))
    }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showPermissionWarning by remember { mutableStateOf(false) }
    var showUpdateNlsPrompt by remember {
        mutableStateOf(com.smartledger.service.ListenerStatus.shouldPromptAfterUpdate(context))
    }
    var showReconnectFailed by remember { mutableStateOf(false) }
    var permissionWarnedThisSession by remember { mutableStateOf(false) }
    // 触发一次「静默重绑后复查」
    var pendingReconnectCheck by remember { mutableStateOf(false) }

    // 监听生命周期，每次回到前台时检查权限并尝试重绑
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                val enabled = com.smartledger.service.ListenerStatus.isEnabledInSettings(context)
                val connected = com.smartledger.service.ListenerStatus.isConnected(context)
                val newOverlay = Settings.canDrawOverlays(context)
                notificationListenerEnabled = enabled
                canDrawOverlays = newOverlay
                // 后台巡检留下的标记：打开软件再弹应用内提示（不发系统通知）
                val pendingPrompt =
                    com.smartledger.service.ListenerStatus.consumePendingInAppPrompt(context)

                if (!enabled) {
                    pendingReconnectCheck = false
                    if (com.smartledger.service.ListenerStatus.shouldPromptAfterUpdate(context)) {
                        // 覆盖安装后系统强制撤销：必须提示重新开启（仅此一次）
                        showUpdateNlsPrompt = true
                    } else if (!isFirstLaunch &&
                        (!permissionWarnedThisSession ||
                            pendingPrompt == com.smartledger.service.ListenerStatus.PROMPT_DISABLED)
                    ) {
                        showPermissionWarning = true
                        permissionWarnedThisSession = true
                    }
                } else {
                    com.smartledger.service.ListenerStatus.clearAfterUpdatePrompt(context)
                    showUpdateNlsPrompt = false
                    if (!connected ||
                        pendingPrompt == com.smartledger.service.ListenerStatus.PROMPT_RECONNECT
                    ) {
                        // 设置仍开着但 binder 断了：静默重绑，仍失败再弹窗
                        com.smartledger.service.ListenerStatus.requestRebind(context, force = true)
                        com.smartledger.service.KeepAliveService.start(context)
                        pendingReconnectCheck = true
                    } else {
                        pendingReconnectCheck = false
                    }
                }
            }
        })
    }

    // 静默重绑后复查：连上则不打扰；仍断则引导「关掉再开」通知使用权
    LaunchedEffect(pendingReconnectCheck) {
        if (!pendingReconnectCheck) return@LaunchedEffect
        kotlinx.coroutines.delay(3000)
        pendingReconnectCheck = false
        if (!com.smartledger.service.ListenerStatus.isEnabledInSettings(context)) {
            showPermissionWarning = true
        } else if (!com.smartledger.service.ListenerStatus.isConnected(context)) {
            com.smartledger.service.ListenerStatus.requestRebindIfNeeded(context)
            kotlinx.coroutines.delay(2000)
            if (!com.smartledger.service.ListenerStatus.isConnected(context)) {
                showReconnectFailed = true
            }
        }
    }

    // 首次启动且权限未开启，跳转到权限引导
    LaunchedEffect(isFirstLaunch) {
        // 首次启动进入权限引导；悬浮窗非必须，避免卡死在引导页
        if (isFirstLaunch) {
            navController.navigate("permission") {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
    }

    // ═══ 自动检查更新（启动后检查；失败可重试；「稍后再说」仅跳过当前版本）═══
    var updateInfo by remember { mutableStateOf<com.smartledger.util.UpdateChecker.UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var pendingInstallFile by remember { mutableStateOf<java.io.File?>(null) }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }
    val updateScope = rememberCoroutineScope()
    fun dismissUpdateDialog(info: com.smartledger.util.UpdateChecker.UpdateInfo) {
        context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)
            .edit()
            .putString("dismissed_update_version", info.versionName)
            .apply()
        updateInfo = null
    }
    fun startApkDownload(info: com.smartledger.util.UpdateChecker.UpdateInfo) {
        updateDownloadError = null
        isDownloadingUpdate = true
        downloadProgress =
            com.smartledger.util.UpdateChecker.partialDownloadPercent(context, info)
        updateScope.launch {
            when (val result =
                com.smartledger.util.UpdateChecker.downloadApk(context, info) { p ->
                    downloadProgress = p
                }
            ) {
                is com.smartledger.util.UpdateChecker.DownloadResult.Success -> {
                    isDownloadingUpdate = false
                    updateInfo = null
                    val ok = com.smartledger.util.UpdateChecker.installApk(
                        context, result.apkFile
                    )
                    if (!ok) {
                        pendingInstallFile = result.apkFile
                        Toast.makeText(
                            context,
                            "请允许安装未知应用后点「继续安装」",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "请按提示完成安装",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is com.smartledger.util.UpdateChecker.DownloadResult.NeedBrowser -> {
                    isDownloadingUpdate = false
                    com.smartledger.util.UpdateChecker.openDownloadPage(context, info)
                    updateInfo = null
                }
                is com.smartledger.util.UpdateChecker.DownloadResult.Failed -> {
                    isDownloadingUpdate = false
                    updateDownloadError = result.message
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        // 稍等首屏渲染，避免与权限引导抢弹窗
        delay(1200)
        val prefs = context.getSharedPreferences("smart_ledger", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_update_check", 0L)
        val now = System.currentTimeMillis()
        // 成功检查后 30 分钟内不重复请求；失败不写时间戳，下次启动可重试
        if (now - lastCheck < 30 * 60 * 1000L) return@LaunchedEffect

        when (val result = com.smartledger.util.UpdateChecker.checkUpdate(context)) {
            is com.smartledger.util.UpdateChecker.CheckResult.HasUpdate -> {
                prefs.edit().putLong("last_update_check", now).apply()
                val dismissed = prefs.getString("dismissed_update_version", null)
                if (dismissed != result.info.versionName) {
                    updateInfo = result.info
                }
            }
            is com.smartledger.util.UpdateChecker.CheckResult.UpToDate -> {
                prefs.edit()
                    .putLong("last_update_check", now)
                    .remove("dismissed_update_version")
                    .apply()
            }
            is com.smartledger.util.UpdateChecker.CheckResult.Failed -> {
                // 不更新 last_update_check，下次冷启动继续试
                Log.w("SmartLedger", "自动检查更新失败: ${result.message}")
            }
        }
    }

    // 通知深链：冷启动 + 热启动（onNewIntent）均可
    val activity = context as? MainActivity
    val openTransactionId = activity?.deepLinkTransactionId ?: -1L
    LaunchedEffect(openTransactionId) {
        if (openTransactionId > 0) {
            activity?.clearDeepLink()
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

    // ═══ 覆盖安装后：系统强制撤销通知使用权（无法自动恢复）═══
    if (showUpdateNlsPrompt && !isFirstLaunch) {
        SmartLedgerDialog(
            onDismissRequest = {
                showUpdateNlsPrompt = false
                com.smartledger.service.ListenerStatus.clearAfterUpdatePrompt(context)
            },
            iconTint = SmartLedgerColors.accent,
            title = "更新后需重新开启通知权限",
            text = "这是 Android 系统安全限制：每次安装或更新应用后，都会自动关闭通知使用权，应用无法替你静默打开。\n\n日常使用中若监听断开，应用会自动尝试重连；仅更新后需要你重新开启一次。",
            confirmText = "去开启",
            onConfirm = {
                showUpdateNlsPrompt = false
                com.smartledger.service.ListenerStatus.clearAfterUpdatePrompt(context)
                navController.navigate("permission")
            },
            dismissText = "稍后再说",
            onDismiss = {
                showUpdateNlsPrompt = false
                com.smartledger.service.ListenerStatus.clearAfterUpdatePrompt(context)
            }
        )
    }

    // ═══ 权限未开启（非更新场景）═══
    if (showPermissionWarning && !showUpdateNlsPrompt) {
        SmartLedgerDialog(
            onDismissRequest = { showPermissionWarning = false },
            iconTint = SmartLedgerColors.expense,
            title = "通知使用权未开启",
            text = "开启后才能自动识别微信、支付宝、银行卡的支付通知。\n\n若设置里已开启但仍不记账，请关掉再打开一次「智能记账」的通知使用权。",
            confirmText = "去开启",
            onConfirm = {
                showPermissionWarning = false
                navController.navigate("permission")
            },
            dismissText = "稍后再说",
            onDismiss = { showPermissionWarning = false }
        )
    }

    // ═══ 监听断连且自动恢复失败 ═══
    if (showReconnectFailed && !showPermissionWarning && !showUpdateNlsPrompt) {
        SmartLedgerDialog(
            onDismissRequest = { showReconnectFailed = false },
            iconTint = SmartLedgerColors.expense,
            title = "支付监听连接已断开",
            text = "通知权限仍在，但系统已断开监听连接（省电杀后台后常见）。应用已自动尝试重连仍未成功。\n\n请到「通知使用权」里关掉再打开本应用，即可恢复。",
            confirmText = "去修复",
            onConfirm = {
                showReconnectFailed = false
                navController.navigate("permission")
            },
            dismissText = "稍后再说",
            onDismiss = { showReconnectFailed = false }
        )
    }

    // ═══ 检查更新弹窗 ═══
    updateInfo?.let { info ->
        if (!isDownloadingUpdate && updateDownloadError == null) {
            SmartLedgerDialog(
                onDismissRequest = { dismissUpdateDialog(info) },
                iconTint = SmartLedgerColors.accent,
                title = "发现新版本 ${info.versionName}",
                text = if (info.releaseNotes.isNotBlank()) {
                    val notes = info.releaseNotes.take(500)
                    if (info.releaseNotes.length > 500) "$notes\n..." else notes
                } else "有新版本可用，建议更新。",
                confirmText = if (info.apkUrl.isNullOrBlank()) "前往下载页" else "应用内下载",
                onConfirm = {
                    if (info.apkUrl.isNullOrBlank()) {
                        com.smartledger.util.UpdateChecker.openDownloadPage(context, info)
                        dismissUpdateDialog(info)
                    } else {
                        startApkDownload(info)
                    }
                },
                dismissText = "稍后再说",
                onDismiss = { dismissUpdateDialog(info) }
            )
        }
    }

    if (isDownloadingUpdate) {
        SmartLedgerDialog(
            onDismissRequest = { },
            iconTint = SmartLedgerColors.accent,
            title = "正在下载更新",
            text = "已下载 $downloadProgress%，请保持网络畅通…",
            confirmText = null,
            dismissText = "",
            content = {
                LinearProgressIndicator(
                    progress = downloadProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = SmartLedgerColors.accent,
                    trackColor = SmartLedgerColors.border
                )
            }
        )
    }

    pendingInstallFile?.let { apk ->
        SmartLedgerDialog(
            onDismissRequest = { pendingInstallFile = null },
            iconTint = SmartLedgerColors.accent,
            title = "安装更新",
            text = "安装包已下载完成。若已允许安装未知应用，请点击继续安装。",
            confirmText = "继续安装",
            onConfirm = {
                val ok = com.smartledger.util.UpdateChecker.installApk(context, apk)
                if (ok) {
                    pendingInstallFile = null
                    Toast.makeText(context, "请按提示完成安装", Toast.LENGTH_SHORT).show()
                } else {
                    updateDownloadError = "仍未获得安装权限，请在系统设置中允许本应用安装未知应用。"
                }
            },
            dismissText = "取消",
            onDismiss = { pendingInstallFile = null }
        )
    }

    updateDownloadError?.let { msg ->
        val retryInfo = updateInfo?.takeIf { !it.apkUrl.isNullOrBlank() }
        val cachedPct = retryInfo?.let {
            com.smartledger.util.UpdateChecker.partialDownloadPercent(context, it)
        } ?: 0
        SmartLedgerDialog(
            onDismissRequest = { updateDownloadError = null },
            iconTint = SmartLedgerColors.expense,
            title = if (retryInfo != null) "下载失败" else "更新提示",
            text = if (retryInfo != null && cachedPct > 0) {
                "$msg\n\n已下载 ${cachedPct}%，重新下载将从断点继续。"
            } else {
                msg
            },
            confirmText = if (retryInfo != null) "重新下载" else "好的",
            onConfirm = {
                if (retryInfo != null) {
                    startApkDownload(retryInfo)
                } else {
                    updateDownloadError = null
                }
            },
            dismissText = if (retryInfo != null) "取消" else "",
            onDismiss = { updateDownloadError = null }
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
    return com.smartledger.service.ListenerStatus.isEnabledInSettings(context)
}
