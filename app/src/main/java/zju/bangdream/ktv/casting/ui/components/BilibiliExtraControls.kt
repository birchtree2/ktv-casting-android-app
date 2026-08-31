package zju.bangdream.ktv.casting.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zju.bangdream.ktv.casting.BiliQuality
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

/**
 * B站投屏专属控制项：弹幕开关、清晰度选择。DLNA 模式没有对应概念，不展示这个组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliExtraControls() {
    var danmakuOn by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(BiliQuality.DEFAULT) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            danmakuOn = RustEngine.getDanmakuState()
            quality = BiliQuality.fromQn(RustEngine.getQuality()) ?: BiliQuality.DEFAULT
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "弹幕", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Switch(
                checked = danmakuOn,
                onCheckedChange = { target ->
                    danmakuOn = target
                    thread { RustEngine.setDanmaku(target) }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "清晰度", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        var qualityMenuExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = qualityMenuExpanded,
            onExpandedChange = { qualityMenuExpanded = !qualityMenuExpanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            OutlinedTextField(
                value = quality.label,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityMenuExpanded)
                }
            )

            ExposedDropdownMenu(
                expanded = qualityMenuExpanded,
                onDismissRequest = { qualityMenuExpanded = false },
                modifier = Modifier.exposedDropdownSize()
            ) {
                BiliQuality.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            quality = option
                            qualityMenuExpanded = false
                            thread { RustEngine.setQuality(option.qn) }
                        }
                    )
                }
            }
        }
    }
}
