package top.jlen.vod.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun EpisodeSkipPanel(source: String, position: Long, duration: Long, rules: EpisodeSkipRules,
                             onChange: (EpisodeSkipRules) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Spacer(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
            Surface(Modifier.align(Alignment.CenterEnd).fillMaxWidth(1f / 3f).fillMaxHeight(),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = Color(0xFF1B2028), contentColor = Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("片头片尾", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭片头片尾设置") }
                    }
                    Text("$source · 当前 ${formatMillis(position)}", style = MaterialTheme.typography.bodyMedium)
                    val intro = rules.copy(introEndMs = position)
                    val outro = rules.copy(outroLengthMs = duration - position)
                    val canMark = position in 1 until duration
                    MarkerSetting("片头结束", rules.introEndMs, "设为片头结束", canMark && intro.fits(duration),
                        onSet = { onChange(intro) }, onClear = { onChange(rules.copy(introEndMs = 0)) })
                    MarkerSetting("片尾时长", rules.outroLengthMs, "设为片尾开始", canMark && outro.fits(duration),
                        onSet = { onChange(outro) }, onClear = { onChange(rules.copy(outroLengthMs = 0)) })
                    Text("自动应用到本剧同一线路的各集。", style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = .76f))
                }
            }
        }
    }
}

@Composable
private fun MarkerSetting(label: String, value: Long, action: String, enabled: Boolean, onSet: () -> Unit, onClear: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = .08f), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$label · ${if (value > 0) formatMillis(value) else "未设置"}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (value > 0) TextButton(onClick = onClear) { Text("清除", color = Color.White) }
            }
            FilledTonalButton(onClick = onSet, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFD5E8FF), contentColor = Color(0xFF122941),
                    disabledContentColor = Color.White.copy(alpha = .55f))) { Text(action) }
        }
    }
}
