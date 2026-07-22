package zju.bangdream.ktv.casting

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
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
            if (ok == 0) {
                prefs.edit().apply {
                    remove("bilibili_session")
                    remove("last_bilibili_device")
                    remove("last_bilibili_buvid")
                    apply()
                }
                RustEngine.logFromKotlin("MainActivity", "已清理无效的 B 站本地会话", LogLevel.WARN)
            }
        }

        // 检查应用更新
        checkForAppUpdate()

        setContent {
            KtvCastingTheme {
                // DLNA 设备状态（需要 Saver 序列化）
                val deviceSaver = remember {
                    Saver<DlnaDeviceItem?, Map<String, String>>(
                        save = { device ->
                            if (device == null) emptyMap()
                            else mapOf("name" to device.name, "location" to device.location)
                        },
                        restore = { data ->
                            val name = data["name"]
                            val location = data["location"]
                            if (name != null && location != null) DlnaDeviceItem(name, location)
                            else null
                        }
                    )
                }

                var selectedDevice by rememberSaveable(stateSaver = deviceSaver) {
                    mutableStateOf<DlnaDeviceItem?>(null)
                }
                var selectedRoomId by rememberSaveable { mutableLongStateOf(0L) }
                var selectedBaseUrl by rememberSaveable { mutableStateOf("") }
                var castMode by rememberSaveable { mutableStateOf("dlna") }

                // B站临时状态（进入 BilibiliSetupScreen 前存储）
                var pendingBaseUrl by rememberSaveable { mutableStateOf("") }
                var pendingRoomId by rememberSaveable { mutableStateOf("") }

                // 覆盖层状态
                var showLogs by rememberSaveable { mutableStateOf(false) }
                var showBilibiliSetup by rememberSaveable { mutableStateOf(false) }

                // PagerState 管理 3 个页面
                val pagerState = rememberPagerState(pageCount = { 3 })
                val scope = rememberCoroutineScope()

                val prefs = remember { getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showBilibiliSetup) {
                        // B站登录页：全屏独立页面，不显示底部导航栏
                        BackHandler { showBilibiliSetup = false }
                        BilibiliSetupScreen(
                            onDeviceSelected = { buvid, name ->
                                showBilibiliSetup = false
                                if (selectedDevice != null || castMode == "dlna") {
                                    stopCasting()
                                }
                                selectedRoomId = pendingRoomId.toLongOrNull() ?: 0L
                                selectedBaseUrl = pendingBaseUrl
                                castMode = "bilibili"
                                prefs.edit().apply {
                                    putString("last_bilibili_device", name)
                                    putString("last_bilibili_buvid", buvid)
                                    apply()
                                }
                                startBilibiliCastingService(
                                    pendingBaseUrl,
                                    pendingRoomId,
                                    buvid,
                                    name
                                )
                                selectedDevice = null
                                scope.launch { pagerState.scrollToPage(1) }
                            },
                            onBack = { showBilibiliSetup = false }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 底层主界面 Scaffold 始终在 Composition 中，避免状态丢失
                            Scaffold(
                                bottomBar = {
                                    NavigationBar {
                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    Icons.Default.Search,
                                                    contentDescription = "连接"
                                                )
                                            },
                                            label = { Text("连接") },
                                            selected = pagerState.currentPage == 0,
                                            onClick = { scope.launch { pagerState.scrollToPage(0) } }
                                        )
                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "控制"
                                                )
                                            },
                                            label = { Text("控制") },
                                            selected = pagerState.currentPage == 1,
                                            onClick = { scope.launch { pagerState.scrollToPage(1) } }
                                        )
                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    Icons.Default.Settings,
                                                    contentDescription = "设置"
                                                )
                                            },
                                            label = { Text("设置") },
                                            selected = pagerState.currentPage == 2,
                                            onClick = { scope.launch { pagerState.scrollToPage(2) } }
                                        )
                                    }
                                }
                            ) { padding ->
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.padding(padding),
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 2
                                ) { pageIndex ->
                                    when (pageIndex) {
                                        0 -> DeviceSelectorScreen(
                                            onDeviceSelect = { url, room, device ->
                                                // 如果当前已经有连接，先停止旧连接
                                                if (selectedDevice != null || castMode == "bilibili") {
                                                    stopCasting()
                                                }
                                                selectedDevice = device
                                                selectedRoomId = room
                                                selectedBaseUrl = url
                                                castMode = "dlna"
                                                startDlnaCastingService(url, room, device)
                                                scope.launch { pagerState.animateScrollToPage(1) }
                                            },
                                            onBilibiliMode = { url, roomId ->
                                                pendingBaseUrl = url
                                                pendingRoomId = roomId
                                                showBilibiliSetup = true
                                            }
                                        )

                                        1 -> {
                                            val isConnected =
                                                selectedDevice != null || castMode == "bilibili"
                                            if (isConnected) {
                                                val deviceName =
                                                    if (selectedDevice != null) selectedDevice!!.name
                                                    else prefs.getString("last_bilibili_device", "")
                                                        ?: ""
                                                CastingControlScreen(
                                                    deviceName = deviceName,
                                                    roomId = selectedRoomId,
                                                    baseUrl = selectedBaseUrl.ifEmpty {
                                                        prefs.getString("base_url", "") ?: ""
                                                    },
                                                    onReset = {
                                                        stopCasting()
                                                        selectedDevice = null
                                                        selectedRoomId = 0L
                                                        selectedBaseUrl = ""
                                                        castMode = "dlna"
                                                    },
                                                    onChangeSettings = { newUrl, newRoomId ->
                                                        stopCasting()
                                                        selectedBaseUrl = newUrl
                                                        selectedRoomId = newRoomId
                                                        prefs.edit().apply {
                                                            putString("base_url", newUrl)
                                                            putString(
                                                                "room_id",
                                                                newRoomId.toString()
                                                            )
                                                            apply()
                                                        }
                                                        if (castMode == "bilibili") {
                                                            // B站模式：用新参数重启B站投屏
                                                            val buvid = prefs.getString(
                                                                "last_bilibili_buvid",
                                                                ""
                                                            ) ?: ""
                                                            startBilibiliCastingService(
                                                                newUrl, newRoomId.toString(), buvid,
                                                                prefs.getString(
                                                                    "last_bilibili_device",
                                                                    ""
                                                                ) ?: ""
                                                            )
                                                        } else {
                                                            // DLNA 模式：用新参数重启 DLNA 投屏
                                                            selectedDevice?.let {
                                                                startDlnaCastingService(
                                                                    newUrl,
                                                                    newRoomId,
                                                                    it
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onChangeDevice = { newDevice ->
                                                        stopCasting()
                                                        selectedDevice = newDevice
                                                        castMode = "dlna"
                                                        startDlnaCastingService(
                                                            selectedBaseUrl,
                                                            selectedRoomId,
                                                            newDevice
                                                        )
                                                    },
                                                    onChangeToBilibiliDevice = { buvid, name ->
                                                        stopCasting()
                                                        selectedDevice = null
                                                        castMode = "bilibili"
                                                        prefs.edit().apply {
                                                            putString("last_bilibili_device", name)
                                                            putString("last_bilibili_buvid", buvid)
                                                            apply()
                                                        }
                                                        val roomStr = selectedRoomId.toString()
                                                        startBilibiliCastingService(
                                                            selectedBaseUrl,
                                                            roomStr,
                                                            buvid,
                                                            name
                                                        )
                                                    }
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "请先在\"连接\"页面选择投屏设备",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                            }
                                        }

                                        2 -> SettingsScreen(
                                            onBack = {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(
                                                        0
                                                    )
                                                }
                                            },
                                            onOpenLogs = { showLogs = true }
                                        )
                                    }
                                }
                            }

                            // 日志页作为覆盖层
                            if (showLogs) {
                                BackHandler { showLogs = false }
                                LogScreen(onBack = { showLogs = false })
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

    private fun stopCasting() {
        stopService(Intent(this, CastingService::class.java))
        RustEngine.resetEngine()
        CastingService.resetProgress()
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
        RustEngine.logFromKotlin(
            "Casting",
            "启动 DLNA 投屏: $url, room=$room, device=${device.name}"
        )
        val intent = Intent(this, CastingService::class.java).apply {
            putExtra("mode", "dlna")
            putExtra("base_url", url)
            putExtra("room_id", room)
            putExtra("location", device.location)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun startBilibiliCastingService(
        url: String,
        roomId: String,
        buvid: String,
        deviceName: String
    ) {
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
