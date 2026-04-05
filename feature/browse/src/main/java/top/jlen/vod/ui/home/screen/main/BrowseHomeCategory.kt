package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.jlen.vod.AppConfig
import top.jlen.vod.AppRuntimeInfo
import top.jlen.vod.PLAYER_DESKTOP_UA
import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.CategoryFilterGroup
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.HotSearchGroup
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.PersistentCookieJar
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem
import top.jlen.vod.data.sanitizeUserFacingComposite


@Composable
fun HomeScreen(
    state: HomeUiState,
    noticeState: NoticeUiState,
    onRefresh: () -> Unit,
    onRefreshAnnouncements: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenAnnouncementList: () -> Unit,
    onOpenAnnouncementDetail: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    if (state.isLoading) {
        LoadingPane("首页加载中...")
        return
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val hotRows = remember(state.hot) { state.hot.chunked(POSTER_GRID_COLUMNS) }
    val latestRows = remember(state.visibleLatest) { state.visibleLatest.chunked(POSTER_GRID_COLUMNS) }

    LaunchedEffect(listState, latestRows.size, state.hasMoreLatest, state.isHomeAppending) {
        snapshotFlow { listState.maxVisiblePosterRowIndex("home_latest-") }
            .distinctUntilChanged()
            .collect { lastVisibleRowIndex ->
                if (
                    shouldAutoPreloadRows(
                        lastVisibleRowIndex = lastVisibleRowIndex,
                        totalRows = latestRows.size,
                        hasMore = state.hasMoreLatest,
                        isLoading = state.isHomeAppending
                    )
                ) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HomeTopBlock(
                onRefresh = onRefresh,
                noticeState = noticeState,
                onRefreshAnnouncements = onRefreshAnnouncements,
                onOpenAnnouncementList = onOpenAnnouncementList,
                onOpenAnnouncementDetail = onOpenAnnouncementDetail,
                onOpenSearch = onOpenSearch
            )
        }
        state.error?.let { message ->
            item { ErrorBanner(message = message, onRetry = onRefresh) }
        }
        if (state.slides.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "轮播推荐",
                    action = null,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = UiPalette.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onAction = {}
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = state.slides,
                        key = { it.stableKey() },
                        contentType = { "slide" }
                    ) { item ->
                        FeaturedCard(item = item, onClick = onOpenDetail)
                    }
                }
            }
        }
        if (state.hot.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "正在热播",
                    action = null,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = UiPalette.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onAction = {}
                )
            }
            posterGridRows(
                rows = hotRows,
                rowKeyPrefix = "home_hot",
                onOpenDetail = onOpenDetail
            )
        }
        if (state.featured.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "推荐",
                    action = null,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = UiPalette.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onAction = {}
                )
            }
            item {
                FeaturedCarouselSection(
                    items = state.featured,
                    onOpenDetail = onOpenDetail
                )
            }
        }
        item {
            SectionTitle(
                title = "最近更新",
                action = "进入片库",
                onAction = onOpenCategory
            )
        }
        if (state.latest.isEmpty()) {
            item {
                InlineEmptyStateCard(
                    message = "暂无内容",
                    actionLabel = "\u5237\u65b0",
                    onAction = onRefresh
                )
            }
        } else {
            posterGridRows(
                rows = latestRows,
                rowKeyPrefix = "home_latest",
                onOpenDetail = onOpenDetail
            )
            item {
                LoadMoreFooter(
                    hasMore = state.hasMoreLatest,
                    isLoading = state.isHomeAppending,
                    errorMessage = state.homeAppendError,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
fun CategoryScreen(
    state: HomeUiState,
    onSelectCategory: (AppleCmsCategory) -> Unit,
    onSelectFilter: (String, String) -> Unit,
    onRetryCategory: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val categoryRows = remember(state.visibleCategoryVideos) {
        state.visibleCategoryVideos.chunked(POSTER_GRID_COLUMNS)
    }
    val categoryRowKeyPrefix = remember(state.selectedCategory?.typeId) {
        "category_${state.selectedCategory?.typeId.orEmpty()}-"
    }

    LaunchedEffect(listState, categoryRows.size, categoryRowKeyPrefix, state.hasMoreCategoryVideos, state.isCategoryAppending) {
        snapshotFlow { listState.maxVisiblePosterRowIndex(categoryRowKeyPrefix) }
            .distinctUntilChanged()
            .collect { lastVisibleRowIndex ->
                if (
                    shouldAutoPreloadRows(
                        lastVisibleRowIndex = lastVisibleRowIndex,
                        totalRows = categoryRows.size,
                        hasMore = state.hasMoreCategoryVideos,
                        isLoading = state.isCategoryAppending
                    )
                ) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "片库",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = state.categories,
                    key = { it.typeId.ifBlank { it.typeName } },
                    contentType = { "category" }
                ) { category ->
                    SelectableAssistChip(
                        text = category.typeName,
                        selected = category.typeId == state.selectedCategory?.typeId,
                        onClick = { onSelectCategory(category) }
                    )
                }
            }
        }
        item {
            SectionTitle(
                title = state.selectedCategory?.typeName ?: "分类",
                action = null,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
                        contentDescription = null,
                        tint = UiPalette.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onAction = {}
            )
        }
        if (state.categoryFilterGroups.isNotEmpty()) {
            item {
                CategoryFilterPanel(
                    groups = state.categoryFilterGroups,
                    selectedFilters = state.selectedCategoryFilters,
                    onSelectFilter = onSelectFilter
                )
            }
        }
        item {
            state.error?.let { message ->
                ErrorBanner(
                    message = message,
                    onRetry = onRetryCategory
                )
            } ?: when {
                state.isCategoryLoading -> LoadingPane("分类加载中...")
                state.categoryVideos.isEmpty() -> InlineEmptyStateCard("暂无内容")
                else -> Spacer(modifier = Modifier.height(0.dp))
            }
        }
        if (!state.isCategoryLoading && state.categoryVideos.isNotEmpty()) {
            posterGridRows(
                rows = categoryRows,
                rowKeyPrefix = "category_${state.selectedCategory?.typeId.orEmpty()}",
                onOpenDetail = onOpenDetail
            )
        }
        if (!state.isCategoryLoading && state.categoryVideos.isNotEmpty()) {
            item {
                LoadMoreFooter(
                    hasMore = state.hasMoreCategoryVideos,
                    isLoading = state.isCategoryAppending,
                    errorMessage = state.categoryAppendError,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterPanel(
    groups: List<CategoryFilterGroup>,
    selectedFilters: Map<String, String>,
    onSelectFilter: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "${group.key}_all") {
                        SelectableAssistChip(
                            text = "全部",
                            selected = selectedFilters[group.key].isNullOrBlank(),
                            onClick = { onSelectFilter(group.key, "") }
                        )
                    }
                    items(
                        items = group.options,
                        key = { option -> "${group.key}_$option" },
                        contentType = { "category_filter_option" }
                    ) { option ->
                        SelectableAssistChip(
                            text = option,
                            selected = selectedFilters[group.key] == option,
                            onClick = { onSelectFilter(group.key, option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableAssistChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, fontWeight = FontWeight.SemiBold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) UiPalette.Ink else UiPalette.Surface,
            labelColor = if (selected) UiPalette.Surface else UiPalette.Ink
        ),
        border = AssistChipDefaults.assistChipBorder(
            borderColor = if (selected) UiPalette.Ink else UiPalette.BorderSoft,
            enabled = true
        )
    )
}
