package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import top.jlen.vod.BuildConfig
import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.HotSearchGroup
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem

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
                source = BuildConfig.APPLE_CMS_BASE_URL.removePrefix("https://").removePrefix("http://").trimEnd('/'),
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
                    val selected = category.typeId == state.selectedCategory?.typeId
                    AssistChip(
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.typeName, fontWeight = FontWeight.SemiBold) },
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
        item {
            when {
                state.isCategoryLoading -> LoadingPane("分类加载中...")
                state.categoryVideos.isEmpty() -> InlineEmptyStateCard("暂无内容")
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
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenSearchResults: (String) -> Unit,
    onSearchHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLoadHotSearches: () -> Unit
) {
    LaunchedEffect(Unit) {
        onLoadHotSearches()
    }
    SearchLandingContent(
        state = state,
        onQueryChange = onQueryChange,
        onOpenSearchResults = onOpenSearchResults,
        onSearchHistory = onSearchHistory,
        onClearHistory = onClearHistory,
        onLoadHotSearches = onLoadHotSearches
    )
}

private const val POSTER_GRID_COLUMNS = 3

private fun LazyListScope.posterGridRows(
    rows: List<List<VodItem>>,
    rowKeyPrefix: String,
    onOpenDetail: (String) -> Unit
) {
    items(
        count = rows.size,
        key = { rowIndex ->
            val firstKey = rows[rowIndex].firstOrNull()?.stableKey().orEmpty()
            "$rowKeyPrefix-$rowIndex-$firstKey"
        },
        contentType = { "poster_row" }
    ) { rowIndex ->
        PosterGridRow(
            rowItems = rows[rowIndex],
            onOpenDetail = onOpenDetail
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchLandingContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenSearchResults: (String) -> Unit,
    onSearchHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLoadHotSearches: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "搜索",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
        }
        item {
            SearchInputCard(
                query = state.query,
                onQueryChange = onQueryChange,
                onSearch = { onOpenSearchResults(state.query.trim()) }
            )
        }
        if (state.history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = UiPalette.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "搜索记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.Ink
                        )
                    }
                    TextButton(onClick = onClearHistory) {
                        Text("清空")
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(
                        items = state.history,
                        key = { it },
                        contentType = { "history" }
                    ) { keyword ->
                        AssistChip(
                            onClick = {
                                onSearchHistory(keyword)
                                onOpenSearchResults(keyword)
                            },
                            label = { Text(keyword) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = UiPalette.Surface,
                                labelColor = UiPalette.Ink
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = UiPalette.BorderSoft,
                                enabled = true
                            )
                        )
                    }
                }
            }
        }
        item {
            SectionTitle(
                title = "热搜榜",
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
            when {
                state.isHotSearchLoading && state.hotSearchGroups.isEmpty() -> LoadingPane("热搜加载中..")
                !state.hotSearchError.isNullOrBlank() && state.hotSearchGroups.isEmpty() ->
                    ErrorBanner(message = state.hotSearchError, onRetry = onLoadHotSearches)
                state.hotSearchGroups.isEmpty() -> EmptyPane("暂无热搜")
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.hotSearchGroups.forEach { group ->
                        HotSearchBoard(
                            group = group,
                            onPickKeyword = { keyword ->
                                onSearchHistory(keyword)
                                onOpenSearchResults(keyword)
                            }
                        )
                    }
                }
            }
        }
    }
}

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleActionButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
                Text(
                    text = "搜索结果",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
        }
        item {
            SearchInputCard(
                query = state.query,
                onQueryChange = onQueryChange,
                onSearch = onSearch
            )
        }
        item {
            Text(
                text = if (state.submittedQuery.isBlank()) "输入片名开始搜索" else "“${state.submittedQuery}” 的结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
        }
        when {
            state.isLoading -> item { LoadingPane("搜索中..") }
            !state.error.isNullOrBlank() && state.results.isEmpty() ->
                item { ErrorBanner(message = state.error, onRetry = onSearch) }
            state.results.isEmpty() ->
                item { EmptyPane(if (state.submittedQuery.isBlank()) "输入片名搜索" else "暂无结果") }
            else -> items(
                items = state.results,
                key = { it.stableKey() },
                contentType = { "search_result" }
            ) { item ->
                ListCard(item = item, onClick = onOpenDetail)
            }
        }
    }
}
*/

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleActionButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
                Text(
                    text = "搜索结果",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
        }
        item {
            SearchInputCard(
                query = state.query,
                onQueryChange = onQueryChange,
                onSearch = onSearch
            )
        }
        item {
            Text(
                text = if (state.submittedQuery.isBlank()) "输入片名开始搜索" else "\"${state.submittedQuery}\" 的结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
        }
        when {
            state.isLoading -> item { LoadingPane("搜索中..") }
            !state.error.isNullOrBlank() && state.submittedQuery.isBlank() && state.results.isEmpty() ->
                item { SearchEmptyState(query = "", message = state.error) }
            !state.error.isNullOrBlank() && state.results.isEmpty() ->
                item { ErrorBanner(message = state.error, onRetry = onSearch) }
            state.results.isEmpty() ->
                item { SearchEmptyState(query = state.submittedQuery) }
            else -> items(
                items = state.results,
                key = { it.stableKey() },
                contentType = { "search_result" }
            ) { item ->
                ListCard(item = item, onClick = onOpenDetail)
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    query: String,
    message: String? = null
) {
    val title = when {
        message != null && query.isBlank() -> message
        query.isBlank() -> "输入片名开始搜索"
        else -> "没找到“$query”"
    }
    val description = when {
        query.isBlank() -> "也可以试试主演、导演或别名"
        else -> "换个关键词再试试，片名、主演、别名都可以"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(UiPalette.AccentSoft.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = UiPalette.Accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = UiPalette.Accent,
            unfocusedBorderColor = UiPalette.BorderSoft,
            focusedTextColor = UiPalette.Ink,
            unfocusedTextColor = UiPalette.Ink,
            cursorColor = UiPalette.Accent,
            focusedTrailingIconColor = UiPalette.Accent,
            unfocusedTrailingIconColor = UiPalette.TextSecondary,
            focusedContainerColor = UiPalette.Surface,
            unfocusedContainerColor = UiPalette.Surface,
            focusedPlaceholderColor = UiPalette.TextMuted,
            unfocusedPlaceholderColor = UiPalette.TextMuted
        ),
        placeholder = { Text("搜片名") },
        trailingIcon = {
            TextButton(
                onClick = onSearch,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text("搜索", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun HotSearchBoard(
    group: HotSearchGroup,
    onPickKeyword: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = group.platform,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            group.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPickKeyword(item.keyword) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = item.rank.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.rank <= 3) UiPalette.Accent else UiPalette.TextSecondary
                    )
                    Text(
                        text = item.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        color = UiPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
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
            item { ErrorBanner(message = state.error, onRetry = onRefresh) }
        }
        when {
            state.isLoading && state.notices.isEmpty() -> item { LoadingPane("公告加载中...") }
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
    onRefresh: () -> Unit
) {
    when {
        isLoading && notice == null -> LoadingPane("公告加载中...")
        notice == null -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
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
                            Text(
                                text = notice.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.Ink
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
                            AnnouncementRichText(content = notice.displayContent)
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
                Text(
                    text = notice.title,
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
private fun AnnouncementRichText(content: String) {
    val blocks = remember(content) {
        content
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            val lines = block
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

            when {
                lines.isEmpty() -> Unit
                lines.all(::isAnnouncementListLine) -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        lines.forEach { line ->
                            val label = line.removeAnnouncementListPrefix()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(6.dp)
                                        .background(UiPalette.Accent, CircleShape)
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = UiPalette.Ink,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                )
                            }
                        }
                    }
                }

                lines.size == 1 && block.length <= 20 -> {
                    Text(
                        text = block,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.Ink
                    )
                }

                else -> {
                    Text(
                        text = block,
                        style = MaterialTheme.typography.bodyLarge,
                        color = UiPalette.Ink,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        textAlign = TextAlign.Start
                    )
                }
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
                Text(
                    text = notice.title,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onCheckUpdate: () -> Unit,
    onSelectSection: (AccountSection) -> Unit,
    onRefreshSection: () -> Unit,
    onChangePortrait: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenHistoryRecord: (top.jlen.vod.data.UserCenterItem) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onClearFavorites: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onUpgradeMembership: (MembershipPlan) -> Unit,
    onProfileEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onProfileTabChange: (Boolean) -> Unit,
    onSaveProfile: () -> Unit,
    onAuthModeChange: (AccountAuthMode) -> Unit,
    onRegisterEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshRegisterCaptcha: () -> Unit,
    onSendRegisterCode: () -> Unit,
    onRegister: () -> Unit,
    onFindPasswordEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onRefreshFindPasswordCaptcha: () -> Unit,
    onFindPassword: () -> Unit,
    onSendEmailCode: () -> Unit,
    onBindEmail: () -> Unit,
    onUnbindEmail: () -> Unit,
    onRefreshCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit
) {
    val context = LocalContext.current
    val showLoggedInContent = state.session.isLoggedIn
    val noticeMessage = state.error?.takeIf { it.isNotBlank() } ?: state.message?.takeIf { it.isNotBlank() }
    val noticeTone = if (!state.error.isNullOrBlank()) {
        AccountNoticeTone.Error
    } else {
        AccountNoticeTone.Info
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Column {
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
        }

        if (showLoggedInContent) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, UiPalette.Border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (state.session.portraitUrl.isNotBlank()) {
                                AsyncImage(
                                    model = state.session.portraitUrl,
                                    contentDescription = state.session.userName,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onChangePortrait),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(UiPalette.Accent.copy(alpha = 0.15f))
                                        .clickable(onClick = onChangePortrait),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.session.userName.take(1).ifBlank { "我" },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = UiPalette.Accent
                                    )
                                }
                                TextButton(
                                    onClick = onChangePortrait,
                                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                                ) {
                                    Text("修改头像", fontWeight = FontWeight.Bold)
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = state.session.userName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = UiPalette.Ink
                                )
                                Text(
                                    text = state.session.groupName.ifBlank { "普通用户" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiPalette.TextSecondary
                                )
                                if (state.session.userId.isNotBlank()) {
                                    Text(
                                        text = "用户 ID：${state.session.userId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UiPalette.TextMuted
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = onRefreshSection,
                                enabled = !state.isLoading,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, UiPalette.BorderSoft)
                            ) {
                                Text("刷新")
                            }
                            Button(
                                onClick = onLogout,
                                enabled = !state.isLoading,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UiPalette.Accent,
                                    contentColor = UiPalette.AccentText
                                )
                            ) {
                                Text(if (state.isLoading) "正在退出..." else "退出登录")
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = AccountSection.entries.toList(),
                        key = { it.name },
                        contentType = { "account_section" }
                    ) { section ->
                        val selected = state.selectedSection == section
                        AssistChip(
                            onClick = { onSelectSection(section) },
                            label = {
                                Text(
                                    when (section) {
                                        AccountSection.Profile -> "资料"
                                        AccountSection.Favorites -> "收藏"
                                        AccountSection.History -> "记录"
                                        AccountSection.Member -> "会员"
                                        AccountSection.About -> "关于"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) UiPalette.Accent else UiPalette.Surface,
                                labelColor = if (selected) UiPalette.AccentText else UiPalette.Ink
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = if (selected) UiPalette.Accent else UiPalette.BorderSoft,
                                enabled = true
                            )
                        )
                    }
                }
            }

            noticeMessage?.let { message ->
                item {
                    AccountStatusNotice(
                        message = message,
                        tone = noticeTone,
                        actionLabel = if (noticeTone == AccountNoticeTone.Error) "刷新" else null,
                        onAction = if (noticeTone == AccountNoticeTone.Error) onRefreshSection else null
                    )
                }
            }

            item {
                when (state.selectedSection) {
                    AccountSection.Profile -> AccountProfilePaneV2(
                        isLoading = state.isContentLoading,
                        fields = state.profileFields,
                        editor = state.profileEditor,
                        isSaving = state.isActionLoading,
                        isEditTab = state.isProfileEditTab,
                        onTabChange = onProfileTabChange,
                        onEditorChange = onProfileEditorChange,
                        onSave = onSaveProfile,
                        onSendEmailCode = onSendEmailCode,
                        onBindEmail = onBindEmail,
                        onUnbindEmail = onUnbindEmail
                    )
                    AccountSection.Favorites -> AccountRecordPane(
                        title = "我的收藏",
                        emptyMessage = "你还没有收藏任何内容",
                        isLoading = state.isContentLoading,
                        items = state.favoriteItems,
                        hasMore = !state.favoriteNextPageUrl.isNullOrBlank(),
                        isActionLoading = state.isActionLoading,
                        onLoadMore = onLoadMoreFavorites,
                        onPrimaryAction = { item ->
                            if (item.vodId.isNotBlank()) onOpenDetail(item.vodId)
                        },
                        onDeleteItem = onDeleteFavorite,
                        onClearAll = onClearFavorites
                    )
                    AccountSection.History -> AccountRecordPane(
                        title = "播放记录",
                        emptyMessage = "还没有播放记录",
                        isLoading = state.isContentLoading,
                        items = state.historyItems,
                        hasMore = !state.historyNextPageUrl.isNullOrBlank(),
                        isActionLoading = state.isActionLoading,
                        onLoadMore = onLoadMoreHistory,
                        onPrimaryAction = onOpenHistoryRecord,
                        onDeleteItem = onDeleteHistory,
                        onClearAll = onClearHistory
                    )
                    AccountSection.Member -> MembershipPaneV2(
                        isLoading = state.isContentLoading,
                        info = state.membershipInfo,
                        plans = state.membershipPlans,
                        isActionLoading = state.isActionLoading,
                        onUpgrade = onUpgradeMembership
                    )
                    AccountSection.About -> AboutPane(
                        currentVersion = state.updateInfo?.currentVersion?.ifBlank { BuildConfig.VERSION_NAME }
                            ?: BuildConfig.VERSION_NAME,
                        latestVersion = state.updateInfo?.latestVersion.orEmpty(),
                        notes = state.updateInfo?.notes.orEmpty(),
                        hasUpdate = state.updateInfo?.hasUpdate == true,
                        isUpdateLoading = state.isUpdateLoading,
                        crashLogText = state.latestCrashLog,
                        hasCrashLog = state.hasCrashLog,
                        onCheckUpdate = onCheckUpdate,
                        onRefreshCrashLog = onRefreshCrashLog,
                        onClearCrashLog = onClearCrashLog,
                        onOpenRelease = {
                            val targetUrl = state.updateInfo?.releasePageUrl
                                ?.takeIf { it.isNotBlank() }
                                ?: "https://github.com/jinnian0703/JlenVideo/releases"
                            openExternalUrl(context, targetUrl)
                        },
                        onDownloadUpdate = {
                            val targetUrl = state.updateInfo?.downloadUrl
                                ?.takeIf { it.isNotBlank() }
                                ?: state.updateInfo?.releasePageUrl
                                ?.takeIf { it.isNotBlank() }
                                ?: "https://github.com/jinnian0703/JlenVideo/releases"
                            openExternalUrl(context, targetUrl)
                        }
                    )
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            AssistChip(
                                onClick = { onAuthModeChange(AccountAuthMode.Login) },
                                label = { Text("登录", fontWeight = FontWeight.SemiBold) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (state.authMode == AccountAuthMode.Login) UiPalette.Accent else UiPalette.Surface,
                                    labelColor = if (state.authMode == AccountAuthMode.Login) UiPalette.AccentText else UiPalette.Ink
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (state.authMode == AccountAuthMode.Login) UiPalette.Accent else UiPalette.BorderSoft,
                                    enabled = true
                                )
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { onAuthModeChange(AccountAuthMode.Register) },
                                label = { Text("注册", fontWeight = FontWeight.SemiBold) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (state.authMode == AccountAuthMode.Register) UiPalette.Accent else UiPalette.Surface,
                                    labelColor = if (state.authMode == AccountAuthMode.Register) UiPalette.AccentText else UiPalette.Ink
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (state.authMode == AccountAuthMode.Register) UiPalette.Accent else UiPalette.BorderSoft,
                                    enabled = true
                                )
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { onAuthModeChange(AccountAuthMode.FindPassword) },
                                label = { Text("找回密码", fontWeight = FontWeight.SemiBold) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (state.authMode == AccountAuthMode.FindPassword) UiPalette.Accent else UiPalette.Surface,
                                    labelColor = if (state.authMode == AccountAuthMode.FindPassword) UiPalette.AccentText else UiPalette.Ink
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (state.authMode == AccountAuthMode.FindPassword) UiPalette.Accent else UiPalette.BorderSoft,
                                    enabled = true
                                )
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { onAuthModeChange(AccountAuthMode.About) },
                                label = { Text("关于", fontWeight = FontWeight.SemiBold) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (state.authMode == AccountAuthMode.About) UiPalette.Accent else UiPalette.Surface,
                                    labelColor = if (state.authMode == AccountAuthMode.About) UiPalette.AccentText else UiPalette.Ink
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (state.authMode == AccountAuthMode.About) UiPalette.Accent else UiPalette.BorderSoft,
                                    enabled = true
                                )
                            )
                        }
                    }

                    noticeMessage?.let { message ->
                        AccountStatusNotice(
                            message = message,
                            tone = noticeTone
                        )
                    }

                    when (state.authMode) {
                        AccountAuthMode.Register -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, UiPalette.Border)
                            ) {
                                AccountRegisterPane(
                                    state = state,
                                    onEditorChange = onRegisterEditorChange,
                                    onRefreshCaptcha = onRefreshRegisterCaptcha,
                                    onSendCode = onSendRegisterCode,
                                    onSubmit = onRegister
                                )
                            }
                        }

                        AccountAuthMode.FindPassword -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, UiPalette.Border)
                            ) {
                                AccountFindPasswordPane(
                                    state = state,
                                    onEditorChange = onFindPasswordEditorChange,
                                    onRefreshCaptcha = onRefreshFindPasswordCaptcha,
                                    onSubmit = onFindPassword
                                )
                            }
                        }

                        AccountAuthMode.About -> {
                            AboutPane(
                                currentVersion = state.updateInfo?.currentVersion?.ifBlank { BuildConfig.VERSION_NAME }
                                    ?: BuildConfig.VERSION_NAME,
                                latestVersion = state.updateInfo?.latestVersion.orEmpty(),
                                notes = state.updateInfo?.notes.orEmpty(),
                                hasUpdate = state.updateInfo?.hasUpdate == true,
                                isUpdateLoading = state.isUpdateLoading,
                                crashLogText = state.latestCrashLog,
                                hasCrashLog = state.hasCrashLog,
                                onCheckUpdate = onCheckUpdate,
                                onRefreshCrashLog = onRefreshCrashLog,
                                onClearCrashLog = onClearCrashLog,
                                onOpenRelease = {
                                    val targetUrl = state.updateInfo?.releasePageUrl
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "https://github.com/jinnian0703/JlenVideo/releases"
                                    openExternalUrl(context, targetUrl)
                                },
                                onDownloadUpdate = {
                                    val targetUrl = state.updateInfo?.downloadUrl
                                        ?.takeIf { it.isNotBlank() }
                                        ?: state.updateInfo?.releasePageUrl
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "https://github.com/jinnian0703/JlenVideo/releases"
                                    openExternalUrl(context, targetUrl)
                                }
                            )
                        }

                        AccountAuthMode.Login -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, UiPalette.Border)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    OutlinedTextField(
                                        value = state.userName,
                                        onValueChange = onUserNameChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        singleLine = true,
                                        label = { Text("用户名") },
                                        placeholder = { Text("请输入站内用户名") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UiPalette.Accent,
                                            unfocusedBorderColor = UiPalette.BorderSoft,
                                            focusedTextColor = UiPalette.Ink,
                                            unfocusedTextColor = UiPalette.Ink,
                                            cursorColor = UiPalette.Accent,
                                            focusedContainerColor = UiPalette.Surface,
                                            unfocusedContainerColor = UiPalette.Surface
                                        )
                                    )
                                    OutlinedTextField(
                                        value = state.password,
                                        onValueChange = onPasswordChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        singleLine = true,
                                        label = { Text("密码") },
                                        placeholder = { Text("请输入密码") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = KeyboardType.Password
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UiPalette.Accent,
                                            unfocusedBorderColor = UiPalette.BorderSoft,
                                            focusedTextColor = UiPalette.Ink,
                                            unfocusedTextColor = UiPalette.Ink,
                                            cursorColor = UiPalette.Accent,
                                            focusedContainerColor = UiPalette.Surface,
                                            unfocusedContainerColor = UiPalette.Surface
                                        )
                                    )
                                    Button(
                                        onClick = onLogin,
                                        enabled = !state.isLoading,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = UiPalette.Accent,
                                            contentColor = UiPalette.AccentText
                                        )
                                    ) {
                                        Text(if (state.isLoading) "正在登录..." else "立即登录", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPane(
    currentVersion: String,
    latestVersion: String,
    notes: String,
    hasUpdate: Boolean,
    isUpdateLoading: Boolean,
    crashLogText: String,
    hasCrashLog: Boolean,
    onCheckUpdate: () -> Unit,
    onRefreshCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
    onOpenRelease: () -> Unit,
    onDownloadUpdate: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = buildString {
                    append("当前版本：")
                    append(currentVersion)
                    if (latestVersion.isNotBlank()) {
                        append("  ·  最新版本：")
                        append(latestVersion)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
            Text(
                text = when {
                    isUpdateLoading -> "检查中"
                    hasUpdate -> "发现新版本"
                    latestVersion.isNotBlank() -> "已是最新版本"
                    else -> "检查更新"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.Ink
            )
            if (notes.isNotBlank()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !isUpdateLoading,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                ) {
                    Text(if (isUpdateLoading) "检查中..." else "检查更新")
                }
                Button(
                    onClick = if (hasUpdate) onDownloadUpdate else onOpenRelease,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UiPalette.Accent,
                        contentColor = UiPalette.AccentText
                    )
                ) {
                    Text(if (hasUpdate) "前往下载" else "查看发布")
                }
            }
            if (hasCrashLog) {
                CrashLogCard(
                    logText = crashLogText,
                    onRefresh = onRefreshCrashLog,
                    onClear = onClearCrashLog
                )
            } else {
                Text(
                    text = "暂无崩溃日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun CrashLogCard(
    logText: String,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "最近一次崩溃日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = logText.ifBlank { "暂无崩溃日志" },
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                ) {
                    Text("刷新日志")
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", logText))
                        Toast.makeText(context, "崩溃日志已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = logText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                ) {
                    Text("复制日志")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = logText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                ) {
                    Text("清空日志")
                }
            }
        }
    }
}

@Composable
private fun AccountRegisterPane(
    state: AccountUiState,
    onEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    val captchaBitmap = remember(state.registerCaptcha) {
        runCatching {
            state.registerCaptcha?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = state.registerEditor.userName,
            onValueChange = { value -> onEditorChange { it.copy(userName = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("用户名") },
            placeholder = { Text("请输入注册用户名") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        OutlinedTextField(
            value = state.registerEditor.password,
            onValueChange = { value -> onEditorChange { it.copy(password = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("密码") },
            placeholder = { Text("请输入注册密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        OutlinedTextField(
            value = state.registerEditor.confirmPassword,
            onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("确认密码") },
            placeholder = { Text("请再次输入密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        OutlinedTextField(
            value = state.registerEditor.contact,
            onValueChange = { value -> onEditorChange { it.copy(contact = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text(state.registerContactLabel) },
            placeholder = { Text("请输入${state.registerContactLabel}") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (state.registerChannel == "phone") KeyboardType.Phone else KeyboardType.Email
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        if (state.registerRequiresCode) {
            OutlinedTextField(
                value = state.registerEditor.code,
                onValueChange = { value -> onEditorChange { it.copy(code = value) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                label = { Text(state.registerCodeLabel) },
                placeholder = { Text("请输入${state.registerCodeLabel}") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UiPalette.Accent,
                    unfocusedBorderColor = UiPalette.BorderSoft,
                    focusedTextColor = UiPalette.Ink,
                    unfocusedTextColor = UiPalette.Ink,
                    cursorColor = UiPalette.Accent,
                    focusedContainerColor = UiPalette.Surface,
                    unfocusedContainerColor = UiPalette.Surface
                )
            )
            OutlinedButton(
                onClick = onSendCode,
                enabled = !state.isActionLoading,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, UiPalette.BorderSoft)
            ) {
                Text(if (state.isActionLoading) "发送中..." else "发送${state.registerCodeLabel}")
            }
        }

        if (state.registerRequiresVerify) {
            OutlinedTextField(
                value = state.registerEditor.verify,
                onValueChange = { value -> onEditorChange { it.copy(verify = value) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                label = { Text("图片验证码") },
                placeholder = { Text("请输入图片验证码") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UiPalette.Accent,
                    unfocusedBorderColor = UiPalette.BorderSoft,
                    focusedTextColor = UiPalette.Ink,
                    unfocusedTextColor = UiPalette.Ink,
                    cursorColor = UiPalette.Accent,
                    focusedContainerColor = UiPalette.Surface,
                    unfocusedContainerColor = UiPalette.Surface
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(UiPalette.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (captchaBitmap != null) {
                        Image(
                            bitmap = captchaBitmap,
                            contentDescription = "图片验证码",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onRefreshCaptcha),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = if (state.isContentLoading) "加载中..." else "点击刷新验证码",
                            color = UiPalette.TextSecondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = onRefreshCaptcha,
                    enabled = !state.isContentLoading,
                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                ) {
                    Text("刷新")
                }
            }
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isActionLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isActionLoading) "注册中..." else "立即注册", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountFindPasswordPane(
    state: AccountUiState,
    onEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSubmit: () -> Unit
) {
    val captchaBitmap = remember(state.findPasswordCaptcha) {
        runCatching {
            state.findPasswordCaptcha?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = state.findPasswordEditor.userName,
            onValueChange = { value -> onEditorChange { it.copy(userName = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("用户名") },
            placeholder = { Text("请输入登录账号") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        OutlinedTextField(
            value = state.findPasswordEditor.question,
            onValueChange = { value -> onEditorChange { it.copy(question = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("密保问题") },
            placeholder = { Text("请输入密保问题") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        OutlinedTextField(
            value = state.findPasswordEditor.answer,
            onValueChange = { value -> onEditorChange { it.copy(answer = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("密保答案") },
            placeholder = { Text("请输入密保答案") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        OutlinedTextField(
            value = state.findPasswordEditor.password,
            onValueChange = { value -> onEditorChange { it.copy(password = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("新密码") },
            placeholder = { Text("请输入新的登录密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        OutlinedTextField(
            value = state.findPasswordEditor.confirmPassword,
            onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("确认新密码") },
            placeholder = { Text("请再次输入新密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiPalette.Accent,
                unfocusedBorderColor = UiPalette.BorderSoft,
                focusedTextColor = UiPalette.Ink,
                unfocusedTextColor = UiPalette.Ink,
                cursorColor = UiPalette.Accent,
                focusedContainerColor = UiPalette.Surface,
                unfocusedContainerColor = UiPalette.Surface
            )
        )
        if (state.findPasswordRequiresVerify) {
            OutlinedTextField(
                value = state.findPasswordEditor.verify,
                onValueChange = { value -> onEditorChange { it.copy(verify = value) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                label = { Text("验证码") },
                placeholder = { Text("请输入图片验证码") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UiPalette.Accent,
                    unfocusedBorderColor = UiPalette.BorderSoft,
                    focusedTextColor = UiPalette.Ink,
                    unfocusedTextColor = UiPalette.Ink,
                    cursorColor = UiPalette.Accent,
                    focusedContainerColor = UiPalette.Surface,
                    unfocusedContainerColor = UiPalette.Surface
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(UiPalette.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (captchaBitmap != null) {
                        Image(
                            bitmap = captchaBitmap,
                            contentDescription = "图片验证码",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onRefreshCaptcha),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = if (state.isContentLoading) "加载中..." else "点击刷新验证码",
                            color = UiPalette.TextSecondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = onRefreshCaptcha,
                    enabled = !state.isContentLoading,
                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                ) {
                    Text("刷新")
                }
            }
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isActionLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isActionLoading) "提交中..." else "立即找回", fontWeight = FontWeight.Bold)
        }
    }
}

private enum class AccountProfileTab {
    Overview,
    Edit
}

@Composable
private fun AccountProfilePaneV2(
    isLoading: Boolean,
    fields: List<Pair<String, String>>,
    editor: UserProfileEditor,
    isSaving: Boolean,
    isEditTab: Boolean,
    onTabChange: (Boolean) -> Unit,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit,
    onSendEmailCode: () -> Unit,
    onBindEmail: () -> Unit,
    onUnbindEmail: () -> Unit
) {
    val selectedTab = if (isEditTab) AccountProfileTab.Edit else AccountProfileTab.Overview

    when {
        isLoading -> LoadingPane("资料加载中...")
        else -> Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(AccountProfileTab.entries.toList()) { tab ->
                        val selected = tab == selectedTab
                        AssistChip(
                            onClick = { onTabChange(tab == AccountProfileTab.Edit) },
                            label = {
                                Text(
                                    text = when (tab) {
                                        AccountProfileTab.Overview -> "基本资料"
                                        AccountProfileTab.Edit -> "修改信息"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) UiPalette.Accent else UiPalette.Surface,
                                labelColor = if (selected) UiPalette.AccentText else UiPalette.Ink
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = if (selected) UiPalette.Accent else UiPalette.BorderSoft,
                                enabled = true
                            )
                        )
                    }
                }

                when (selectedTab) {
                    AccountProfileTab.Overview -> {
                        if (fields.isEmpty()) {
                            Text(
                                text = "暂无资料",
                                color = UiPalette.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            fields.forEach { (label, value) ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(label, color = UiPalette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        value,
                                        color = UiPalette.Ink,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }

                    AccountProfileTab.Edit -> {
                        Text(
                            text = "资料修改",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        ProfileEditorField(
                            label = "QQ号码",
                            value = editor.qq,
                            onValueChange = { value -> onEditorChange { it.copy(qq = value) } }
                        )
                        ProfileEditorField(
                            label = "找回问题",
                            value = editor.question,
                            onValueChange = { value -> onEditorChange { it.copy(question = value) } }
                        )
                        ProfileEditorField(
                            label = "找回答案",
                            value = editor.answer,
                            onValueChange = { value -> onEditorChange { it.copy(answer = value) } }
                        )

                        val hasBoundEmail = editor.email.contains("@") && editor.email.contains(".")
                        if (!hasBoundEmail) {
                            ProfileEditorField(
                                label = "邮箱",
                                value = editor.pendingEmail,
                                onValueChange = { value -> onEditorChange { it.copy(pendingEmail = value) } }
                            )
                            ProfileEditorField(
                                label = "邮箱验证码",
                                value = editor.emailCode,
                                onValueChange = { value -> onEditorChange { it.copy(emailCode = value) } }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = onSendEmailCode,
                                    enabled = !isSaving,
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                                ) {
                                    Text("发送验证码")
                                }
                                Button(
                                    onClick = onBindEmail,
                                    enabled = !isSaving,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UiPalette.Accent,
                                        contentColor = UiPalette.AccentText
                                    )
                                ) {
                                    Text(if (isSaving) "绑定中..." else "确认绑定")
                                }
                            }
                        } else {
                            ReadonlyBindingField(
                                label = "邮箱",
                                value = editor.email,
                                actionText = "已绑定",
                                onAction = null
                            )
                            OutlinedButton(
                                onClick = onUnbindEmail,
                                enabled = !isSaving,
                                border = BorderStroke(1.dp, UiPalette.BorderSoft)
                            ) {
                                Text(if (isSaving) "解绑中..." else "解绑邮箱")
                            }
                        }

                        Text(
                            text = "修改密码",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Text(
                            text = "只在修改密码时填写原密码；不改密码就留空。",
                            color = UiPalette.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        ProfileEditorField(
                            label = "原密码",
                            value = editor.currentPassword,
                            onValueChange = { value -> onEditorChange { it.copy(currentPassword = value) } },
                            password = true
                        )
                        ProfileEditorField(
                            label = "新密码",
                            value = editor.newPassword,
                            onValueChange = { value -> onEditorChange { it.copy(newPassword = value) } },
                            password = true
                        )
                        ProfileEditorField(
                            label = "确认新密码",
                            value = editor.confirmPassword,
                            onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
                            password = true
                        )
                        Button(
                            onClick = onSave,
                            enabled = !isSaving,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.Accent,
                                contentColor = UiPalette.AccentText
                            )
                        ) {
                            Text(if (isSaving) "保存中..." else "保存修改", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountProfilePane(
    isLoading: Boolean,
    fields: List<Pair<String, String>>,
    editor: UserProfileEditor,
    isSaving: Boolean,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit
) {
    when {
        isLoading -> LoadingPane("资料加载中...")
        fields.isEmpty() -> EmptyPane("暂无资料")
        else -> Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                fields.forEach { (label, value) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(label, color = UiPalette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                        Text(
                            value,
                            color = UiPalette.Ink,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "修改资料",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                ProfileEditorField(
                    label = "QQ号码",
                    value = editor.qq,
                    onValueChange = { value -> onEditorChange { it.copy(qq = value) } }
                )
                ProfileEditorField(
                    label = "Email",
                    value = editor.email,
                    onValueChange = { value -> onEditorChange { it.copy(email = value) } }
                )
                ProfileEditorField(
                    label = "手机号",
                    value = editor.phone,
                    onValueChange = { value -> onEditorChange { it.copy(phone = value) } }
                )
                ProfileEditorField(
                    label = "找回问题",
                    value = editor.question,
                    onValueChange = { value -> onEditorChange { it.copy(question = value) } }
                )
                ProfileEditorField(
                    label = "找回答案",
                    value = editor.answer,
                    onValueChange = { value -> onEditorChange { it.copy(answer = value) } }
                )
                ProfileEditorField(
                    label = "当前密码",
                    value = editor.currentPassword,
                    onValueChange = { value -> onEditorChange { it.copy(currentPassword = value) } },
                    password = true
                )
                ProfileEditorField(
                    label = "新密码",
                    value = editor.newPassword,
                    onValueChange = { value -> onEditorChange { it.copy(newPassword = value) } },
                    password = true
                )
                ProfileEditorField(
                    label = "确认新密码",
                    value = editor.confirmPassword,
                    onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
                    password = true
                )
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UiPalette.Accent,
                        contentColor = UiPalette.AccentText
                    )
                ) {
                    Text(if (isSaving) "保存中..." else "保存修改", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = UiPalette.Accent,
            unfocusedBorderColor = UiPalette.BorderSoft,
            focusedTextColor = UiPalette.Ink,
            unfocusedTextColor = UiPalette.Ink,
            cursorColor = UiPalette.Accent,
            focusedContainerColor = UiPalette.Surface,
            unfocusedContainerColor = UiPalette.Surface
        )
    )
}

@Composable
private fun ReadonlyBindingField(
    label: String,
    value: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onAction?.invoke() },
            enabled = onAction != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, UiPalette.BorderSoft),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(label, color = UiPalette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Text(value, color = UiPalette.Ink, style = MaterialTheme.typography.bodyLarge)
                if (!actionText.isNullOrBlank()) {
                    Text(actionText, color = UiPalette.Accent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AccountRecordPane(
    title: String,
    emptyMessage: String,
    isLoading: Boolean,
    items: List<top.jlen.vod.data.UserCenterItem>,
    hasMore: Boolean,
    isActionLoading: Boolean,
    onLoadMore: () -> Unit,
    onPrimaryAction: (top.jlen.vod.data.UserCenterItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    when {
        isLoading && items.isEmpty() -> LoadingPane("$title 加载中...")
        items.isEmpty() -> EmptyPane(emptyMessage)
        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, UiPalette.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClearAll, enabled = !isActionLoading) {
                        Text(if (isActionLoading) "处理中..." else "清空")
                    }
                }
            }

            items.forEach { item ->
                AccountRecordCard(
                    item = item,
                    isActionLoading = isActionLoading,
                    onPrimaryAction = onPrimaryAction,
                    onDelete = onDeleteItem
                )
            }

            LoadMoreFooter(
                hasMore = hasMore,
                isLoading = isLoading && items.isNotEmpty(),
                onLoadMore = onLoadMore
            )
        }
    }
}

@Composable
private fun AccountRecordCard(
    item: top.jlen.vod.data.UserCenterItem,
    isActionLoading: Boolean,
    onPrimaryAction: (top.jlen.vod.data.UserCenterItem) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onPrimaryAction(item) },
                    enabled = !isActionLoading && (item.vodId.isNotBlank() || item.playUrl.isNotBlank()),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft)
                ) {
                    Text(item.actionLabel.ifBlank { "查看详情" })
                }
                TextButton(
                    onClick = { onDelete(item.recordId) },
                    enabled = item.recordId.isNotBlank() && !isActionLoading,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun MembershipPaneV2(
    isLoading: Boolean,
    info: top.jlen.vod.data.MembershipInfo,
    plans: List<MembershipPlan>,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit
) {
    when {
        isLoading && plans.isEmpty() && info.groupName.isBlank() -> LoadingPane("会员信息加载中...")
        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, UiPalette.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("会员信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("当前分组：${info.groupName.ifBlank { "普通用户" }}", color = UiPalette.Ink)
                    Text("剩余积分：${info.points.ifBlank { "--" }}", color = UiPalette.Ink)
                    Text("到期时间：${info.expiry.ifBlank { "--" }}", color = UiPalette.Ink)
                }
            }

            if (plans.isEmpty()) {
                EmptyPane("暂无套餐")
            } else {
                plans.forEach { plan ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, UiPalette.Border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${plan.groupName} ${plan.duration.toMembershipDuration()}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = UiPalette.Ink
                                )
                                Text(
                                    text = "${plan.points} 积分",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiPalette.TextSecondary
                                )
                            }
                            Button(
                                onClick = { onUpgrade(plan) },
                                enabled = !isActionLoading,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UiPalette.Accent,
                                    contentColor = UiPalette.AccentText
                                )
                            ) {
                                Text(if (isActionLoading) "处理中..." else "立即升级")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembershipPane(
    isLoading: Boolean,
    info: top.jlen.vod.data.MembershipInfo,
    plans: List<MembershipPlan>,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit
) {
    when {
        isLoading && plans.isEmpty() && info.groupName.isBlank() -> LoadingPane("会员信息加载中...")
        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, UiPalette.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("会员信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("当前分组：${info.groupName.ifBlank { "未知" }}", color = UiPalette.Ink)
                    Text("剩余积分：${info.points.ifBlank { "未知" }}", color = UiPalette.Ink)
                    Text("到期时间：${info.expiry.ifBlank { "未知" }}", color = UiPalette.Ink)
                }
            }

            if (plans.isEmpty()) {
                EmptyPane("暂无套餐")
            } else {
                plans.forEach { plan ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, UiPalette.Border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${plan.groupName} ${plan.duration.toMembershipDuration()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = UiPalette.Ink
                                )
                                Text(
                                    text = "${plan.points} 积分",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiPalette.TextSecondary
                                )
                            }
                            Button(
                                onClick = { onUpgrade(plan) },
                                enabled = !isActionLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UiPalette.Accent,
                                    contentColor = UiPalette.AccentText
                                )
                            ) {
                                Text(if (isActionLoading) "处理中..." else "升级")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.toMembershipDuration(): String = when (lowercase()) {
    "day" -> "包天"
    "week" -> "包周"
    "month" -> "包月"
    "year" -> "包年"
    else -> this
}

@Composable
private fun HomeTopBlock(
    source: String,
    onRefresh: () -> Unit,
    noticeState: NoticeUiState,
    onRefreshAnnouncements: () -> Unit,
    onOpenAnnouncementList: () -> Unit,
    onOpenAnnouncementDetail: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextSecondary
                )
                Text(
                    text = "Jlen 影视",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleActionButton(icon = Icons.Rounded.NewReleases, onClick = onOpenAnnouncementList)
                CircleActionButton(icon = Icons.Rounded.Refresh, onClick = onRefresh)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SearchDock(onClick = onOpenSearch)
        Spacer(modifier = Modifier.height(14.dp))
        AnnouncementTickerStrip(
            state = noticeState,
            onRefresh = onRefreshAnnouncements,
            onOpenList = onOpenAnnouncementList,
            onOpenDetail = onOpenAnnouncementDetail
        )
    }
}

@Composable
private fun SearchDock(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = UiPalette.Accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "搜影片",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnnouncementTickerStrip(
    state: NoticeUiState,
    onRefresh: () -> Unit,
    onOpenList: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val activeNotices = state.activeNotices

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NewReleases,
                        contentDescription = null,
                        tint = UiPalette.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "公告",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                }
                TextButton(
                    onClick = onOpenList,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("全部公告", fontWeight = FontWeight.Bold)
                        if (state.hasUnreadActiveNotices) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(UiPalette.Accent, CircleShape)
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading && activeNotices.isEmpty() -> {
                    Text(
                        text = "公告加载中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary
                    )
                }

                !state.error.isNullOrBlank() && activeNotices.isEmpty() -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onRefresh() }
                            .background(UiPalette.DangerSurface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.DangerText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "重试",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.DangerText
                        )
                    }
                }

                activeNotices.isEmpty() -> {
                    Text(
                        text = "当前暂无有效公告",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary
                    )
                }

                else -> {
                    val pagerState = rememberPagerState(pageCount = { activeNotices.size })
                    LaunchedEffect(activeNotices.size) {
                        if (activeNotices.size <= 1) return@LaunchedEffect
                        while (true) {
                            delay(3200)
                            val nextPage = (pagerState.currentPage + 1) % activeNotices.size
                            pagerState.animateScrollToPage(nextPage, animationSpec = tween(durationMillis = 600))
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val notice = activeNotices[page]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenDetail(notice.id) }
                                .background(UiPalette.SurfaceSoft)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (notice.isPinned) {
                                Box(
                                    modifier = Modifier
                                        .background(UiPalette.Accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "置顶",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = UiPalette.Accent
                                    )
                                }
                            }
                            Text(
                                text = notice.title,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (notice.title.length > 14) {
                                            Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = UiPalette.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = UiPalette.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleActionButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(UiPalette.Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = UiPalette.Ink)
    }
}

@Composable
fun SectionTitle(
    title: String,
    action: String?,
    icon: @Composable (() -> Unit)? = null,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
        }
        if (!action.isNullOrBlank()) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text(action, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FeaturedCard(item: VodItem, onClick: (String) -> Unit) {
    val badgeText = compactPosterBadgeText(item.badgeText)

    Card(
        modifier = Modifier
            .width(312.dp)
            .clickable { onClick(item.vodId) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
    ) {
        Box {
            AsyncImage(
                model = rememberPosterRequest(
                    data = item.vodPic,
                    width = 960,
                    height = 576
                ),
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xA0000000), Color(0xCC000000))
                        )
                    )
            )
            if (badgeText.isNotBlank()) {
                Text(
                    text = badgeText,
                    color = UiPalette.Surface,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UiPalette.Accent.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Surface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarouselSection(items: List<VodItem>, onOpenDetail: (String) -> Unit) {
    val actualCount = items.size
    val loopItems = remember(items) {
        when {
            items.isEmpty() -> emptyList()
            items.size == 1 -> items
            else -> listOf(items.last()) + items + listOf(items.first())
        }
    }
    val pagerState = rememberPagerState(
        initialPage = if (actualCount > 1) 1 else 0,
        pageCount = { loopItems.size }
    )
    val currentIndex = when {
        actualCount <= 1 -> 0
        pagerState.currentPage <= 0 -> actualCount - 1
        pagerState.currentPage >= loopItems.lastIndex -> 0
        else -> pagerState.currentPage - 1
    }
    val settledIndex = when {
        actualCount <= 1 -> 0
        pagerState.settledPage <= 0 -> actualCount - 1
        pagerState.settledPage >= loopItems.lastIndex -> 0
        else -> pagerState.settledPage - 1
    }

    LaunchedEffect(actualCount, pagerState.settledPage) {
        if (actualCount <= 1) return@LaunchedEffect
        when (pagerState.settledPage) {
            0 -> pagerState.scrollToPage(actualCount)
            loopItems.lastIndex -> pagerState.scrollToPage(1)
        }
    }

    LaunchedEffect(actualCount) {
        if (actualCount <= 1) return@LaunchedEffect
        while (true) {
            delay(UiMotion.CarouselAutoScrollMillis)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(UiMotion.CarouselSlideMillis)
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            userScrollEnabled = actualCount > 1,
            key = { page -> loopItems[page].vodId.ifBlank { "featured_$page" } + "_$page" }
        ) { page ->
            FeaturedCard(
                item = loopItems[page],
                onClick = onOpenDetail
            )
        }

        if (actualCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(actualCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == currentIndex) 18.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == settledIndex || index == currentIndex) UiPalette.Accent
                                else UiPalette.BorderSoft
                            )
                    )
                }
            }
        }
    }
}

private enum class AccountNoticeTone {
    Info,
    Error
}

@Composable
private fun AccountStatusNotice(
    message: String,
    tone: AccountNoticeTone,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val isError = tone == AccountNoticeTone.Error
    val containerColor = if (isError) UiPalette.DangerSurface else UiPalette.AccentSoft.copy(alpha = 0.18f)
    val borderColor = if (isError) UiPalette.DangerBorder else UiPalette.BorderSoft
    val iconTint = if (isError) UiPalette.DangerText else UiPalette.Accent
    val textColor = if (isError) UiPalette.DangerText else UiPalette.Ink
    val icon = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isError) 0.42f else 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = textColor)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InlineEmptyStateCard(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(UiPalette.AccentSoft.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = UiPalette.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.Ink
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PosterGridRow(
    rowItems: List<VodItem>,
    onOpenDetail: (String) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rowItems.forEach { item ->
            CompactPosterCard(
                item = item,
                onClick = onOpenDetail,
                modifier = Modifier.weight(1f)
            )
        }
        repeat(POSTER_GRID_COLUMNS - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadMoreFooter(
    hasMore: Boolean,
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = UiPalette.Accent
            )
            Text(
                text = "加载中",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextMuted
            )
        } else if (hasMore) {
            TextButton(
                onClick = onLoadMore,
                colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
            ) {
                Text("继续加载", fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "到底了",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextMuted
            )
        }
    }
}

@Composable
private fun CompactPosterCard(
    item: VodItem,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeText = compactPosterBadgeText(item.badgeText)

    Column(modifier = modifier.clickable { onClick(item.vodId) }) {
        Box {
            AsyncImage(
                model = rememberPosterRequest(
                    data = item.vodPic,
                    width = 360,
                    height = 540
                ),
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            if (badgeText.isNotBlank()) {
                PosterBadgeText(
                    text = badgeText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xB825252B))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.displayTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = UiPalette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterBadgeText(
    text: String,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isVisibleInWindow by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = UiPalette.Surface,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = if (isVisibleInWindow) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val visibleFrame = Rect()
                view.getWindowVisibleDisplayFrame(visibleFrame)
                val bounds = coordinates.boundsInWindow()
                isVisibleInWindow =
                    bounds.right > visibleFrame.left &&
                        bounds.left < visibleFrame.right &&
                        bounds.bottom > visibleFrame.top &&
                        bounds.top < visibleFrame.bottom
            }
            .then(
                if (isVisibleInWindow && text.length > 8) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                } else {
                    Modifier
                }
            )
    )
}

private val noticeDisplayFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private val AppNotice.formattedActiveTime: String
    get() {
        val start = startAt.formatNoticeTime()
        val end = endAt.formatNoticeTime()
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start - $end"
            start.isNotBlank() -> start
            end.isNotBlank() -> end
            else -> ""
        }
    }

private val AppNotice.formattedPublishTime: String
    get() = createdAt.formatNoticeTime().ifBlank { updatedAt.formatNoticeTime() }

private fun String.formatNoticeTime(): String {
    val raw = trim()
    if (raw.isBlank()) return ""
    val timeMillis = raw.toLongOrNull()?.let { numeric ->
        if (raw.length <= 10) numeric * 1000 else numeric
    } ?: return raw
    return runCatching {
        noticeDisplayFormatter.format(Date(timeMillis))
    }.getOrDefault(raw)
}

private fun isAnnouncementListLine(text: String): Boolean =
    text.startsWith("- ") ||
        text.startsWith("* ") ||
        text.startsWith("•") ||
        Regex("^\\d+[.、]\\s*.+").matches(text)

private fun String.removeAnnouncementListPrefix(): String =
    replaceFirst(Regex("^(-|\\*|•)\\s*"), "")
        .replaceFirst(Regex("^\\d+[.、]\\s*"), "")
        .trim()

private fun compactPosterBadgeText(raw: String): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(Regex("^NO\\s*\\d+[\\d\\s]*"), "")
        .trim()

    val compactEpisodeBadge = when {
        trimmedRankPrefix.matches(Regex("^更新至第\\d{1,4}集?$")) ->
            trimmedRankPrefix.replace(Regex("^更新至第(\\d{1,4})集?$"), "第$1集")
        trimmedRankPrefix.matches(Regex("^更新至\\d{1,4}集?$")) ->
            trimmedRankPrefix.replace(Regex("^更新至(\\d{1,4})集?$"), "第$1集")
        trimmedRankPrefix.matches(Regex("^第\\d{1,4}$")) -> "${trimmedRankPrefix}集"
        else -> trimmedRankPrefix
    }

    return when {
        compactEpisodeBadge.matches(Regex("^[.。·•…-]+$")) -> ""
        compactEpisodeBadge.isBlank() -> ""
        else -> compactEpisodeBadge
    }
}

private fun normalizePosterBadgeText(raw: String): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(Regex("^NO\\s*\\d+[\\d\\s]*"), "")
        .trim()

    val normalizedEpisodeBadge = when {
        trimmedRankPrefix.matches(Regex("^更新至第\\d{1,4}$")) -> "${trimmedRankPrefix}集"
        trimmedRankPrefix.matches(Regex("^第\\d{1,4}$")) -> "${trimmedRankPrefix}集"
        else -> trimmedRankPrefix
    }

    return when {
        normalizedEpisodeBadge.matches(Regex("^[.。·•…-]+$")) -> ""
        normalizedEpisodeBadge.isBlank() -> ""
        else -> normalizedEpisodeBadge
    }
}

private fun sanitizePosterBadge(raw: String): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(Regex("^NO\\s*\\d+[\\d\\s]*"), "")
        .trim()

    return when {
        trimmedRankPrefix.matches(Regex("^[.。·•…-]+$")) -> ""
        trimmedRankPrefix.isBlank() -> ""
        else -> trimmedRankPrefix
    }
}

private fun LazyListState.maxVisiblePosterRowIndex(rowKeyPrefix: String): Int =
    layoutInfo.visibleItemsInfo
        .mapNotNull { itemInfo ->
            val key = itemInfo.key.toString()
            if (!key.startsWith(rowKeyPrefix)) return@mapNotNull null
            key.removePrefix(rowKeyPrefix)
                .substringBefore('-')
                .toIntOrNull()
        }
        .maxOrNull() ?: -1

private fun shouldAutoPreloadRows(
    lastVisibleRowIndex: Int,
    totalRows: Int,
    hasMore: Boolean,
    isLoading: Boolean
): Boolean {
    if (!hasMore || isLoading || totalRows <= 0 || lastVisibleRowIndex < 0) return false
    val triggerRowIndex = (totalRows - GRID_AUTO_PRELOAD_REMAINING_ROWS - 1).coerceAtLeast(0)
    return lastVisibleRowIndex >= triggerRowIndex
}

private const val GRID_AUTO_PRELOAD_REMAINING_ROWS = 6

@Composable
private fun ListCard(item: VodItem, onClick: (String) -> Unit) {
    val detailDescription = item.description
        .takeIf {
            it.isNotBlank() &&
                it != "暂无简介" &&
                it != item.subtitle &&
                it != item.badgeText
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.vodId) },
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = rememberPosterRequest(
                    data = item.vodPic,
                    width = 312,
                    height = 414
                ),
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .size(width = 104.dp, height = 138.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.subtitle.ifBlank { "站内资源" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                detailDescription?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextMuted,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberPosterRequest(
    data: String?,
    width: Int,
    height: Int
): ImageRequest {
    val context = LocalContext.current
    return remember(context, data, width, height) {
        ImageRequest.Builder(context)
            .data(data.orEmpty())
            .size(width, height)
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .allowHardware(true)
            .crossfade(false)
            .diskCacheKey(data.orEmpty())
            .memoryCacheKey("${data.orEmpty()}@$width@$height")
            .build()
    }
}

internal fun VodItem.stableKey(): String = vodId.ifBlank { "$displayTitle|${vodPic.orEmpty()}" }
