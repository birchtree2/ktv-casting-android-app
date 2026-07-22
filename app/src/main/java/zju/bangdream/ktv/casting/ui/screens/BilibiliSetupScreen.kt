package zju.bangdream.ktv.casting.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as GColor
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import zju.bangdream.ktv.casting.BilibiliDevice
import zju.bangdream.ktv.casting.RustEngine
import zju.bangdream.ktv.casting.parseBilibiliDevices
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.content.edit

private fun createQrBitmap(content: String): Bitmap? {
    return try {
        val size = 400
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = createBitmap(size, size)
        for (x in 0 until size) for (y in 0 until size) {
            bmp[x, y] = if (bits[x, y]) GColor.BLACK else GColor.WHITE
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
fun BilibiliSetupScreen(
    onDeviceSelected: (buvid: String, name: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }

    // -2=未开始, 0=等待扫码, 1=成功, -1=失败/过期
    var loginStatus by remember { mutableIntStateOf(RustEngine.getBilibiliLoginStatus()) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf(if (loginStatus == 1) "已登录" else "正在获取二维码...") }
    var devices by remember { mutableStateOf<List<BilibiliDevice>>(emptyList()) }
    var isLoadingDevices by remember { mutableStateOf(false) }
    var devicesError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var refreshDevicesKey by remember { mutableIntStateOf(0) }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }

    // 检测 token 是否过期（应用启动时自动调用）
    LaunchedEffect(Unit) {
        val isExpired = withContext(Dispatchers.IO) { RustEngine.isBilibiliSessionExpired() }
        if (isExpired && loginStatus == 1) {
            loginStatus = -2
            statusText = "登录已过期，请重新扫码登录"
        }
    }

    // QR 登录轮询
    LaunchedEffect(retryKey) {
        val current = withContext(Dispatchers.IO) { RustEngine.getBilibiliLoginStatus() }
        // 首次进入时保留已恢复的会话；后续 retryKey 变化表示用户明确要重新拉起扫码流程。
        if (retryKey == 0 && current == 1) {
            loginStatus = 1
            return@LaunchedEffect
        }

        qrBitmap = null
        statusText = "正在获取二维码..."
        withContext(Dispatchers.IO) { RustEngine.startBilibiliQrLogin() }

        while (true) {
            delay(1500)
            val s = withContext(Dispatchers.IO) { RustEngine.getBilibiliLoginStatus() }
            loginStatus = s
            when (s) {
                0 -> {
                    val url = withContext(Dispatchers.IO) { RustEngine.getBilibiliQrUrl() }
                    if (url.isNotEmpty() && qrBitmap == null) {
                        qrBitmap = withContext(Dispatchers.Default) { createQrBitmap(url) }
                        statusText = "请用 B 站 App 扫描上方二维码"
                    }
                }

                1 -> {
                    val json = withContext(Dispatchers.IO) { RustEngine.getBilibiliSessionJson() }
                    if (json.isNotEmpty()) prefs.edit { putString("bilibili_session", json) }
                    break
                }

                -1 -> {
                    statusText = "二维码已过期，请重新获取"; break
                }
            }
        }
    }

    // 登录成功后拉取设备列表
    LaunchedEffect(loginStatus, refreshDevicesKey) {
        if (loginStatus == 1) {
            isLoadingDevices = true
            devicesError = null
            val json = withContext(Dispatchers.IO) { RustEngine.listBilibiliDevices() }
            val latestStatus = withContext(Dispatchers.IO) { RustEngine.getBilibiliLoginStatus() }
            if (latestStatus != 1) {
                prefs.edit {
                    remove("bilibili_session")
                    remove("last_bilibili_device")
                    remove("last_bilibili_buvid")
                }
                devices = emptyList()
                isLoadingDevices = false
                loginStatus = -2
                statusText = "登录已失效，请重新扫码登录"
                retryKey++
                return@LaunchedEffect
            }

            val parsed = parseBilibiliDevices(json)
            devices = parsed
            if (parsed.isEmpty()) devicesError =
                "未找到在线投屏设备\n请先在哔哩哔哩小电视 App 上扫码登录同一账号，然后点击刷新"
            isLoadingDevices = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("哔哩哔哩云投屏", style = MaterialTheme.typography.titleLarge)
        }

        if (loginStatus != 1) {
            // ── 登录阶段 ──────────────────────────────────────────────────
            // 隐私说明卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp)
                    )
                    Text(
                        text = "本应用仅使用登录凭证（access_token）控制您所选设备的投屏播放，" +
                                "不会读取您的观看记录、个人资料或任何隐私信息。" +
                                "登录凭证仅保存在您的手机本地，不会上传至任何第三方服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("扫码登录 B 站账号", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // 二维码区域
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    qrBitmap != null -> Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                    )

                    loginStatus == -1 -> {
                        // expired, handled below
                    }

                    else -> CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (loginStatus == -1) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )

            if (loginStatus == -1) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { retryKey++ }) { Text("重新获取二维码") }
            }
        } else {
            // ── 设备选择阶段 ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择投屏设备", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showSwitchAccountDialog = true }) {
                    Text("切换账号")
                }
            }
            Text(
                "请选择要控制的设备（设备需登录同一b站账号）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoadingDevices -> CircularProgressIndicator()
                devicesError != null -> {
                    Text(
                        devicesError!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { refreshDevicesKey++ }) { Text("刷新设备列表") }
                }

                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onDeviceSelected(device.buvid, device.name) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                if (device.brand.isNotEmpty() || device.model.isNotEmpty()) {
                                    Text(
                                        "${device.brand} ${device.model}".trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 切换账号确认对话框
        if (showSwitchAccountDialog) {
            AlertDialog(
                onDismissRequest = { showSwitchAccountDialog = false },
                title = { Text("切换账号") },
                text = { Text("确定要清除当前登录信息并重新扫码登录吗？") },
                confirmButton = {
                    Button(onClick = {
                        showSwitchAccountDialog = false
                        RustEngine.clearBilibiliSession()
                        prefs.edit {
                            remove("bilibili_session")
                            remove("last_bilibili_device")
                            remove("last_bilibili_buvid")
                        }
                        loginStatus = -2
                        statusText = "正在获取二维码..."
                        retryKey++
                    }) { Text("确认") }
                },
                dismissButton = {
                    TextButton(onClick = { showSwitchAccountDialog = false }) { Text("取消") }
                }
            )
        }
    }
}
