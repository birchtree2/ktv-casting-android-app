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
 * 投屏扩展控制：B站模式显示弹幕与全部清晰度，DLNA 模式只显示 720/1080(beta)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliExtraControls(dlnaMode: Boolean = false) {
    var danmakuOn by remember { mutableStateOf(false) }
    // 切换投屏模式时必须重建本地状态；否则同一个 Compose slot 会保留
    // B 站模式的 1080P 选项，虽然 DLNA 实际仍从 720P 开始播放。
    var quality by remember(dlnaMode) { mutableStateOf(if (dlnaMode) BiliQuality.P720 else BiliQuality.DEFAULT) }
    val qualityOptions = if (dlnaMode) {
        listOf(BiliQuality.P720, BiliQuality.P1080)
    } else {
        BiliQuality.entries.toList()
    }

    LaunchedEffect(dlnaMode) {
        if (dlnaMode) {
            // Rust 每次新建 DLNA 引擎也会重置到 720P。不要读取遗留会话的
            // 1080P 状态，否则下拉框会在用户尚未选择时错误地显示 1080P。
            quality = BiliQuality.P720
        } else {
            val (currentDanmakuOn, currentQuality) = withContext(Dispatchers.IO) {
                RustEngine.getDanmakuState() to RustEngine.getQuality()
            }
            danmakuOn = currentDanmakuOn
            quality = BiliQuality.fromQn(currentQuality)
                ?.takeIf { it in qualityOptions }
                ?: BiliQuality.DEFAULT
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (!dlnaMode) {
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
        }

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
                value = if (dlnaMode && quality == BiliQuality.P1080) "1080P (Beta)" else quality.label,
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
                qualityOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(if (dlnaMode && option == BiliQuality.P1080) "1080P (Beta)" else option.label)
                        },
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
