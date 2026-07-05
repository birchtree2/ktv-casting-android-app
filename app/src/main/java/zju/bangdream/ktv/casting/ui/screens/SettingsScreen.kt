package zju.bangdream.ktv.casting.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import zju.bangdream.ktv.casting.R
import zju.bangdream.ktv.casting.update.UpdateChecker
import zju.bangdream.ktv.casting.update.UpdateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    var isIgnoringBattery by remember { mutableStateOf(checkBatteryOptimizations(context)) }
    var isNotificationEnabled by remember { mutableStateOf(checkNotificationPermission(context)) }

    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    // null=未检查, ""=已是最新, 其他=错误信息或"发现新版本"提示
    var updateStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = checkBatteryOptimizations(context)
                isNotificationEnabled = checkNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台运行设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isIgnoringBattery = checkBatteryOptimizations(context)
                        isNotificationEnabled = checkNotificationPermission(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "手动刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "请确保以下状态为“已允许”，以防投屏中途断开。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "1. 通知权限", style = MaterialTheme.typography.titleMedium)
                        Badge(containerColor = if (isNotificationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) {
                            Text(if (isNotificationEnabled) "已允许" else "未允许", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        text = "前台通知是维持后台运行的关键，请务必开启。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { openNotificationSettings(context) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("去设置")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "2. 忽略电池优化", style = MaterialTheme.typography.titleMedium)
                        Badge(containerColor = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) {
                            Text(if (isIgnoringBattery) "已允许" else "未允许", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        text = "允许应用不受到系统用电限制。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(onClick = { openAppDetails(context) }) {
                            Text("应用详情")
                        }
                        Button(onClick = { requestIgnoreBatteryOptimizations(context) }) {
                            Text("去设置")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "查看运行日志", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = onOpenLogs) {
                        Text("打开")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "检查更新", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdate = true
                                    updateStatus = null
                                    try {
                                        val checker = UpdateChecker(context)
                                        val release = checker.fetchLatestRelease()
                                        if (release == null) {
                                            updateStatus = "无法获取版本信息，请检查网络连接"
                                        } else if (checker.shouldUpdate(release)) {
                                            updateStatus = "发现新版本: ${release.tagName}"
                                            UpdateDialog.showUpdateDialog(context, release) {
                                                checker.saveLastCheckTime(release)
                                            }
                                        } else {
                                            updateStatus = "已是最新版本 (${release.tagName})"
                                        }
                                    } catch (e: Exception) {
                                        updateStatus = "检查失败: ${e.message}"
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("检查")
                            }
                        }
                    }
                    updateStatus?.let { status ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.startsWith("检查失败") || status.startsWith("无法获取"))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "有些安卓设备制造商不遵守后台应用程序的标准行为，根据你的设备品牌，你可能需要执行额外的配置。\n" +
                        "请参阅以下网站，了解有关该问题的更多信息，以及如何提高权限的稳定性：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Don't kill my app")
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { uriHandler.openUri("https://github.com/birchtree2/ktv-casting-android-app") }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github_logo),
                            contentDescription = "GitHub",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val versionName = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (e: Exception) {
                            "1.0.0"
                        }
                    }

                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openNotificationSettings(context: Context) {
    try {
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                else -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        openAppDetails(context)
    }
}

private fun checkBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

private fun openAppDetails(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
