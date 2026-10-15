package zju.bangdream.ktv.casting.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.DlnaProxySettings

@Composable
fun rememberDlnaProxyEnabled(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }
    var enabled by remember(prefs) { mutableStateOf(DlnaProxySettings.isEnabled(context)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DlnaProxySettings.DIRECT_KEY) enabled = DlnaProxySettings.isEnabled(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Refresh after registration, including changes while this screen was absent.
        enabled = DlnaProxySettings.isEnabled(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
fun DlnaProxyControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled = rememberDlnaProxyEnabled()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("反向代理", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = { DlnaProxySettings.setEnabled(context, it) })
        }
        Text(
            if (enabled) "已开启：由手机代理媒体，支持 B站 1080P。"
            else "已关闭：设备直连媒体，B站仅支持 720P。",
            style = MaterialTheme.typography.bodySmall
        )
        Text("下次切歌或点击“重新投屏”生效。", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun DlnaEnvironmentDialog(onChoice: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("是否在纯K？") },
        text = { Text("在纯K请开启反向代理；其他环境可关闭。之后也可在播放页或设置中调整。") },
        confirmButton = { TextButton(onClick = { onChoice(true) }) { Text("是，在纯K") } },
        dismissButton = { TextButton(onClick = { onChoice(false) }) { Text("否，其他环境") } }
    )
}
