package zju.bangdream.ktv.casting.ui.components

import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeControlGroup(castMode: String = "dlna") {
    var volumeValue by remember { mutableIntStateOf(50) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // 对于 Bilibili 投屏，初始值固定为 50（只能相对增减）
            if (castMode == "bilibili") {
                volumeValue = 50
                return@withContext
            }
            // 对于 DLNA，尝试从设备获取
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
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { commitVolume(volumeValue - 5) }) {
                Text("-", style = MaterialTheme.typography.titleMedium)
            }

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

            IconButton(onClick = { commitVolume(volumeValue + 5) }) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}