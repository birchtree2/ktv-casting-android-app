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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.DlnaDeviceItem
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

private fun normalizeDeviceUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return "http://${trimmed.trimEnd('/')}:9958/bilibili/description.xml"
}

@Composable
fun DeviceSelectorScreen(
    onDeviceSelect: (String, Long, DlnaDeviceItem) -> Unit,
    onBilibiliMode: (baseUrl: String, roomId: String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }

    var baseUrl by remember { mutableStateOf(prefs.getString("base_url", "https://ktv.starfreedomx.top") ?: "") }
    var roomIdStr by remember { mutableStateOf(prefs.getString("room_id", "1111") ?: "") }
    var inputError by remember { mutableStateOf<String?>(null) }

    // DLNA 搜索状态
    var dlnaExpanded by remember { mutableStateOf(false) }
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
        if (baseUrl.isBlank()) { inputError = "请填写服务器网址"; return false }
        if (roomIdStr.isBlank()) { inputError = "请填写房间号"; return false }
        inputError = null
        return true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("KTV 投屏助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("填写房间信息后选择投屏方式", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(16.dp))

        // ── 房间信息 ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; inputError = null },
            label = { Text("服务器网址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = inputError != null && baseUrl.isBlank()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = roomIdStr,
            onValueChange = { roomIdStr = it; inputError = null },
            label = { Text("房间号") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = inputError != null && roomIdStr.isBlank()
        )
        inputError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("选择投屏方式", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        // ── 模式卡片：DLNA ───────────────────────────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clickable {
                if (!validateInputs()) return@clickable
                saveSettings()
                dlnaExpanded = !dlnaExpanded
            }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("📡", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("DLNA 局域网投屏", style = MaterialTheme.typography.titleMedium)
                    Text("适合 纯K 包厢、家庭 WiFi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("无需登录任何账号，直接搜索同一局域网内支持 DLNA 的设备（电视、智能屏幕等）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (dlnaExpanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            }
        }

        // DLNA 展开区域
        AnimatedVisibility(visible = dlnaExpanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // 自动搜索
                Button(
                    onClick = {
                        isSearching = true; searchError = null
                        thread {
                            val results = RustEngine.searchDevices()
                            deviceList = results; isSearching = false
                            if (results.isEmpty())
                                searchError = "未发现设备，请确认设备与手机在同一局域网，且 WiFi 支持多播"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching && !isDirectConnecting
                ) { Text(if (isSearching) "正在搜索..." else "自动搜索可用设备") }

                Spacer(modifier = Modifier.height(8.dp))
                Text("手动输入 IP（WiFi 不支持多播时使用）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = directIp,
                    onValueChange = { directIp = it },
                    label = { Text("设备 IP 或描述文件地址") },
                    placeholder = { Text("192.168.x.x 或 http://ip:9958/...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        val preview = if (directIp.isNotBlank()) normalizeDeviceUrl(directIp) else ""
                        if (preview.isNotEmpty()) Text("将连接：$preview",
                            style = MaterialTheme.typography.labelSmall)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (directIp.isBlank()) return@Button
                        saveSettings(); isDirectConnecting = true; searchError = null
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

                searchError?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(err, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                if (deviceList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("发现设备 (${deviceList.size}):", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    deviceList.forEach { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                saveSettings()
                                onDeviceSelect(baseUrl, roomIdStr.toLongOrNull() ?: 0L, device)
                            },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                Text(device.location, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 模式卡片：哔哩哔哩云投屏 ────────────────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clickable {
                if (!validateInputs()) return@clickable
                saveSettings()
                onBilibiliMode(baseUrl, roomIdStr)
            }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("☁️", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("哔哩哔哩云投屏", style = MaterialTheme.typography.titleMedium)
                    Text("适合 温莎 KTV 或 WiFi 有严格限制的场景",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("通过 B 站服务器中转，需要扫码登录 B 站账号。设备需先在 B 站 App 上开始投屏。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
