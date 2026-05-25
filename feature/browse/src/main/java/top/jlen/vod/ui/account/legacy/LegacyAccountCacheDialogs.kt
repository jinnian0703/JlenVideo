package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.jlen.vod.data.CacheRetentionOption
import top.jlen.vod.data.CacheSizeLimitOption

@Composable
internal fun ClearCacheConfirmDialog(
    totalSize: String,
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
                        text = "缓存设置",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.DangerText
                    )
                }
                Text(
                    text = "清除缓存",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "确认清除当前内容缓存和图片缓存吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.76f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "预计清除 $totalSize。不会删除登录状态、追剧、播放记录、搜索历史和问题日志。",
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
                        Text("取消", fontWeight = FontWeight.Bold)
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
                        Text("确认清除", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CacheRetentionPickerDialog(
    selectedRetention: CacheRetentionOption,
    onDismiss: () -> Unit,
    onSelect: (CacheRetentionOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UiPalette.Surface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(UiPalette.AccentSoft.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                        .border(1.dp, UiPalette.Accent.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "缓存设置",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.Accent
                    )
                }
                Text(
                    text = "缓存保存时间",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "超过保存时间后，内容缓存会重新请求；图片缓存仍可手动清除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CacheRetentionOption.entries.forEach { option ->
                    val selected = option == selectedRetention
                    CachePickerOptionCard(
                        title = option.label,
                        description = option.cacheRetentionDescription(),
                        selected = selected,
                        onClick = { onSelect(option) }
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.72f))
                ) {
                    Text(
                        text = "手动刷新、清除缓存或内容更新时，仍会拉取最新数据。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.TextSecondary)
            ) {
                Text("取消", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun CacheSizeLimitPickerDialog(
    selectedLimit: CacheSizeLimitOption,
    onDismiss: () -> Unit,
    onSelect: (CacheSizeLimitOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UiPalette.Surface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(UiPalette.AccentSoft.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                        .border(1.dp, UiPalette.Accent.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "缓存设置",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.Accent
                    )
                }
                Text(
                    text = "缓存上限",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "当内容缓存和图片缓存超过上限时，会优先删除较旧缓存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CacheSizeLimitOption.entries.forEach { option ->
                    CachePickerOptionCard(
                        title = option.label,
                        description = option.cacheSizeLimitDescription(),
                        selected = option == selectedLimit,
                        onClick = { onSelect(option) }
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.72f))
                ) {
                    Text(
                        text = "手动清除缓存仍会立即删除内容缓存和图片缓存。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.TextSecondary)
            ) {
                Text("取消", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CachePickerOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                UiPalette.AccentSoft.copy(alpha = 0.34f)
            } else {
                UiPalette.SurfaceSoft.copy(alpha = 0.72f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) UiPalette.Accent.copy(alpha = 0.7f) else UiPalette.BorderSoft
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) UiPalette.Accent else UiPalette.TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
            if (selected) {
                Row(
                    modifier = Modifier
                        .background(UiPalette.Surface.copy(alpha = 0.86f), RoundedCornerShape(999.dp))
                        .border(
                            1.dp,
                            UiPalette.Accent.copy(alpha = 0.2f),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = UiPalette.Accent
                    )
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.Accent
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(
                            1.dp,
                            UiPalette.BorderSoft,
                            RoundedCornerShape(999.dp)
                        )
                )
            }
        }
    }
}

private fun CacheRetentionOption.cacheRetentionDescription(): String =
    when (this) {
        CacheRetentionOption.ThreeDays -> "默认选项，兼顾新鲜度和加载速度。"
        CacheRetentionOption.SevenDays -> "减少重复请求，适合常用内容。"
        CacheRetentionOption.ThirtyDays -> "长时间保留内容缓存，减少网络加载。"
        CacheRetentionOption.Forever -> "不按时间自动过期，只能手动清除或刷新覆盖。"
    }

private fun CacheSizeLimitOption.cacheSizeLimitDescription(): String =
    when (this) {
        CacheSizeLimitOption.FiftyMb -> "占用更克制，适合存储空间紧张时使用。"
        CacheSizeLimitOption.OneHundredMb -> "保留常用内容和少量图片缓存。"
        CacheSizeLimitOption.ThreeHundredMb -> "默认选项，适合日常浏览和加载速度。"
        CacheSizeLimitOption.OneGb -> "保留更多图片和内容缓存。"
        CacheSizeLimitOption.Unlimited -> "不按大小自动清理，只按时间或手动清理。"
    }
