package zju.bangdream.ktv.casting

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import zju.bangdream.ktv.casting.ui.screens.BilibiliSetupScreen
import zju.bangdream.ktv.casting.ui.screens.CastingControlScreen
import zju.bangdream.ktv.casting.ui.screens.DeviceSelectorScreen
import zju.bangdream.ktv.casting.ui.screens.LogScreen
import zju.bangdream.ktv.casting.ui.screens.SettingsScreen
import zju.bangdream.ktv.casting.ui.theme.KtvCastingTheme
import zju.bangdream.ktv.casting.update.ApkDownloader
import zju.bangdream.ktv.casting.update.UpdateChecker
import zju.bangdream.ktv.casting.update.UpdateDialog

class MainActivity : ComponentActivity() {

    companion object {
        private var updateCheckedThisSession = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemRequirements()
        RustEngine.initLogging(2)
        RustEngine.initSessionDir(filesDir.absolutePath)
        RustEngine.logFromKotlin("MainActivity", "应用启动", LogLevel.INFO)

        // 恢复上次保存的 B 站 Session（如果有）
        val prefs = getSharedPreferences("ktv_settings", MODE_PRIVATE)
        val savedSession = prefs.getString("bilibili_session", null)
        if (!savedSession.isNullOrEmpty()) {
            val ok = RustEngine.restoreBilibiliSession(savedSession)
            RustEngine.logFromKotlin("MainActivity", "B站会话恢复: $ok", LogLevel.INFO)
        }

        // 检查应用更新
        checkForAppUpdate()

        setContent {
            KtvCastingTheme {
                // 导航状态
                // "main" | "settings" | "logs" | "bilibili_setup"
                var currentScreen by rememberSaveable { mutableStateOf("main") }

                // 投屏会话状态（DLNA 或 Bilibili 二选一）
                var castDeviceName by rememberSaveable { mutableStateOf("") }
                var castRoomId by rememberSaveable { mutableLongStateOf(0L) }
                // 用于 bilibili 临时存储 base_url 和 room_id，在进入 BilibiliSetupScreen 之前赋值
                var pendingBaseUrl by rememberSaveable { mutableStateOf("") }
                var pendingRoomId by rememberSaveable { mutableStateOf("") }

                Surface(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "settings" -> SettingsScreen(
                            onBack = { currentScreen = "main" },
                            onOpenLogs = { currentScreen = "logs" }
                        )
                        "logs" -> LogScreen(onBack = { currentScreen = "settings" })
                        "bilibili_setup" -> BilibiliSetupScreen(
                            onDeviceSelected = { buvid, name ->
                                castDeviceName = name
                                castRoomId = pendingRoomId.toLongOrNull() ?: 0L
                                startBilibiliCastingService(pendingBaseUrl, pendingRoomId, buvid, name)
                                currentScreen = "main"
                            },
                            onBack = { currentScreen = "main" }
                        )
                        else -> Box {
                            if (castDeviceName.isEmpty()) {
                                DeviceSelectorScreen(
                                    onDeviceSelect = { url, room, device ->
                                        castDeviceName = device.name
                                        castRoomId = room
                                        startDlnaCastingService(url, room, device)
                                    },
                                    onBilibiliMode = { url, roomId ->
                                        pendingBaseUrl = url
                                        pendingRoomId = roomId
                                        currentScreen = "bilibili_setup"
                                    }
                                )
                            } else {
                                CastingControlScreen(
                                    deviceName = castDeviceName,
                                    roomId = castRoomId,
                                    baseUrl = getSharedPreferences("ktv_settings", MODE_PRIVATE).getString("base_url", "") ?: "",
                                    onReset = {
                                        stopService(Intent(this@MainActivity, CastingService::class.java))
                                        RustEngine.resetEngine()
                                        castDeviceName = ""
                                        castRoomId = 0L
                                    }
                                )
                            }

                            if (castDeviceName.isEmpty()) {
                                IconButton(
                                    onClick = { currentScreen = "settings" },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "设置")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupSystemRequirements() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        wifiManager.createMulticastLock("ktv_search_lock").acquire()
    }

    private fun checkForAppUpdate() {
        if (updateCheckedThisSession) return
        updateCheckedThisSession = true
        lifecycleScope.launch {
            try {
                RustEngine.logFromKotlin("MainActivity", "开始检查应用更新...", LogLevel.DEBUG)

                val updateChecker = UpdateChecker(this@MainActivity)
                val releaseInfo = updateChecker.fetchLatestRelease()

                if (releaseInfo != null) {
                    RustEngine.logFromKotlin(
                        "MainActivity",
                        "获取到最新版本: ${releaseInfo.tagName}",
                        LogLevel.DEBUG
                    )
                    if (updateChecker.shouldUpdate(releaseInfo)) {
                        RustEngine.logFromKotlin(
                            "MainActivity",
                            "检测到新版本，显示更新对话框",
                            LogLevel.INFO
                        )
                        UpdateDialog.showUpdateDialog(this@MainActivity, releaseInfo) {
                            try {
                                val downloader = ApkDownloader(this@MainActivity)
                                downloader.downloadAndInstall(releaseInfo)
                                // 用户确认更新后保存，下次启动不再提示同一版本
                                updateChecker.saveLastCheckTime(releaseInfo)
                                Toast.makeText(
                                    this@MainActivity,
                                    "后台下载中，请稍候...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                RustEngine.logFromKotlin(
                                    "MainActivity",
                                    "启动更新失败: ${e.message}",
                                    LogLevel.ERROR
                                )
                            }
                        }
                    }
                } else {
                    RustEngine.logFromKotlin(
                        "MainActivity",
                        "无法获取最新版本信息",
                        LogLevel.DEBUG
                    )
                }
            } catch (e: Exception) {
                RustEngine.logFromKotlin(
                    "MainActivity",
                    "检查更新异常: ${e.message} ${e.stackTrace.joinToString("\n")}",
                    LogLevel.ERROR
                )
            }
        }
    }

    private fun startDlnaCastingService(url: String, room: Long, device: DlnaDeviceItem) {
        RustEngine.logFromKotlin("Casting", "启动 DLNA 投屏: $url, room=$room, device=${device.name}")
        val intent = Intent(this, CastingService::class.java).apply {
            putExtra("mode", "dlna")
            putExtra("base_url", url)
            putExtra("room_id", room)
            putExtra("location", device.location)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun startBilibiliCastingService(url: String, roomId: String, buvid: String, deviceName: String) {
        RustEngine.logFromKotlin("Casting", "启动B站云投屏: $url, room=$roomId, device=$deviceName")
        val intent = Intent(this, CastingService::class.java).apply {
            putExtra("mode", "bilibili")
            putExtra("base_url", url)
            putExtra("room_id", roomId.toLongOrNull() ?: 0L)
            putExtra("buvid", buvid)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }
}
