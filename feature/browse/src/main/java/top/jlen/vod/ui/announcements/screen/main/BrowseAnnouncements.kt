package top.jlen.vod.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.jlen.vod.data.AppNotice


@Composable
fun AnnouncementListScreen(
    state: NoticeUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNotice: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom)
            .statusBarsPadding()
            .padding(horizontal = UiDimens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleActionButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
                    Text(
                        text = "公告",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                }
                TextButton(
                    onClick = onRefresh,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Text("刷新", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!state.error.isNullOrBlank() && state.notices.isEmpty()) {
            item { ErrorBanner(message = state.error.orEmpty(), onRetry = onRefresh) }
        }
        when {
            state.isLoading && state.notices.isEmpty() -> item {
                LoadingPane("公告加载中...", style = FeedbackPaneStyle.Card)
            }
            state.notices.isEmpty() -> item {
                InlineEmptyStateCard(
                    message = "暂无公告",
                    actionLabel = "刷新",
                    onAction = onRefresh
                )
            }
            else -> items(
                items = state.notices,
                key = { it.id },
                contentType = { "announcement" }
            ) { notice ->
                AnnouncementListCardCompact(
                    notice = notice,
                    onClick = { onOpenNotice(notice.id) }
                )
            }
        }
    }
}

@Composable
fun AnnouncementDetailScreen(
    notice: AppNotice?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLink: (String) -> Unit = {}
) {
    when {
        isLoading && notice == null -> LoadingPane("公告加载中...")
        notice == null -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UiPalette.BackgroundBottom)
                    .statusBarsPadding()
                    .padding(horizontal = UiDimens.PagePadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
            ) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleActionButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
                        Text(
                            text = "公告详情",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                    }
                }
                item {
                    InlineEmptyStateCard(
                        message = "未找到公告内容",
                        actionLabel = "刷新",
                        onAction = onRefresh
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UiPalette.BackgroundBottom)
                    .statusBarsPadding()
                    .padding(horizontal = UiDimens.PagePadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
            ) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleActionButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
                        Text(
                            text = "公告详情",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, UiPalette.Border)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (notice.isPinned) {
                                    Box(
                                        modifier = Modifier
                                            .background(UiPalette.Accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "置顶",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = UiPalette.Accent
                                        )
                                    }
                                }
                                if (notice.isActive) {
                                    Text(
                                        text = "生效中",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = UiPalette.TextSecondary
                                    )
                                }
                            }
                            AnnouncementRichInlineText(
                                content = notice.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.Ink,
                                onOpenLink = onOpenLink
                            )
                            notice.formattedActiveTime.takeIf(String::isNotBlank)?.let { activeTime ->
                                Text(
                                    text = "生效时间：$activeTime",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiPalette.TextSecondary
                                )
                            } ?: notice.formattedPublishTime.takeIf(String::isNotBlank)?.let { publishTime ->
                                Text(
                                    text = "发布时间：$publishTime",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiPalette.TextSecondary
                                )
                            }
                            AnnouncementRichContent(
                                notice = notice,
                                onOpenLink = onOpenLink
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementListCard(
    notice: AppNotice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (notice.isPinned) {
                    Box(
                        modifier = Modifier
                            .background(UiPalette.Accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "置顶",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Accent
                        )
                    }
                }
                AnnouncementRichInlineText(
                    content = notice.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
            notice.previewText.takeIf(String::isNotBlank)?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notice.formattedActiveTime
                        .ifBlank { notice.formattedPublishTime }
                        .ifBlank { "暂无时间信息" },
                    style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextMuted
                )
                Text(
                    text = if (notice.isActive) "查看详情" else "历史公告",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Accent
                )
            }
        }
    }
}

@Composable
private fun AnnouncementListCardCompact(
    notice: AppNotice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (notice.isPinned) {
                    Box(
                        modifier = Modifier
                            .background(UiPalette.Accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "置顶",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Accent
                        )
                    }
                }
                AnnouncementRichInlineText(
                    content = notice.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            notice.previewText.takeIf(String::isNotBlank)?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notice.formattedActiveTime
                        .ifBlank { notice.formattedPublishTime }
                        .ifBlank { "暂无时间信息" },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextMuted
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(UiPalette.Accent.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (notice.isActive) "查看详情" else "历史公告",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.Accent,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
