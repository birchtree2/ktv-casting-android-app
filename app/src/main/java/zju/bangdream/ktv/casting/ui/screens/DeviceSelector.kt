package zju.bangdream.ktv.casting.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.draw.clip
import zju.bangdream.ktv.casting.DlnaDeviceItem
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

@OptIn(ExperimentalMaterial3Api::class)
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

    var showDescriptionHelp by remember { mutableStateOf(false) }
    var descriptionTabIndex by remember { mutableStateOf(0) }
    var descriptionInput by remember { mutableStateOf("") }
    var isResolvingDescription by remember { mutableStateOf(false) }
    var descriptionError by remember { mutableStateOf<String?>(null) }

    val saveSettings = {
        prefs.edit().apply {
            putString("base_url", baseUrl)
            putString("room_id", roomIdStr)
            apply()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("连接设备") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
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

            Button(
                onClick = {
                    saveSettings()
                    isSearching = true
                    thread {
                        val results = RustEngine.searchDevices()
                        deviceList = results
                        isSearching = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSearching
            ) {
                Text(if (isSearching) "正在搜索..." else "搜索可用设备")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "可用设备 (${deviceList.size}):", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showDescriptionHelp = true }) {
                    Text("手动填写地址", style = MaterialTheme.typography.labelSmall)
                }
            }
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
                            Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = device.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }

    if (showDescriptionHelp) {
        AlertDialog(
            onDismissRequest = { showDescriptionHelp = false },
            title = { Text("描述文件地址") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val tabShape = RoundedCornerShape(12.dp)
                    Surface(
                        shape = tabShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        TabRow(
                            selectedTabIndex = descriptionTabIndex,
                            modifier = Modifier.clip(tabShape),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[descriptionTabIndex])
                                )
                            }
                        ) {
                            Tab(
                                selected = descriptionTabIndex == 0,
                                onClick = { descriptionTabIndex = 0 },
                                text = { Text("小电视") }
                            )
                            Tab(
                                selected = descriptionTabIndex == 1,
                                onClick = { descriptionTabIndex = 1 },
                                text = { Text("自定义") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = descriptionInput,
                        onValueChange = { descriptionInput = it },
                        label = { Text(if (descriptionTabIndex == 0) "设备 IP" else "描述文件地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    descriptionError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    if (descriptionTabIndex == 0) {
                        val ip = descriptionInput.trim()
                        if (ip.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "将使用: http://$ip:9958/bilibili/description.xml",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        descriptionError = null
                        val descriptionUrl = if (descriptionTabIndex == 0) {
                            val ip = descriptionInput.trim()
                            if (ip.isNotEmpty()) {
                                "http://$ip:9958/bilibili/description.xml"
                            } else {
                                ""
                            }
                        } else {
                            descriptionInput.trim()
                        }

                        val trimmed = descriptionUrl.trim()
                        if (trimmed.isEmpty()) {
                            descriptionError = "请输入 IP 或描述文件地址"
                            return@TextButton
                        }

                        // 调用 Rust 层通过 URL 搜索设备，避免直接进入投屏
                        isResolvingDescription = true
                        thread {
                            try {
                                val results = RustEngine.searchDeviceByUrl(trimmed)
                                Handler(Looper.getMainLooper()).post {
                                    isResolvingDescription = false
                                    if (results.isNotEmpty()) {
                                        saveSettings()
                                        val roomId = roomIdStr.toLongOrNull() ?: 0L
                                        showDescriptionHelp = false
                                        onDeviceSelect(baseUrl, roomId, results[0])
                                    } else {
                                        descriptionError = "未通过该地址找到设备，请确认地址或 IP 是否正确"
                                    }
                                }
                            } catch (e: Exception) {
                                Handler(Looper.getMainLooper()).post {
                                    isResolvingDescription = false
                                    descriptionError = "搜索设备时发生错误: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = !isResolvingDescription
                ) {
                    if (isResolvingDescription) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("搜索中...")
                        }
                    } else {
                        Text("应用")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionHelp = false }) {
                    Text("取消")
                }
            }
        )
    }
}
