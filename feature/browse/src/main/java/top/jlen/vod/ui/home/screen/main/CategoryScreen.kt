package top.jlen.vod.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.CategoryFilterGroup
import top.jlen.vod.data.libraryCategoryOptions
import top.jlen.vod.data.libraryFilterSummary

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CategoryScreen(
    state: HomeUiState,
    scrollToTopSignal: Int = 0,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChange: (Int, Int) -> Unit = { _, _ -> },
    onSelectCategory: (AppleCmsCategory) -> Unit,
    onSelectFilter: (String, String) -> Unit,
    onRetryCategory: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(initialScrollIndex, initialScrollOffset)
    }
    val behaviorSaver = remember {
        listSaver<CategoryHeaderBehavior, Any>(
            save = { listOf(it.expanded, it.hasLeftTop) },
            restore = { CategoryHeaderBehavior(it[0] as Boolean, it[1] as Boolean) }
        )
    }
    var header by rememberSaveable(stateSaver = behaviorSaver) {
        val atTop = initialScrollIndex == 0 && initialScrollOffset == 0
        mutableStateOf(CategoryHeaderBehavior(expanded = atTop, hasLeftTop = !atTop))
    }
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    val scrollConnection = remember(listState, threshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                header = header.onScroll(
                    deltaY = consumed.y,
                    atTop = !listState.canScrollBackward,
                    collapseThreshold = threshold
                )
                return Offset.Zero
            }
        }
    }
    val latestScrollCallback by rememberUpdatedState(onScrollPositionChange)
    val latestLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> latestScrollCallback(index, offset) }
    }
    var handledScrollToTopSignal by rememberSaveable { mutableStateOf(scrollToTopSignal) }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0 && scrollToTopSignal != handledScrollToTopSignal) {
            handledScrollToTopSignal = scrollToTopSignal
            header = CategoryHeaderBehavior()
            listState.animateScrollToItem(0)
        }
    }
    val selectionKey = state.selectedCategory?.typeId.orEmpty() + state.selectedCategoryFilters.toSortedMap().toString()
    var lastSelectionKey by rememberSaveable { mutableStateOf(selectionKey) }
    LaunchedEffect(selectionKey) {
        if (selectionKey != lastSelectionKey) {
            lastSelectionKey = selectionKey
            header = CategoryHeaderBehavior()
            listState.scrollToItem(0)
        }
    }

    val isRefreshing = state.isLoading || state.isCategoryLoading
    var refreshRequested by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) { if (!isRefreshing) refreshRequested = false }
    val pullState = rememberPullRefreshState(
        refreshing = isRefreshing && refreshRequested,
        onRefresh = {
            if (!isRefreshing) {
                refreshRequested = true
                onRetryCategory()
            }
        }
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(UiPalette.BackgroundBottom).statusBarsPadding()
    ) {
        val columns = ((maxWidth - 32.dp) / 120.dp).toInt().coerceIn(3, 8)
        // 横屏或大字体时筛选内容可独立滚动，始终为影片保留空间。
        val filterMaxHeight = (maxHeight * 0.48f).coerceAtLeast(48.dp)
        val rows = remember(state.categoryVideos, state.categoryVisibleCount, columns) {
            state.visibleCategoryVideos.chunked(columns)
        }
        LaunchedEffect(
            listState, rows.size, state.categoryVisibleCount, state.hasMoreCategoryVideos,
            isRefreshing, state.isCategoryAppending, state.categoryAppendError
        ) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged()
                .collect { lastIndex ->
                    if (rows.isNotEmpty() && lastIndex >= (rows.size - 3).coerceAtLeast(0) &&
                        state.hasMoreCategoryVideos && !isRefreshing && !state.isCategoryAppending &&
                        state.categoryAppendError == null
                    ) latestLoadMore()
                }
        }

        Column(Modifier.fillMaxSize()) {
            CategoryFilterHeader(
                state = state,
                expanded = header.expanded,
                maxBodyHeight = filterMaxHeight,
                onToggle = { header = header.toggle(atTop = !listState.canScrollBackward) },
                onSelectCategory = {
                    header = CategoryHeaderBehavior()
                    scope.launch { listState.scrollToItem(0) }
                    onSelectCategory(it)
                },
                onSelectFilter = { key, value ->
                    header = CategoryHeaderBehavior()
                    scope.launch { listState.scrollToItem(0) }
                    onSelectFilter(key, value)
                }
            )
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .pullRefresh(pullState, enabled = !isRefreshing)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.error != null) {
                        item(key = "category_error") {
                            ErrorBanner(message = state.error.orEmpty(), onRetry = onRetryCategory)
                        }
                    }
                    if (rows.isEmpty()) {
                        item(key = "category_empty") {
                            if (isRefreshing) LoadingPane("正在加载内容...", style = FeedbackPaneStyle.Card)
                            else if (state.error == null) InlineEmptyStateCard("暂无内容")
                        }
                    }
                    items(
                        items = rows,
                        key = { row ->
                            "library_" + row.joinToString("|") { item ->
                                val key = item.stableKey()
                                "${key.length}:$key"
                            }
                        },
                        contentType = { "poster_row" }
                    ) { row ->
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { item ->
                                key(item.stableKey()) {
                                    CompactPosterCard(item = item, onClick = onOpenDetail, modifier = Modifier.weight(1f))
                                }
                            }
                            repeat((columns - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (rows.isNotEmpty()) {
                        item(key = "category_footer") {
                            LoadMoreFooter(
                                hasMore = state.hasMoreCategoryVideos,
                                isLoading = state.isCategoryAppending,
                                errorMessage = state.categoryAppendError,
                                onLoadMore = onLoadMore
                            )
                        }
                    }
                }
                PullRefreshIndicator(
                    refreshing = isRefreshing && refreshRequested,
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = UiPalette.Surface,
                    contentColor = UiPalette.Accent
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterHeader(
    state: HomeUiState,
    expanded: Boolean,
    maxBodyHeight: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onSelectCategory: (AppleCmsCategory) -> Unit,
    onSelectFilter: (String, String) -> Unit
) {
    val categories = remember(state.categories) { libraryCategoryOptions(state.categories) }
    val groups = remember(state.selectedCategory, state.categories) { state.categoryFilterGroups }
    val summary = libraryFilterSummary(state.selectedCategory, groups, state.selectedCategoryFilters)
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "category_arrow")
    Surface(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = UiPalette.Surface,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "收起分类筛选" else "展开分类筛选", onClick = onToggle)
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.GridView, contentDescription = null, tint = UiPalette.Accent, modifier = Modifier.size(22.dp))
                Text(
                    text = summary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(if (expanded) "收起" else "筛选", style = MaterialTheme.typography.labelLarge, color = UiPalette.TextSecondary)
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = UiPalette.TextSecondary, modifier = Modifier.rotate(arrowRotation))
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = maxBodyHeight)
                        .verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it.typeId }, contentType = { "library_category" }) { category ->
                            LibraryFilterChip(
                                text = category.typeName,
                                selected = category.typeId == state.selectedCategory?.typeId,
                                onClick = { onSelectCategory(category) }
                            )
                        }
                    }
                    groups.forEach { group ->
                        key(group.key) {
                            CategoryFilterRow(group, state.selectedCategoryFilters[group.key].orEmpty(), onSelectFilter)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(group: CategoryFilterGroup, selected: String, onSelect: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            group.label,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = UiPalette.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "all") {
                LibraryFilterChip("全部", selected.isBlank()) { onSelect(group.key, "") }
            }
            items(group.options, key = { "option_$it" }) { option ->
                LibraryFilterChip(option, selected == option) { onSelect(group.key, option) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = UiPalette.SurfaceSoft,
            labelColor = UiPalette.Ink,
            selectedContainerColor = UiPalette.Ink,
            selectedLabelColor = UiPalette.Surface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = UiPalette.Border,
            selectedBorderColor = UiPalette.Ink
        )
    )
}
