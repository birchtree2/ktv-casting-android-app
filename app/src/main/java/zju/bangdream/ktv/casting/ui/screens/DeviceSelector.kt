package zju.bangdream.ktv.casting.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zju.bangdream.ktv.casting.DlnaDeviceItem
import zju.bangdream.ktv.casting.EnsureRoomResult
import zju.bangdream.ktv.casting.RoomApi
import zju.bangdream.ktv.casting.RoomEntryMode
import zju.bangdream.ktv.casting.RoomExistenceResult
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

private sealed interface RoomCheckState {
    data object Idle : RoomCheckState
    data object Checking : RoomCheckState
    data object Exists : RoomCheckState
    data object Available : RoomCheckState
    data class Error(val message: String) : RoomCheckState
}

private fun normalizeDeviceUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return "http://$trimmed"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectorScreen(
    onDeviceSelect: (String, Long, DlnaDeviceItem) -> Unit,
    onBilibiliMode: (baseUrl: String, roomId: String) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember { context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }

    var baseUrl by remember {
        mutableStateOf(
            prefs.getString(
                "base_url",
                "https://ktv.starfreedomx.top"
            ) ?: ""
        )
    }
    var roomIdStr by remember { mutableStateOf(prefs.getString("room_id", "1111") ?: "") }
    var roomEntryMode by remember { mutableStateOf(RoomEntryMode.CREATE) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isPreparingRoom by remember { mutableStateOf(false) }
    var roomCheckState by remember { mutableStateOf<RoomCheckState>(RoomCheckState.Idle) }
    var roomCheckGeneration by remember { mutableIntStateOf(0) }

    // DLNA 搜索状态
    var dlnaShowManualInput by remember { mutableStateOf(false) }
    var deviceList by remember { mutableStateOf(emptyArray<DlnaDeviceItem>()) }
    var isSearching by remember { mutableStateOf(false) }
    var isDirectConnecting by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var directIp by remember { mutableStateOf(prefs.getString("direct_ip", "") ?: "") }

    val saveSettings = {
        prefs.edit().apply {
            putString("base_url", baseUrl)
            putString("room_id", roomIdStr)
            putString("direct_ip", directIp)
            apply()
        }
    }

    fun validateInputs(): Boolean {
        if (baseUrl.isBlank()) {
            inputError = "请填写服务器网址"; return false
        }
        if (roomIdStr.isBlank()) {
            inputError = "请填写房间号"; return false
        }
        if (roomIdStr.trim().toLongOrNull() == null) {
            inputError = "房间号必须是有效数字"; return false
        }
        inputError = null
        return true
    }

    fun prepareRoom(onReady: (baseUrl: String, roomId: String) -> Unit) {
        if (!validateInputs() || isPreparingRoom) return
        val readinessError = when {
            roomCheckState == RoomCheckState.Checking || roomCheckState == RoomCheckState.Idle ->
                "请等待房间号检查完成"
            roomEntryMode == RoomEntryMode.CREATE && roomCheckState == RoomCheckState.Exists ->
                "房间号已被占用，请更换房间号或选择加入房间"
            roomEntryMode == RoomEntryMode.JOIN && roomCheckState == RoomCheckState.Available ->
                "房间不存在，请先创建房间"
            roomCheckState is RoomCheckState.Error ->
                (roomCheckState as RoomCheckState.Error).message
            else -> null
        }
        if (readinessError != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(readinessError) }
            return
        }
        val requestedBaseUrl = baseUrl.trim()
        val requestedRoomId = roomIdStr.trim().toLong().toString()
        val requestedMode = roomEntryMode
        baseUrl = requestedBaseUrl
        roomIdStr = requestedRoomId
        saveSettings()
        inputError = null
        isPreparingRoom = true
        coroutineScope.launch {
            try {
                when (
                    val result = RoomApi.enterRoom(
                        requestedBaseUrl,
                        requestedRoomId,
                        requestedMode
                    )
                ) {
                    EnsureRoomResult.Success -> onReady(requestedBaseUrl, requestedRoomId)
                    is EnsureRoomResult.Failure -> {
                        inputError = result.message
                        roomCheckGeneration++
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            } finally {
                isPreparingRoom = false
            }
        }
    }

    LaunchedEffect(baseUrl, roomIdStr, roomCheckGeneration) {
        roomCheckState = RoomCheckState.Idle
        val requestedBaseUrl = baseUrl.trim()
        val requestedRoomId = roomIdStr.trim().toLongOrNull()?.toString() ?: return@LaunchedEffect
        if (requestedBaseUrl.isEmpty()) return@LaunchedEffect

        delay(800)
        roomCheckState = RoomCheckState.Checking
        roomCheckState = when (val result = RoomApi.checkRoom(requestedBaseUrl, requestedRoomId)) {
            RoomExistenceResult.Exists -> RoomCheckState.Exists
            RoomExistenceResult.Available -> RoomCheckState.Available
            is RoomExistenceResult.Failure -> RoomCheckState.Error(result.message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("连接设备") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("KTV 投屏助手", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "填写房间信息后选择投屏方式", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { uriHandler.openUri("https://jcntv1iqoo5s.feishu.cn/wiki/Ytt1wNh88i6E9jkEndhcxNmYnBd") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("使用帮助")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ── 房间信息 ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; inputError = null },
                label = { Text("服务器网址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPreparingRoom,
                isError = inputError != null && baseUrl.isBlank()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = roomIdStr,
                onValueChange = { roomIdStr = it; inputError = null },
                label = { Text("房间号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPreparingRoom,
                isError = inputError != null && roomIdStr.isBlank()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("房间操作", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = roomEntryMode == RoomEntryMode.CREATE,
                    onClick = {
                        roomEntryMode = RoomEntryMode.CREATE
                        inputError = null
                    },
                    label = { Text("创建房间") },
                    enabled = !isPreparingRoom
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = roomEntryMode == RoomEntryMode.JOIN,
                    onClick = {
                        roomEntryMode = RoomEntryMode.JOIN
                        inputError = null
                    },
                    label = { Text("加入房间") },
                    enabled = !isPreparingRoom
                )
            }
            Text(
                text = if (roomEntryMode == RoomEntryMode.CREATE) {
                    "房间号已被占用时不会进入，避免与陌生房间串联"
                } else {
                    "仅加入已有房间；投屏 App 重启后需要重新选择加入房间"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (val state = roomCheckState) {
                RoomCheckState.Idle -> Unit
                RoomCheckState.Checking -> Text(
                    "正在检查房间号…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                RoomCheckState.Exists -> Text(
                    if (roomEntryMode == RoomEntryMode.CREATE) {
                        "该房间号已存在，请更换房间号或选择加入房间"
                    } else {
                        "房间存在，可以加入"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (roomEntryMode == RoomEntryMode.CREATE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                RoomCheckState.Available -> Text(
                    if (roomEntryMode == RoomEntryMode.CREATE) {
                        "房间号可用，可以创建"
                    } else {
                        "房间不存在，请先创建房间"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (roomEntryMode == RoomEntryMode.CREATE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                is RoomCheckState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { roomCheckGeneration++ }) {
                        Text("重试")
                    }
                }
            }
            inputError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isPreparingRoom) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (roomEntryMode == RoomEntryMode.CREATE) "正在创建房间…" else "正在检查房间…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("选择投屏方式", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // ── 模式卡片：DLNA ───────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DLNA 局域网投屏", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "适合 纯K、家庭 WiFi", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "无需登录任何账号，直接搜索同一局域网内支持 DLNA 的设备（电视、智能屏幕等）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 自动搜索按钮
                        Button(
                            onClick = {
                                if (!validateInputs()) return@Button
                                saveSettings()
                                isSearching = true; searchError = null; dlnaShowManualInput =
                                false; deviceList = emptyArray()
                                thread {
                                    val results = RustEngine.searchDevices()
                                    deviceList = results; isSearching = false
                                    if (results.isEmpty()) {
                                        searchError =
                                            "未发现设备，请检查设备是否在线和 WiFi 是否支持多播"
                                        dlnaShowManualInput = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSearching && !isDirectConnecting
                        ) { Text(if (isSearching) "正在搜索..." else "搜索 DLNA 设备") }
                    }
                }
            }

            // 搜索结果或错误提示
            if (searchError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "搜索失败", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            searchError!!, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { dlnaShowManualInput = !dlnaShowManualInput },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text(if (dlnaShowManualInput) "收起" else "手动输入地址") }
                    }
                }
            }

            // 手动输入 IP（默认隐藏，仅在搜索失败时显示）
            AnimatedVisibility(visible = dlnaShowManualInput) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = directIp,
                        onValueChange = { directIp = it },
                        label = { Text("描述文件完整地址") },
                        placeholder = { Text("http://设备IP:端口/路径") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            val preview =
                                if (directIp.isNotBlank()) normalizeDeviceUrl(directIp) else ""
                            if (preview.isNotEmpty()) Text(
                                "将连接：$preview",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "常见默认地址（将目标设备 IP 替换到对应位置）：\n" +
                                "• bilibili 小电视：http://设备IP:9958/bilibili/description.xml\n" +
                                "• Kodi（HTTP）：http://设备IP:1432/\n" +
                                "• Kodi（XML）：http://设备IP:1743/DeviceDescription.xml",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (directIp.isBlank()) return@Button
                            saveSettings(); isDirectConnecting = true; deviceList = emptyArray()
                            val url = normalizeDeviceUrl(directIp)
                            thread {
                                val results = RustEngine.searchDeviceByUrl(url)
                                deviceList = results; isDirectConnecting = false
                                if (results.isEmpty())
                                    searchError = "连接失败，请检查 IP 是否正确，设备是否开启"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSearching && !isDirectConnecting && directIp.isNotBlank()
                    ) { Text(if (isDirectConnecting) "正在连接..." else "直接连接") }
                }
            }

            // 设备列表
            if (deviceList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("发现设备 (${deviceList.size}):", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                deviceList.forEach { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable(enabled = !isPreparingRoom) {
                                prepareRoom { requestedBaseUrl, requestedRoomId ->
                                    onDeviceSelect(
                                        requestedBaseUrl,
                                        requestedRoomId.toLongOrNull() ?: 0L,
                                        device
                                    )
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                device.location, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 模式卡片：哔哩哔哩云投屏 ────────────────────────────────────
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isPreparingRoom) {
                        prepareRoom { requestedBaseUrl, requestedRoomId ->
                            onBilibiliMode(requestedBaseUrl, requestedRoomId)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("哔哩哔哩小电视云投屏", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "适合 温莎 KTV 或 被投屏设备和手机不在同一局域网下的场景",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "通过b站云投屏，需要在被投屏设备的云视听小电视App和手机上都登录b站",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "›", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
