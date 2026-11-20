package top.jlen.vod.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import top.jlen.vod.ui.UiPalette
import java.io.File

@Composable
internal fun DownloadAlbumCard(title: String, cover: File?, downloaded: Int, single: Boolean, size: String,
    expanded: Boolean, managing: Boolean, toggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = UiPalette.Surface, border = BorderStroke(1.dp, UiPalette.Border)) {
        Row(Modifier.fillMaxWidth().clickable(enabled = !managing, onClickLabel = if (expanded) "收起剧集" else "展开剧集", onClick = toggle)
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(60.dp, 80.dp).clip(RoundedCornerShape(10.dp)).background(UiPalette.SurfaceStrong), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Movie, null, tint = UiPalette.TextMuted)
                if (cover != null) AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = UiPalette.Ink, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("已下载 $downloaded ${if (single) "部" else "集"} · $size", style = MaterialTheme.typography.bodySmall, color = UiPalette.TextSecondary)
            }
            if (!managing) Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = UiPalette.TextSecondary)
        }
    }
}

@Composable
internal fun RemoveDownloadsDialog(rows: List<Entry>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val action = when {
        rows.all { it.stage == Stage.DONE } -> "删除下载"
        rows.none { it.stage == Stage.DONE } -> "取消下载"
        else -> "移除所选项目"
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = UiPalette.Surface, border = BorderStroke(1.dp, UiPalette.Border)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).background(Brush.linearGradient(listOf(UiPalette.Accent, UiPalette.AccentSoft)), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DeleteOutline, null, tint = UiPalette.AccentText) }
                Text("$action？", style = MaterialTheme.typography.headlineSmall, color = UiPalette.Ink, fontWeight = FontWeight.Bold)
                Text(when (action) {
                    "取消下载" -> "所选任务将停止，已经下载的部分也会从手机移除。"
                    "删除下载" -> "所选视频将从手机移除，需要重新下载才能离线观看。"
                    else -> "所选任务和对应的本地视频将从手机移除。"
                }, style = MaterialTheme.typography.bodyMedium, color = UiPalette.TextSecondary)
                Surface(color = UiPalette.SurfaceSoft, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(rows.first().title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = UiPalette.Ink, fontWeight = FontWeight.SemiBold)
                        Text(if (rows.size == 1) rows.first().episode else "已选 ${rows.size} 项，涉及 ${rows.distinctBy { it.vod }.size} 部影片",
                            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = UiPalette.TextSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("保留") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = UiPalette.DangerSurface,
                        contentColor = UiPalette.DangerText)) { Text(action) }
                }
            }
        }
    }
}
