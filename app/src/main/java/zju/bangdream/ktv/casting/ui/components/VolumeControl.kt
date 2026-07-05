package zju.bangdream.ktv.casting.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

private const val VOLUME_STEP = 5
private val VolumeStepButtonWidth = 64.dp
private val VolumeStepButtonHeight = 48.dp

@Composable
fun VolumeControlGroup(castMode: String = "dlna") {
    if (castMode == "bilibili") {
        BilibiliVolumeControl()
    } else {
        DlnaVolumeControl()
    }
}

/**
 * B站投屏没有绝对音量接口，只能发送设备原生的相对“音量+/-”指令，
 * 也没有读回当前音量的方式，因此不展示会被误解为可拖动的音量条。
 */
@Composable
private fun BilibiliVolumeControl() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = "设备音量（小电视模式）", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VolumeStepButton(label = "-", onClick = { thread { RustEngine.volumeDown(VOLUME_STEP) } })

            Surface(
                modifier = Modifier.weight(1f).height(VolumeStepButtonHeight),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "用两侧 - / + 调节",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            VolumeStepButton(label = "+", onClick = { thread { RustEngine.volumeUp(VOLUME_STEP) } })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DlnaVolumeControl() {
    var volumeValue by remember { mutableIntStateOf(50) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            repeat(5) {
                val remoteVol = RustEngine.getVolume()
                if (remoteVol > 0) {
                    volumeValue = remoteVol
                    return@withContext
                }
                delay(1000)
            }
        }
    }

    val commitVolume = { newValue: Int ->
        val target = newValue.coerceIn(0, 100)
        volumeValue = target
        thread { RustEngine.setVolume(target) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "设备音量", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(
                text = " $volumeValue",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDragging) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Normal
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VolumeStepButton(label = "-", onClick = { commitVolume(volumeValue - VOLUME_STEP) })

            Slider(
                value = volumeValue.toFloat(),
                onValueChange = {
                    isDragging = true
                    volumeValue = it.toInt()
                },
                onValueChangeFinished = {
                    isDragging = false
                    commitVolume(volumeValue)
                },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        modifier = Modifier
                            .size(10.dp)
                            .offset(y = 2.5.dp),
                        thumbSize = DpSize(10.dp, 10.dp),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(sliderState = sliderState, modifier = Modifier.height(2.dp))
                }
            )

            VolumeStepButton(label = "+", onClick = { commitVolume(volumeValue + VOLUME_STEP) })
        }
    }
}

@Composable
private fun VolumeStepButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(VolumeStepButtonWidth).height(VolumeStepButtonHeight),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
