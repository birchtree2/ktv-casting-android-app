package zju.bangdream.ktv.casting.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.CastingService
import zju.bangdream.ktv.casting.RustEngine
import zju.bangdream.ktv.casting.ui.components.VolumeControlGroup
import kotlin.concurrent.thread

@Composable
fun CastingControlScreen(
    deviceName: String,
    roomId: Long,
    baseUrl: String,
    onReset: () -> Unit
) {
    val progressState by CastingService.playbackProgress.collectAsState()
    val (currentSec, totalSec) = progressState

    val songTitle by CastingService.currentSongTitle.collectAsState()
    val castMode by CastingService.castMode.collectAsState()

    var isPlaying by remember { mutableStateOf(true) }
    var isSwitchingSong by remember { mutableStateOf(false) }
    var switchingFromTitle by remember { mutableStateOf("") }
    var queuedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(songTitle) {
        if (isSwitchingSong && songTitle != switchingFromTitle) {
            isSwitchingSong = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            queuedCount = RustEngine.getQueuedSongsCount()
            kotlinx.coroutines.delay(1000)
        }
    }

    CastingControlContent(
        deviceName = deviceName,
        roomId = roomId,
        baseUrl = baseUrl,
        songTitle = songTitle,
        castMode = castMode,
        currentSec = currentSec,
        totalSec = totalSec,
        isPlaying = isPlaying,
        isSwitchingSong = isSwitchingSong,
        queuedCount = queuedCount,
        onTogglePause = {
            val result = RustEngine.togglePause()
            isPlaying = (result == 1)
        },
        onNext = {
            switchingFromTitle = songTitle
            isSwitchingSong = true
            RustEngine.nextSong()
        },
        onSeek = { target ->
            thread { RustEngine.jumpToSecs(target) }
        },
        onReset = {
            CastingService.resetProgress()
            onReset()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastingControlContent(
    deviceName: String,
    roomId: Long,
    baseUrl: String = "",
    songTitle: String,
    castMode: String = "dlna",
    currentSec: Long,
    totalSec: Long,
    isPlaying: Boolean,
    isSwitchingSong: Boolean = false,
    queuedCount: Int = 0,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    var isDraggingProgress by remember { mutableStateOf(false) }
    var dragProgressValue by remember { mutableFloatStateOf(0f) }
    var showResetDialog by remember { mutableStateOf(false) }
    val displaySec = if (isDraggingProgress) dragProgressValue.toLong() else currentSec
    val totalProgress = if (totalSec > 0) totalSec.toFloat() else 100f

    androidx.activity.compose.BackHandler(enabled = true) {
        showResetDialog = true
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false }, // 点击弹窗外部关闭
            title = { Text(text = "停止投屏") },
            text = { Text(text = "确定要停止当前投屏并更换设备吗？这将会中断播放。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset()
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- 头部设计 ---
        Text(text = "正在投屏至", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFFEEEEEE),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = deviceName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "房间号: $roomId",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // 歌曲标题：大字显示
        Text(
            text = songTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )

        if (songTitle == "暂无歌曲") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "可尝试点击「下一首」，或去网页端确认是否已点歌",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else if (queuedCount == 0 && songTitle != "暂无歌曲") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "队列已空，请去点歌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else if (queuedCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "队列中还有 $queuedCount 首歌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        AnimatedVisibility(
            visible = isSwitchingSong,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "切歌中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // --- 进度控制区 ---
        Slider(
            value = displaySec.toFloat().coerceIn(0f, totalProgress),
            onValueChange = {
                isDraggingProgress = true
                dragProgressValue = it
            },
            onValueChangeFinished = {
                onSeek(dragProgressValue.toInt())
                isDraggingProgress = false
            },
            valueRange = 0f..totalProgress,
            modifier = Modifier
                .fillMaxWidth(),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    modifier = Modifier
                        .size(10.dp) // 声明尺寸
                        .offset(y = 2.5.dp), // 如果还有极小偏差，用 offset 比 padding 更专业
                    thumbSize = DpSize(10.dp, 10.dp),
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(sliderState = sliderState, modifier = Modifier.height(4.dp), drawStopIndicator = null)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(displaySec), style = MaterialTheme.typography.bodySmall)
            Text(text = formatTime(totalSec), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(48.dp))

        // --- 主控按钮区 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTogglePause,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else Color(0xFF555555)
                )
            ) {
                Text(if (isPlaying) "暂停" else "播放")
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = queuedCount != 0
            ) {
                Text("下一首")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (baseUrl.isNotEmpty()) {
            val songUrl = baseUrl.trimEnd('/') + "/room?roomId=$roomId"
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(songUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("去点歌")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 音量控制区 (引入外部组件) ---
        VolumeControlGroup(castMode = castMode)

        Spacer(modifier = Modifier.height(56.dp))

        // 退出/重置
        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("更换设备 / 停止投屏")
        }
    }
}

/**
 * 时间格式化工具 (00:00)
 */
private fun formatTime(seconds: Long): String {
    if (seconds < 0) return "00:00"
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Android Studio 预览专用函数
 */
@Preview(showBackground = true, name = "Casting Control - Normal")
@Composable
fun CastingControlPreview() {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFFF3377))) {
        CastingControlContent(
            deviceName = "Preview Device",
            roomId = 8888,
            castMode = "dlna",
            currentSec = 45,
            totalSec = 210,
            isPlaying = true,
            onTogglePause = {},
            onNext = {},
            onSeek = {},
            onReset = {},
            songTitle = "八月のif - Poppin'Party"
        )
    }
}