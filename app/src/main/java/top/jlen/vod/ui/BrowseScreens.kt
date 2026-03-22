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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.GridView
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.jlen.vod.BuildConfig
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.VodItem

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenSearch: () -> Unit
) {
    if (state.isLoading) {
        LoadingPane("首页加载中...")
        return
    }

    val listState = rememberLazyListState()

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
                onOpenCategory = onOpenCategory,
                onOpenSearch = onOpenSearch
            )
        }
        state.error?.let { message ->
            item { ErrorBanner(message = message, onRetry = onRefresh) }
        }
        if (state.featured.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "推荐",
                    action = "刷新",
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = UiPalette.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onAction = onRefresh
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.featured) { item ->
                        FeaturedCard(item = item, onClick = onOpenDetail)
                    }
                }
            }
        }
        item {
            SectionTitle(
                title = "片库更新",
                action = "进入片库",
                onAction = onOpenCategory
            )
        }
        if (state.latest.isEmpty()) {
            item {
                ErrorBanner(
                    message = "首页暂时没有内容，点刷新再试试。",
                    onRetry = onRefresh
                )
            }
        } else {
            item {
                PosterGridSection(
                    items = state.visibleLatest,
                    onOpenDetail = onOpenDetail
                )
            }
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
    val listState = rememberLazyListState()

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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "默认先显示全部分类，下方可以切换其他分类。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.categories) { category ->
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
                state.categoryVideos.isEmpty() -> EmptyPane("这个分类暂时没有内容")
                else -> PosterGridSection(
                    items = state.visibleCategoryVideos,
                    onOpenDetail = onOpenDetail
                )
            }
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
    onSearch: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = UiPalette.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "直接搜索站内影片、剧集、综艺和动漫",
            style = MaterialTheme.typography.bodyMedium,
            color = UiPalette.TextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
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
            placeholder = { Text("输入影片名称开始搜索") },
            trailingIcon = {
                TextButton(
                    onClick = onSearch,
                    colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                ) {
                    Text("搜索", fontWeight = FontWeight.Bold)
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        when {
            state.isLoading -> LoadingPane("搜索中...")
            !state.error.isNullOrBlank() && state.results.isEmpty() -> ErrorBanner(message = state.error, onRetry = onSearch)
            state.results.isEmpty() -> EmptyPane("搜索结果会显示在这里")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.results) { item ->
                    ListCard(item = item, onClick = onOpenDetail)
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
    onRefresh: () -> Unit,
    onSelectSection: (AccountSection) -> Unit,
    onRefreshSection: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onClearFavorites: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onUpgradeMembership: (MembershipPlan) -> Unit
) {
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.session.isLoggedIn) {
                        "当前账号已经登录，可以直接继续使用会员相关功能。"
                    } else {
                        "登录后可以同步站内账号状态和后续会员功能。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
            }
        }

        state.message?.let { message ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.AccentSoft.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        color = UiPalette.Ink,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        state.error?.let { message ->
            item {
                ErrorBanner(
                    message = message,
                    onRetry = if (state.session.isLoggedIn) onRefreshSection else onRefresh
                )
            }
        }

        if (state.session.isLoggedIn) {
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
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(UiPalette.Accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.session.userName.take(1).ifBlank { "我" },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = UiPalette.Accent
                                    )
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
                    items(AccountSection.entries.toList()) { section ->
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

            item {
                when (state.selectedSection) {
                    AccountSection.Profile -> AccountProfilePane(
                        isLoading = state.isContentLoading,
                        fields = state.profileFields
                    )
                    AccountSection.Favorites -> AccountRecordPane(
                        title = "我的收藏",
                        emptyMessage = "你还没有收藏任何内容",
                        isLoading = state.isContentLoading,
                        items = state.favoriteItems,
                        hasMore = !state.favoriteNextPageUrl.isNullOrBlank(),
                        isActionLoading = state.isActionLoading,
                        onLoadMore = onLoadMoreFavorites,
                        onOpenDetail = onOpenDetail,
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
                        onOpenDetail = onOpenDetail,
                        onDeleteItem = onDeleteHistory,
                        onClearAll = onClearHistory
                    )
                    AccountSection.Member -> MembershipPane(
                        isLoading = state.isContentLoading,
                        info = state.membershipInfo,
                        plans = state.membershipPlans,
                        isActionLoading = state.isActionLoading,
                        onUpgrade = onUpgradeMembership
                    )
                }
            }
        } else {
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

@Composable
private fun AccountProfilePane(
    isLoading: Boolean,
    fields: List<Pair<String, String>>
) {
    when {
        isLoading -> LoadingPane("资料加载中...")
        fields.isEmpty() -> EmptyPane("暂时没有资料信息")
        else -> Card(
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
                fields.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, color = UiPalette.TextSecondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(value, color = UiPalette.Ink, fontWeight = FontWeight.SemiBold)
                    }
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
    onOpenDetail: (String) -> Unit,
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
                    onOpenDetail = onOpenDetail,
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
    onOpenDetail: (String) -> Unit,
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
                    color = UiPalette.TextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (item.vodId.isNotBlank()) onOpenDetail(item.vodId) },
                    enabled = item.vodId.isNotBlank() && !isActionLoading,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("会员信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("当前分组：${info.groupName.ifBlank { "未知" }}", color = UiPalette.Ink)
                    Text("剩余积分：${info.points.ifBlank { "未知" }}", color = UiPalette.Ink)
                    Text("到期时间：${info.expiry.ifBlank { "未知" }}", color = UiPalette.Ink)
                }
            }

            if (plans.isEmpty()) {
                EmptyPane("当前没有可用的升级方案")
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
    onOpenCategory: () -> Unit,
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
                CircleActionButton(icon = Icons.Rounded.GridView, onClick = onOpenCategory)
                CircleActionButton(icon = Icons.Rounded.Refresh, onClick = onRefresh)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SearchDock(onClick = onOpenSearch)
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
                text = "搜索影片、剧集、综艺、动漫",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
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
    Card(
        modifier = Modifier
            .width(312.dp)
            .clickable { onClick(item.vodId) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
    ) {
        Box {
            AsyncImage(
                model = item.vodPic,
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
            if (item.badgeText.isNotBlank()) {
                Text(
                    text = item.badgeText,
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.subtitle.ifBlank { "站内资源" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PosterGridSection(items: List<VodItem>, onOpenDetail: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    CompactPosterCard(
                        item = item,
                        onClick = onOpenDetail,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
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
                text = "正在加载...",
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
                text = "已经全部加载完了",
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
    Column(modifier = modifier.clickable { onClick(item.vodId) }) {
        Box {
            AsyncImage(
                model = item.vodPic,
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            if (item.badgeText.isNotBlank()) {
                Text(
                    text = item.badgeText,
                    color = UiPalette.Surface,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.subtitle.ifBlank { "站内资源" },
            style = MaterialTheme.typography.bodySmall,
            color = UiPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListCard(item: VodItem, onClick: (String) -> Unit) {
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
                model = item.vodPic,
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
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
