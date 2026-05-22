package zju.bangdream.ktv.casting.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.DlnaDeviceItem
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

// 将用户输入的 IP 或完整 URL 标准化为设备描述 XML 地址
// - 若已是 http:// 开头，原样使用
// - 若是 IP 或 IP:PORT，补全为 B站小电视默认路径
private fun normalizeDeviceUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    // 去掉末尾多余的斜杠，然后补全
    val host = trimmed.trimEnd('/')
    return "http://$host:9958/bilibili/description.xml"
}

@Composable
fun DeviceSelectorScreen(onDeviceSelect: (String, Long, DlnaDeviceItem) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }

    var baseUrl by remember {
        mutableStateOf(prefs.getString("base_url", "https://ktv.starfreedomx.top") ?: "")
    }
    var roomIdStr by remember {
        mutableStateOf(prefs.getString("room_id", "1111") ?: "")
    }

    var deviceList by remember { mutableStateOf(emptyArray<DlnaDeviceItem>()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // 直连模式：IP 或完整 URL
    var directIp by remember {
        mutableStateOf(prefs.getString("direct_ip", "") ?: "")
    }
    var isDirectConnecting by remember { mutableStateOf(false) }

    val saveSettings = {
        prefs.edit().apply {
            putString("base_url", baseUrl)
            putString("room_id", roomIdStr)
            putString("direct_ip", directIp)
            apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "KTV 投屏助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("服务器网址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = roomIdStr,
            onValueChange = { roomIdStr = it },
            label = { Text("房间号") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 模式一：多播搜索 ──────────────────────────────────────────────
        Button(
            onClick = {
                saveSettings()
                isSearching = true
                searchError = null
                thread {
                    val results = RustEngine.searchDevices()
                    deviceList = results
                    isSearching = false
                    if (results.isEmpty()) {
                        searchError = "未发现设备，请确认设备与手机在同一局域网，且WiFi支持多播"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSearching && !isDirectConnecting
        ) {
            Text(if (isSearching) "正在搜索..." else "自动搜索可用设备")
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // ── 模式二：直连（输入 IP 或 URL）────────────────────────────────
        Text(
            text = "手动添加设备（WiFi不支持多播时使用）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = directIp,
            onValueChange = { directIp = it },
            label = { Text("设备 IP 或描述文件地址") },
            placeholder = { Text("192.168.x.x 或 http://ip:9958/bilibili/description.xml") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                val preview = if (directIp.isNotBlank()) normalizeDeviceUrl(directIp) else ""
                if (preview.isNotEmpty()) {
                    Text(text = "将连接：$preview", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (directIp.isBlank()) return@Button
                saveSettings()
                isDirectConnecting = true
                searchError = null
                val url = normalizeDeviceUrl(directIp)
                thread {
                    val results = RustEngine.searchDeviceByUrl(url)
                    deviceList = results
                    isDirectConnecting = false
                    if (results.isEmpty()) {
                        searchError = "连接失败，请检查 IP 地址是否正确，设备是否开启"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSearching && !isDirectConnecting && directIp.isNotBlank()
        ) {
            Text(if (isDirectConnecting) "正在连接..." else "直接连接")
        }

        // 错误提示
        searchError?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 设备列表 ─────────────────────────────────────────────────────
        Text(
            text = "可用设备 (${deviceList.size}):",
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(deviceList) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            saveSettings()
                            val roomId = roomIdStr.toLongOrNull() ?: 0L
                            onDeviceSelect(baseUrl, roomId, device)
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = device.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
