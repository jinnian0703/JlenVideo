package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppUpdateInfo

@Composable
fun FollowRemoveConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(UiPalette.DangerSurface.copy(alpha = 0.68f), RoundedCornerShape(999.dp))
                        .border(1.dp, UiPalette.DangerBorder.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "已追剧",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.DangerText
                    )
                }
                Text(
                    text = "取消追剧",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "确认把这部影片从追剧列表中移除吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.76f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "移除后不会影响播放记录和续播进度。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.36f),
                            contentColor = UiPalette.TextPrimary
                        )
                    ) {
                        Text("先保留", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.width(122.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.DangerText,
                            contentColor = UiPalette.Surface
                        )
                    ) {
                        Text("确认移除", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementPromptDialog(
    notice: AppNotice,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenDetail: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                brush = Brush.linearGradient(colors = listOf(UiPalette.Accent, UiPalette.AccentSoft)),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = UiPalette.AccentText
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "公告提醒",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.Ink
                            )
                            if (notice.isPinned) {
                                Box(
                                    modifier = Modifier
                                        .background(UiPalette.AccentSoft.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
                                        .border(1.dp, UiPalette.Accent.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "置顶",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = UiPalette.Accent
                                    )
                                }
                            }
                        }
                        Text(
                            text = notice.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.Ink
                        )
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        AnnouncementRichContent(
                            notice = notice,
                            onOpenLink = onOpenLink
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("稍后查看")
                    }
                    Button(
                        onClick = onOpenDetail,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.Accent,
                            contentColor = UiPalette.AccentText
                        )
                    ) {
                        Text("查看详情", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
@Composable
fun UpdatePromptDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
    onUpdate: () -> Unit
) {
    val notes = updateInfo.notes
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(UiPalette.Accent, UiPalette.AccentSoft)
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = UiPalette.AccentText
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "发现新版本",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Text(
                            text = "建议更新到最新版本，获得更好的稳定性和使用体验。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(y = 2.dp)
                            .background(
                                color = UiPalette.AccentGlow,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .border(1.dp, UiPalette.DangerBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "可更新",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.DangerText
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateVersionPill(
                        modifier = Modifier.weight(1f),
                        label = "当前版本",
                        value = updateInfo.currentVersion.ifBlank { "未知" }
                    )
                    UpdateVersionPill(
                        modifier = Modifier.weight(1f),
                        label = "最新版本",
                        value = updateInfo.latestVersion.ifBlank { "未知" }
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "更新说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.Ink
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (notes.isEmpty()) {
                                Text(
                                    text = "本次版本已发布，建议直接更新体验最新内容。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiPalette.TextSecondary
                                )
                            } else {
                                notes.forEach { note ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = UiPalette.Accent
                                        )
                                        Text(
                                            text = note.removePrefix("•").removePrefix("-").trim(),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = UiPalette.TextPrimary,
                                            maxLines = 8,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "稍后再说",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenRelease,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.width(4.dp))
                            Text(
                                text = "发布页",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = onUpdate,
                            modifier = Modifier
                                .weight(1.25f)
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.Accent,
                                contentColor = UiPalette.AccentText
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.width(4.dp))
                            Text(
                                text = "立即更新",
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateVersionPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = UiPalette.TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
        }
    }
}
