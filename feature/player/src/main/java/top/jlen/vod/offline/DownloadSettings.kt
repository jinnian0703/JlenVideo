package top.jlen.vod.offline

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.jlen.vod.data.CacheSizeSummary
import top.jlen.vod.ui.UiPalette
import top.jlen.vod.ui.appTopInsetsPadding
import java.util.Locale

@Composable
fun DownloadSettingsScreen(onBack: () -> Unit) {
    val lib = OfflineLibrary.get(LocalContext.current)
    val mobile by lib.mobile.collectAsState()
    val count by lib.parallelism.collectAsState()
    Column(Modifier.fillMaxSize().background(UiPalette.BackgroundBottom).appTopInsetsPadding()
        .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text("下载设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = UiPalette.Surface, border = BorderStroke(1.dp, UiPalette.Border)) {
            Row(Modifier.fillMaxWidth().toggleable(mobile, role = Role.Switch, onValueChange = lib::setMobile).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("允许移动网络下载", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (mobile) "使用移动网络可能产生流量费用" else "仅在 Wi-Fi 下下载", style = MaterialTheme.typography.bodySmall, color = UiPalette.TextSecondary)
                }
                Switch(checked = mobile, onCheckedChange = null)
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = UiPalette.Surface, border = BorderStroke(1.dp, UiPalette.Border)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("并行下载数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("每集占一个名额，超出的任务按顺序排队。调低上限不会中断正在下载的任务。", style = MaterialTheme.typography.bodySmall, color = UiPalette.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n ->
                        FilterChip(selected = n == count, onClick = { lib.setParallelism(n) }, modifier = Modifier.weight(1f),
                            label = { Text("$n") })
                    }
                }
            }
        }
    }
}

@Composable
fun StorageOverview(cache: CacheSizeSummary, loading: Boolean, onDownloads: () -> Unit) {
    val context = LocalContext.current
    val disk = rememberDiskUsage(OfflineLibrary.get(context))
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Surface(shape = RoundedCornerShape(22.dp), color = UiPalette.Surface, border = BorderStroke(1.dp, UiPalette.Border)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("手机存储", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = UiPalette.Ink)
            if (disk == null || disk.capacity <= 0 || loading || !cache.isAvailable) {
                Text(if (!cache.isAvailable && !loading) "暂时无法统计缓存占比" else "正在统计容量…", color = UiPalette.TextSecondary)
            } else {
                val (download, cached, other, free) = storageParts(disk.capacity, disk.free, disk.downloads, cache.totalBytes)
                Text("${size(free)} 可用", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = UiPalette.Ink)
                Text("总容量 ${size(disk.capacity)}", style = MaterialTheme.typography.bodySmall, color = UiPalette.TextSecondary)
                val parts = listOf(Triple("下载", download, UiPalette.Accent), Triple("缓存", cached, Color(0xFFDEAC36)),
                    Triple("其他已用空间", other, Color(0xFF6586AC)), Triple("剩余空间", free, UiPalette.BorderSoft))
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp))) {
                    parts.filter { it.second > 0 }.forEach { (_, bytes, color) ->
                        Spacer(Modifier.fillMaxHeight().weight((bytes.toDouble()/disk.capacity).toFloat()).background(color))
                    }
                }
                parts.forEach { (name, bytes, color) ->
                    val pct = 100.0 * bytes / disk.capacity
                    val share = if (pct > 0 && pct < 0.1) "<0.1%" else String.format(Locale.getDefault(), "%.1f%%", pct)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = UiPalette.Ink)
                        Text("${size(bytes)} · $share", style = MaterialTheme.typography.labelLarge, color = UiPalette.TextSecondary)
                    }
                }
            }
            HorizontalDivider(color = UiPalette.Border)
            TextButton(onClick = onDownloads, modifier = Modifier.align(Alignment.End)) {
                Text("打开下载管理"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward,null,Modifier.size(16.dp))
            }
        }
    }
}

internal fun storageParts(total: Long, available: Long, downloads: Long, cache: Long): List<Long> {
    val free = available.coerceIn(0, total)
    val used = total - free
    val saved = downloads.coerceIn(0, used)
    val cached = cache.coerceIn(0, used - saved)
    return listOf(saved, cached, used - saved - cached, free)
}
