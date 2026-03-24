package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import top.jlen.vod.BuildConfig
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem
import androidx.compose.ui.platform.LocalContext

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
                    items(state.slides) { item ->
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
            item {
                PosterGridSection(
                    items = state.hot,
                    onOpenDetail = onOpenDetail
                )
            }
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
    onSearchHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
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
        if (state.history.isNotEmpty()) {
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
                        text = "搜索历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.Ink
                    )
                }
                TextButton(onClick = onClearHistory) {
                    Text("清空")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.history) { keyword ->
                    AssistChip(
                        onClick = { onSearchHistory(keyword) },
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
            Spacer(modifier = Modifier.height(16.dp))
        }
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
    val sessionExpired = state.error?.contains("请先登录") == true ||
        state.error?.contains("登录已失效") == true
    val showLoggedInContent = state.session.isLoggedIn && !sessionExpired

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
                    text = if (showLoggedInContent) {
                        "当前账号已经登录，可以直接继续使用会员相关功能。"
                    } else {
                        "登录后可以同步站内账号状态和后续会员功能。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
            }
        }

        if (!showLoggedInContent) {
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
                        onRetry = onRefresh
                    )
                }
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
                        onRetry = onRefreshSection
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
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                        },
                        onDownloadUpdate = {
                            val targetUrl = state.updateInfo?.downloadUrl
                                ?.takeIf { it.isNotBlank() }
                                ?: state.updateInfo?.releasePageUrl
                                ?.takeIf { it.isNotBlank() }
                                ?: "https://github.com/jinnian0703/JlenVideo/releases"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
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
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                },
                                onDownloadUpdate = {
                                    val targetUrl = state.updateInfo?.downloadUrl
                                        ?.takeIf { it.isNotBlank() }
                                        ?: state.updateInfo?.releasePageUrl
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "https://github.com/jinnian0703/JlenVideo/releases"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
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
                    isUpdateLoading -> "正在检查最新版本..."
                    hasUpdate -> "检测到新版本，可以在这里查看发布说明并下载。"
                    latestVersion.isNotBlank() -> "当前已经是最新版本。"
                    else -> "可以在这里检查更新和查看版本发布说明。"
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
                                text = "暂时没有解析到基础资料。",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarouselSection(items: List<VodItem>, onOpenDetail: (String) -> Unit) {
    val actualCount = items.size
    val pagerState = rememberPagerState(pageCount = { actualCount })
    val currentIndex = pagerState.currentPage
    val settledIndex = pagerState.settledPage

    LaunchedEffect(actualCount, pagerState.settledPage, pagerState.isScrollInProgress) {
        if (actualCount <= 1) return@LaunchedEffect
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        delay(3500)
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        val nextPage = if (pagerState.settledPage >= actualCount - 1) 0 else pagerState.settledPage + 1
        if (nextPage == 0) {
            pagerState.scrollToPage(0)
        } else {
            pagerState.animateScrollToPage(nextPage)
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
            pageSpacing = 12.dp
        ) { page ->
            FeaturedCard(
                item = items[page],
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
