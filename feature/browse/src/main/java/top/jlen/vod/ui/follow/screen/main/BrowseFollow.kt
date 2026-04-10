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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FollowScreen(
    state: FollowUiState,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLibrary: () -> Unit
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "追剧",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = "聚合已加入追剧的连载内容，更新和续播都在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary
                    )
                }
                CircleActionButton(icon = Icons.Rounded.Refresh, onClick = onRefresh)
            }
        }

        if (!state.error.isNullOrBlank() && state.items.isNotEmpty()) {
            item {
                ErrorBanner(
                    message = state.error.orEmpty(),
                    onRetry = onRefresh,
                    density = ErrorBannerDensity.Compact
                )
            }
        }

        when {
            !state.isLoggedIn -> item {
                EmptyPane(
                    message = "登录后可查看追剧列表",
                    description = "已加入追剧的连载内容会自动汇总到这里",
                    actionLabel = "去登录",
                    onAction = onOpenAccount,
                    style = FeedbackPaneStyle.Card
                )
            }

            state.isLoading && state.items.isEmpty() -> item {
                LoadingPane("追剧列表加载中...", style = FeedbackPaneStyle.Card)
            }

            !state.error.isNullOrBlank() && state.items.isEmpty() -> item {
                ErrorBanner(message = state.error.orEmpty(), onRetry = onRefresh)
            }

            state.items.isEmpty() -> item {
                EmptyPane(
                    message = "暂无追剧内容",
                    description = "先把想追的影片加入追剧，后续更新会自动显示在这里",
                    actionLabel = "去片库",
                    onAction = onOpenLibrary,
                    style = FeedbackPaneStyle.Card
                )
            }

            else -> {
                if (state.updatingItems.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = "有更新",
                            action = "${state.updatingItems.size} 部",
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Update,
                                    contentDescription = null,
                                    tint = UiPalette.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onAction = {}
                        )
                    }
                    items(
                        items = state.updatingItems,
                        key = { "follow_update_${it.vodId}" },
                        contentType = { "follow_item" }
                    ) { item ->
                        FollowUpCard(item = item, onOpenDetail = onOpenDetail)
                    }
                }

                if (state.continueItems.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = "继续观看",
                            action = "${state.continueItems.size} 部",
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = UiPalette.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onAction = {}
                        )
                    }
                    items(
                        items = state.continueItems,
                        key = { "follow_continue_${it.vodId}" },
                        contentType = { "follow_item" }
                    ) { item ->
                        FollowUpCard(item = item, onOpenDetail = onOpenDetail)
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUpCard(
    item: FollowUpItem,
    onOpenDetail: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(item.vodId) },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (item.hasUpdate) UiPalette.Accent.copy(alpha = 0.24f) else UiPalette.BorderSoft.copy(alpha = 0.78f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box {
                RetryablePosterImage(
                    data = item.poster,
                    title = item.title,
                    width = 312,
                    height = 414,
                    modifier = Modifier
                        .size(width = 90.dp, height = 122.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                if (item.hasUpdate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(10.dp)
                            .background(UiPalette.Accent, CircleShape)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.hasUpdate) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(UiPalette.Accent.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = item.updateLabel.ifBlank { "有更新" },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = UiPalette.Accent
                            )
                        }
                    }
                }

                item.latestEpisodeLabel
                    .takeIf { it.isNotBlank() }
                    ?.let { latest ->
                        FollowMetaLine(
                            label = "最新状态",
                            value = latest
                        )
                    }

                FollowMetaLine(
                    label = "观看进度",
                    value = item.watchedEpisodeLabel
                )
                FollowMetaLine(
                    label = "最后观看",
                    value = item.lastWatchedAtText
                )

                item.subtitle
                    .takeIf { it.isNotBlank() }
                    ?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = UiPalette.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = UiPalette.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (item.sourceName.isNotBlank()) item.sourceName else "已追剧",
                            style = MaterialTheme.typography.labelMedium,
                            color = UiPalette.TextMuted
                        )
                    }
                    Button(
                        onClick = { onOpenDetail(item.vodId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.hasUpdate) UiPalette.Accent else UiPalette.SurfaceSoft,
                            contentColor = if (item.hasUpdate) UiPalette.AccentText else UiPalette.Ink
                        )
                    ) {
                        Text(
                            text = item.primaryActionLabel,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowMetaLine(
    label: String,
    value: String
) {
    if (value.isBlank()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = UiPalette.TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = UiPalette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
