package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.StandardCharsets
import top.jlen.vod.RuntimeEndpoints
import top.jlen.vod.data.CacheRetentionOption
import top.jlen.vod.data.CacheSizeLimitOption
import top.jlen.vod.data.CacheSizeSummary
import top.jlen.vod.data.formatCacheSize

@Composable
fun AccountSettingsHomePane(
    currentVersion: String,
    latestVersion: String,
    hasUpdate: Boolean,
    isUpdateLoading: Boolean,
    cacheRetention: CacheRetentionOption,
    cacheSizeLimit: CacheSizeLimitOption,
    cacheSizeSummary: CacheSizeSummary,
    isCacheSizeLoading: Boolean,
    hasCrashLog: Boolean,
    onOpenUpdate: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        AccountSettingsEntryCard(
            title = "缓存设置",
            description = "保存时间：${cacheRetention.label} / 上限：${cacheSizeLimit.label}",
            meta = when {
                isCacheSizeLoading -> "总缓存：统计中..."
                !cacheSizeSummary.isAvailable -> "总缓存：无法统计"
                else -> "总缓存：${formatCacheSize(cacheSizeSummary.totalBytes)}"
            },
            icon = Icons.Rounded.Cached,
            onClick = onOpenCache
        )
        AccountSettingsEntryCard(
            title = "问题日志",
            description = if (hasCrashLog) "已有本机日志" else "暂无日志",
            meta = "查看、复制、分享、清空",
            icon = Icons.Rounded.BugReport,
            onClick = onOpenLogs
        )
        AccountSettingsEntryCard(
            title = "关于",
            description = "Jlen 影视",
            meta = "作者、反馈群、发布页",
            icon = Icons.Rounded.Info,
            onClick = onOpenAbout
        )
    }
}

@Composable
fun AccountAboutSettingsScreen(
    currentVersion: String,
    onBack: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    AccountSettingsScaffold(
        title = "关于",
        subtitle = "应用信息和相关项目",
        onBack = onBack
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(UiPalette.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = top.jlen.vod.feature.browse.R.drawable.ic_app_icon),
                        contentDescription = "Jlen 影视",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Jlen 影视",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = "Version $currentVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary
                    )
                }
            }
        }
        item {
            AccountAboutGroup(title = "信息") {
                AccountAboutRow(
                    title = "作者",
                    subtitle = "堇年",
                    icon = Icons.Rounded.Person
                )
                AccountAboutRow(
                    title = "QQ群 / 反馈群",
                    subtitle = "点击加入群聊",
                    icon = Icons.Rounded.Groups,
                    onClick = { onOpenUrl(JLEN_VIDEO_FEEDBACK_GROUP_URL) }
                )
                AccountAboutRow(
                    title = "版本更新",
                    subtitle = "检查更新和查看发布说明",
                    icon = Icons.Rounded.NewReleases,
                    onClick = onOpenUpdate
                )
                AccountAboutRow(
                    title = "官网 / 发布页",
                    subtitle = "GitHub Releases",
                    icon = Icons.AutoMirrored.Rounded.Article,
                    onClick = { onOpenUrl(JLEN_VIDEO_RELEASES_URL) }
                )
                AccountAboutRow(
                    title = "用户协议与免责声明",
                    subtitle = "协议、隐私、免责说明",
                    icon = Icons.AutoMirrored.Rounded.Article,
                    onClick = onOpenAgreement
                )
                AccountAboutRow(
                    title = "问题反馈",
                    subtitle = "问题日志",
                    icon = Icons.Rounded.BugReport,
                    onClick = onOpenLogs
                )
            }
        }
        item {
            AccountAboutGroup(title = "开源") {
                AccountAboutRow(
                    title = "项目源码",
                    subtitle = "GitHub / JlenVideo",
                    icon = Icons.Rounded.Code,
                    onClick = { onOpenUrl(JLEN_VIDEO_REPOSITORY_URL) }
                )
                AccountAboutRow(
                    title = "开源许可证",
                    subtitle = "MIT License",
                    icon = Icons.AutoMirrored.Rounded.Article,
                    onClick = { onOpenUrl(JLEN_VIDEO_LICENSE_URL) }
                )
                AccountAboutRow(
                    title = "数据 API",
                    subtitle = "maccms-pure-video-api",
                    icon = Icons.Rounded.Storage,
                    onClick = { onOpenUrl(JLEN_VIDEO_API_REPOSITORY_URL) }
                )
                AccountAboutRow(
                    title = "管理后台",
                    subtitle = "appcenter-standalone-admin",
                    icon = Icons.AutoMirrored.Rounded.Article,
                    onClick = { onOpenUrl(JLEN_VIDEO_ADMIN_REPOSITORY_URL) }
                )
            }
        }
        item {
            Text(
                text = "Kotlin / Jetpack Compose",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextMuted
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
    cacheSizeLimit: CacheSizeLimitOption,
    cacheSizeSummary: CacheSizeSummary,
    isCacheSizeLoading: Boolean,
    isCacheClearing: Boolean,
    onBack: () -> Unit,
    onRefreshCacheSize: () -> Unit,
    onSetCacheRetention: (CacheRetentionOption) -> Unit,
    onSetCacheSizeLimit: (CacheSizeLimitOption) -> Unit,
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
                sizeLimit = cacheSizeLimit,
                summary = cacheSizeSummary,
                isLoading = isCacheSizeLoading,
                isClearing = isCacheClearing,
                onRefreshSize = onRefreshCacheSize,
                onSetRetention = onSetCacheRetention,
                onSetSizeLimit = onSetCacheSizeLimit,
                onClearCache = onClearAppCache
            )
        }
    }
}

@Composable
fun AccountAgreementSettingsScreen(onBack: () -> Unit) {
    AccountSettingsScaffold(
        title = "协议与免责说明",
        subtitle = "查看用户协议、隐私和免责声明",
        onBack = onBack
    ) {
        items(
            items = JlenUserAgreementSections,
            key = { it.title }
        ) { section ->
            AccountToolSection(
                title = section.title,
                description = when (section.title) {
                    "一、服务范围" -> "应用提供的基础能力"
                    "二、内容来源与播放说明" -> "站点内容和线路可用性"
                    "三、账号与会员信息" -> "登录后的站点账号信息"
                    "四、本地数据与缓存" -> "设备本机保存的数据范围"
                    "五、设备与运行信息" -> "版本统计和兼容性排查"
                    "六、隐私与问题日志" -> "问题日志和敏感信息提醒"
                    "七、合法合规使用" -> "使用边界和责任要求"
                    "八、免责声明" -> "第三方内容和异常情况说明"
                    else -> "继续使用前的确认内容"
                }
            ) {
                Text(
                    text = section.body,
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
    issueLogEntries: List<AccountIssueLogEntry>,
    hasCrashLog: Boolean,
    onBack: () -> Unit,
    onRefreshCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
    onOpenIssueLog: (AccountIssueLogEntry) -> Unit,
    onReadIssueLog: (String) -> String,
    onDeleteIssueLog: (String) -> Unit
) {
    val context = LocalContext.current
    AccountSettingsScaffold(
        title = "问题日志",
        subtitle = "排查本机运行问题",
        onBack = onBack
    ) {
        item {
            AccountToolSection(
                title = "问题日志",
                description = if (hasCrashLog) "已有日志" else "暂无日志"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRefreshCrashLog,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("刷新", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onClearCrashLog,
                        enabled = hasCrashLog,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("清空全部", maxLines = 1)
                    }
                }
            }
        }
        if (issueLogEntries.isEmpty()) {
            item {
                EmptyPane(
                    message = "暂无问题日志",
                    description = "",
                    style = FeedbackPaneStyle.Card
                )
            }
        } else {
            items(issueLogEntries, key = { it.id }) { entry ->
                val logText = remember(entry.id, crashLogText) { onReadIssueLog(entry.id) }
                IssueLogEntryCard(
                    entry = entry,
                    onOpen = { onOpenIssueLog(entry) },
                    onCopy = { copyIssueLog(context, logText) },
                    onShare = { shareIssueLog(context, entry, logText) },
                    onDelete = { onDeleteIssueLog(entry.id) }
                )
            }
        }
    }
}

@Composable
private fun IssueLogEntryCard(
    entry: AccountIssueLogEntry,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(entry.time, entry.summary).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Text("复制", maxLines = 1)
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Text("分享", maxLines = 1)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Text("删除", maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun AccountIssueLogDetailScreen(
    entry: AccountIssueLogEntry,
    logText: String,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()
    AccountSettingsScaffold(
        title = "日志详情",
        subtitle = entry.title,
        onBack = onBack
    ) {
        item {
            AccountToolSection(title = entry.title, description = entry.time) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 520.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(UiPalette.SurfaceSoft.copy(alpha = 0.7f))
                        .verticalScroll(scrollState)
                        .padding(12.dp)
                ) {
                    Text(
                        text = logText.ifBlank { "暂无问题日志" },
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text("复制", maxLines = 1)
                    }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text("分享", maxLines = 1)
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text("删除", maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun copyIssueLog(context: Context, logText: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("issue_log", logText))
    Toast.makeText(context, "问题日志已复制", Toast.LENGTH_SHORT).show()
}

private fun shareIssueLog(context: Context, entry: AccountIssueLogEntry, logText: String) {
    runCatching {
        val dir = File(context.cacheDir, "shared_logs").apply { mkdirs() }
        val file = File(dir, "${entry.id.removeSuffix(".txt")}.txt")
        file.writeText(logText, StandardCharsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, entry.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享问题日志"))
    }.onFailure {
        Toast.makeText(context, "分享日志失败", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AccountAboutGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = UiPalette.Ink,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AccountAboutRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = UiPalette.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = UiPalette.TextMuted
            )
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

private val JLEN_VIDEO_REPOSITORY_URL: String
    get() = RuntimeEndpoints.projectRepositoryUrl
private val JLEN_VIDEO_FEEDBACK_GROUP_URL: String
    get() = RuntimeEndpoints.feedbackGroupUrl
private val JLEN_VIDEO_RELEASES_URL: String
    get() = RuntimeEndpoints.githubReleasesUrl
private val JLEN_VIDEO_LICENSE_URL: String
    get() = RuntimeEndpoints.projectLicenseUrl
private val JLEN_VIDEO_API_REPOSITORY_URL: String
    get() = RuntimeEndpoints.apiRepositoryUrl
private val JLEN_VIDEO_ADMIN_REPOSITORY_URL: String
    get() = RuntimeEndpoints.adminRepositoryUrl

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
    AccountToolSection(
        title = "更新说明",
        description = "最近版本变更"
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AnnouncementRichHtmlContent(
                htmlContent = notes,
                fallbackContent = notes
            )
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
