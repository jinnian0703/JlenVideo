package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cached
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.jlen.vod.data.CacheRetentionOption
import top.jlen.vod.data.CacheSizeSummary
import top.jlen.vod.data.formatCacheSize

private const val ACCOUNT_AGREEMENT_TEXT =
    "应用用于浏览和播放站点提供的影视信息。登录后会使用站点账号能力同步资料、会员积分、追剧和播放记录；本地会保存必要的引导状态、搜索历史、播放进度和问题日志。请在合法合规的前提下使用。"

@Composable
fun AccountSettingsHomeScreen(
    currentVersion: String,
    latestVersion: String,
    hasUpdate: Boolean,
    isUpdateLoading: Boolean,
    cacheRetention: CacheRetentionOption,
    cacheSizeSummary: CacheSizeSummary,
    isCacheSizeLoading: Boolean,
    hasCrashLog: Boolean,
    onBack: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenLogs: () -> Unit
) {
    AccountSettingsScaffold(
        title = "设置",
        subtitle = "版本、缓存、协议和问题日志",
        onBack = onBack
    ) {
        item {
            AccountSettingsEntryCard(
                title = "版本更新",
                description = when {
                    isUpdateLoading -> "正在检查更新"
                    hasUpdate -> "发现新版本"
                    latestVersion.isNotBlank() -> "当前已是最新版本"
                    else -> "可手动检查发布页"
                },
                meta = buildString {
                    append("当前版本：")
                    append(currentVersion)
                    if (latestVersion.isNotBlank()) {
                        append(" / 最新版本：")
                        append(latestVersion)
                    }
                },
                icon = Icons.Rounded.NewReleases,
                onClick = onOpenUpdate
            )
        }
        item {
            AccountSettingsEntryCard(
                title = "缓存设置",
                description = "保存时间：${cacheRetention.label}",
                meta = when {
                    isCacheSizeLoading -> "总缓存：统计中..."
                    !cacheSizeSummary.isAvailable -> "总缓存：无法统计"
                    else -> "总缓存：${formatCacheSize(cacheSizeSummary.totalBytes)}"
                },
                icon = Icons.Rounded.Cached,
                onClick = onOpenCache
            )
        }
        item {
            AccountSettingsEntryCard(
                title = "用户协议与隐私说明",
                description = "首次启动确认内容",
                meta = "查看应用用途、账号数据和本地数据说明",
                icon = Icons.AutoMirrored.Rounded.Article,
                onClick = onOpenAgreement
            )
        }
        item {
            AccountSettingsEntryCard(
                title = "问题日志",
                description = if (hasCrashLog) "已有本机日志" else "暂无本机日志",
                meta = "刷新、复制或清空本机崩溃日志",
                icon = Icons.Rounded.BugReport,
                onClick = onOpenLogs
            )
        }
    }
}

@Composable
fun AccountUpdateSettingsScreen(
    currentVersion: String,
    latestVersion: String,
    notes: String,
    hasUpdate: Boolean,
    isUpdateLoading: Boolean,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenRelease: () -> Unit,
    onDownloadUpdate: () -> Unit
) {
    AccountSettingsScaffold(
        title = "版本更新",
        subtitle = "检查新版本和查看发布说明",
        onBack = onBack
    ) {
        item {
            AccountToolSection(
                title = "版本信息",
                description = when {
                    isUpdateLoading -> "正在检查更新"
                    hasUpdate -> "发现新版本，可直接前往下载"
                    latestVersion.isNotBlank() -> "当前已是最新版本"
                    else -> "可手动检查发布页"
                }
            ) {
                Text(
                    text = buildString {
                        append("当前版本：")
                        append(currentVersion)
                        if (latestVersion.isNotBlank()) {
                            append("\n最新版本：")
                            append(latestVersion)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.Ink
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        enabled = !isUpdateLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (isUpdateLoading) "检查中..." else "检查更新")
                    }
                    Button(
                        onClick = if (hasUpdate) onDownloadUpdate else onOpenRelease,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.Accent,
                            contentColor = UiPalette.AccentText
                        )
                    ) {
                        Text(if (hasUpdate) "前往下载" else "查看发布")
                    }
                }
            }
        }
        if (notes.isNotBlank()) {
            item {
                UpdateNotesSection(notes = notes)
            }
        }
    }
}

@Composable
fun AccountCacheSettingsScreen(
    cacheRetention: CacheRetentionOption,
    cacheSizeSummary: CacheSizeSummary,
    isCacheSizeLoading: Boolean,
    isCacheClearing: Boolean,
    onBack: () -> Unit,
    onRefreshCacheSize: () -> Unit,
    onSetCacheRetention: (CacheRetentionOption) -> Unit,
    onClearAppCache: () -> Unit
) {
    AccountSettingsScaffold(
        title = "缓存设置",
        subtitle = "管理内容缓存和图片缓存",
        onBack = onBack
    ) {
        item {
            CacheSettingsSection(
                retention = cacheRetention,
                summary = cacheSizeSummary,
                isLoading = isCacheSizeLoading,
                isClearing = isCacheClearing,
                onRefreshSize = onRefreshCacheSize,
                onSetRetention = onSetCacheRetention,
                onClearCache = onClearAppCache
            )
        }
    }
}

@Composable
fun AccountAgreementSettingsScreen(onBack: () -> Unit) {
    AccountSettingsScaffold(
        title = "用户协议与隐私说明",
        subtitle = "查看首次启动确认内容",
        onBack = onBack
    ) {
        item {
            AccountToolSection(
                title = "用户协议与隐私说明",
                description = "应用用途、账号数据和本地数据说明"
            ) {
                Text(
                    text = ACCOUNT_AGREEMENT_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextPrimary
                )
            }
        }
    }
}

@Composable
fun AccountCrashLogSettingsScreen(
    crashLogText: String,
    hasCrashLog: Boolean,
    onBack: () -> Unit,
    onRefreshCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit
) {
    AccountSettingsScaffold(
        title = "问题日志",
        subtitle = "排查本机运行问题",
        onBack = onBack
    ) {
        item {
            AccountToolSection(
                title = "问题日志",
                description = if (hasCrashLog) "已有本机日志" else "暂无本机日志"
            ) {
                if (hasCrashLog) {
                    CrashLogCard(
                        logText = crashLogText,
                        onRefresh = onRefreshCrashLog,
                        onClear = onClearCrashLog
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前没有崩溃日志。",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = UiPalette.TextSecondary
                        )
                        OutlinedButton(
                            onClick = onRefreshCrashLog,
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("刷新")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccountSettingsScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = UiPalette.Ink
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun AccountSettingsEntryCard(
    title: String,
    description: String,
    meta: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UiPalette.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = UiPalette.Accent
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = UiPalette.TextMuted
            )
        }
    }
}

@Composable
internal fun UpdateNotesSection(notes: String) {
    var expanded by remember(notes) { mutableStateOf(false) }
    AccountToolSection(
        title = "更新说明",
        description = "最近版本变更"
    ) {
        Box(
            modifier = if (expanded) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 184.dp)
                    .clip(RoundedCornerShape(12.dp))
            }
        ) {
            AnnouncementRichHtmlContent(
                htmlContent = notes,
                fallbackContent = notes
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Accent
                )
            }
        }
    }
}

@Composable
internal fun AccountToolSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(UiPalette.SurfaceSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
            content()
        }
    }
}
