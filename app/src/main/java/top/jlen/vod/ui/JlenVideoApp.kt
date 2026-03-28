package top.jlen.vod.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppUpdateInfo

private val topLevelRoutes = setOf("home", "categories", "search", "account")

private val appBackground = Brush.verticalGradient(
    colors = listOf(UiPalette.HeroEnd, UiPalette.BackgroundTop, UiPalette.BackgroundBottom)
)

private val appColors = lightColorScheme(
    primary = UiPalette.Accent,
    onPrimary = UiPalette.AccentText,
    secondary = UiPalette.AccentSoft,
    onSecondary = UiPalette.AccentText,
    background = UiPalette.BackgroundTop,
    onBackground = UiPalette.TextPrimary,
    surface = UiPalette.Surface,
    onSurface = UiPalette.TextPrimary,
    surfaceVariant = UiPalette.SurfaceStrong,
    onSurfaceVariant = UiPalette.TextSecondary
)

@Composable
fun JlenVideoApp() {
    val viewModel: AppViewModel = viewModel()
    val navController = rememberNavController()
    val context = LocalContext.current
    val portraitPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.uploadPortrait(uri)
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val heartbeatRoute = normalizeHeartbeatRoute(currentRoute)
    val showBottomBar = currentRoute in topLevelRoutes
    val rootContentInsets = if (currentRoute == "player") {
        WindowInsets(0, 0, 0, 0)
    } else {
        WindowInsets.safeDrawing
    }
    val updateInfo = viewModel.accountState.updateInfo
    val noticeDialog = viewModel.noticeState.dialogNotice
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf("") }
    val shouldShowUpdateDialog = updateInfo?.hasUpdate == true &&
        updateInfo.latestVersion.isNotBlank() &&
        dismissedUpdateVersion != updateInfo.latestVersion &&
        noticeDialog == null
    val openReleaseLink: () -> Unit = {
        val targetUrl = updateInfo?.releasePageUrl
            ?.takeIf { it.isNotBlank() }
            ?: "https://github.com/jinnian0703/JlenVideo/releases"
        openExternalUrl(context, targetUrl)
    }
    val openUpdateLink: () -> Unit = {
        val targetUrl = updateInfo?.downloadUrl
            ?.takeIf { it.isNotBlank() }
            ?: updateInfo?.releasePageUrl
            ?.takeIf { it.isNotBlank() }
            ?: "https://github.com/jinnian0703/JlenVideo/releases"
        openExternalUrl(context, targetUrl)
    }
    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openSearchResults: (String) -> Unit = { query ->
        val normalized = query.trim()
        if (normalized.isBlank()) {
            viewModel.updateQuery(normalized)
        } else {
            viewModel.updateQuery(normalized)
            navController.navigate("search/results/${Uri.encode(normalized)}")
        }
    }
    LaunchedEffect(heartbeatRoute, viewModel.accountState.session.userId) {
        viewModel.reportHeartbeat(heartbeatRoute)
        while (true) {
            delay(60_000)
            viewModel.reportHeartbeat(heartbeatRoute)
        }
    }

    MaterialTheme(colorScheme = appColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(modifier = Modifier.fillMaxSize().background(appBackground)) {
                if (noticeDialog != null) {
                    AnnouncementPromptDialog(
                        notice = noticeDialog,
                        onDismiss = viewModel::dismissNoticeDialog,
                        onOpenDetail = {
                            viewModel.markNoticeOpened(noticeDialog.id)
                            navController.navigate("announcement/${Uri.encode(noticeDialog.id)}")
                        }
                    )
                }
                if (shouldShowUpdateDialog) {
                    UpdatePromptDialog(
                        updateInfo = updateInfo ?: AppUpdateInfo(),
                        onDismiss = {
                            dismissedUpdateVersion = updateInfo?.latestVersion.orEmpty()
                        },
                        onOpenRelease = openReleaseLink,
                        onUpdate = {
                            dismissedUpdateVersion = updateInfo?.latestVersion.orEmpty()
                            openUpdateLink()
                        },
                    )
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = rootContentInsets,
                    bottomBar = {
                        if (showBottomBar) {
                            AppBottomBar(
                                currentRoute = currentRoute.orEmpty(),
                                onNavigate = navigateToTopLevel
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = {
                            if (!shouldAnimateRouteTransition(initialState.destination.route, targetState.destination.route)) {
                                EnterTransition.None
                            } else {
                                screenEnterTransition()
                            }
                        },
                        exitTransition = {
                            if (!shouldAnimateRouteTransition(initialState.destination.route, targetState.destination.route)) {
                                ExitTransition.None
                            } else {
                                screenExitTransition()
                            }
                        },
                        popEnterTransition = {
                            if (!shouldAnimateRouteTransition(initialState.destination.route, targetState.destination.route)) {
                                EnterTransition.None
                            } else {
                                screenPopEnterTransition()
                            }
                        },
                        popExitTransition = {
                            if (!shouldAnimateRouteTransition(initialState.destination.route, targetState.destination.route)) {
                                ExitTransition.None
                            } else {
                                screenPopExitTransition()
                            }
                        }
                    ) {
                        composable("home") {
                            HomeScreen(
                                state = viewModel.homeState,
                                noticeState = viewModel.noticeState,
                                onRefresh = { viewModel.refreshHome(forceRefresh = true) },
                                onRefreshAnnouncements = { viewModel.refreshNotices(forceRefresh = true) },
                                onLoadMore = viewModel::loadMoreHome,
                                onOpenDetail = { navController.navigate("detail/$it") },
                                onOpenCategory = { navigateToTopLevel("categories") },
                                onOpenAnnouncementList = { navController.navigate("announcements") },
                                onOpenAnnouncementDetail = { noticeId ->
                                    viewModel.markNoticeOpened(noticeId)
                                    navController.navigate("announcement/${Uri.encode(noticeId)}")
                                },
                                onOpenSearch = { navigateToTopLevel("search") }
                            )
                        }
                        composable("categories") {
                            CategoryScreen(
                                state = viewModel.homeState,
                                onSelectCategory = viewModel::selectCategory,
                                onLoadMore = viewModel::loadMoreCategory,
                                onOpenDetail = { navController.navigate("detail/$it") }
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                state = viewModel.searchState,
                                onQueryChange = viewModel::updateQuery,
                                onOpenSearchResults = openSearchResults,
                                onSearchHistory = viewModel::searchHistory,
                                onClearHistory = viewModel::clearSearchHistory,
                                onLoadHotSearches = viewModel::refreshHotSearches
                            )
                        }
                        composable(
                            route = "search/results/{query}",
                            arguments = listOf(navArgument("query") { type = NavType.StringType })
                        ) { entry ->
                            val query = entry.arguments?.getString("query").orEmpty()
                            LaunchedEffect(query) {
                                viewModel.search(query)
                            }
                            SearchResultsScreen(
                                state = viewModel.searchState,
                                onBack = { navController.popBackStack() },
                                onQueryChange = viewModel::updateQuery,
                                onSearch = {
                                    val normalized = viewModel.searchState.query.trim()
                                    if (normalized == query.trim()) {
                                        viewModel.search()
                                    } else {
                                        openSearchResults(normalized)
                                    }
                                },
                                onOpenDetail = { navController.navigate("detail/$it") }
                            )
                        }
                        composable("account") {
                            AccountScreen(
                                state = viewModel.accountState,
                                onUserNameChange = viewModel::updateLoginUserName,
                                onPasswordChange = viewModel::updateLoginPassword,
                                onLogin = viewModel::login,
                                onLogout = viewModel::logout,
                                onCheckUpdate = viewModel::checkAppUpdate,
                                onSelectSection = viewModel::selectAccountSection,
                                onRefreshSection = viewModel::refreshSelectedAccountSection,
                                onChangePortrait = { portraitPicker.launch("image/*") },
                                onOpenDetail = { navController.navigate("detail/$it") },
                                onOpenHistoryRecord = { item ->
                                    viewModel.resumeHistoryRecord(item)
                                    navController.navigate("player")
                                },
                                onLoadMoreFavorites = viewModel::loadMoreFavorites,
                                onLoadMoreHistory = viewModel::loadMoreHistory,
                                onDeleteFavorite = viewModel::deleteFavorite,
                                onClearFavorites = viewModel::clearFavorites,
                                onDeleteHistory = viewModel::deleteHistory,
                                onClearHistory = viewModel::clearHistory,
                                onUpgradeMembership = viewModel::upgradeMembership,
                                onProfileEditorChange = viewModel::updateProfileEditor,
                                onProfileTabChange = viewModel::setProfileEditTab,
                                onSaveProfile = viewModel::saveProfile,
                                onAuthModeChange = viewModel::setAccountAuthMode,
                                onRegisterEditorChange = viewModel::updateRegisterEditor,
                                onRefreshRegisterCaptcha = viewModel::refreshRegisterCaptcha,
                                onSendRegisterCode = viewModel::sendRegisterCode,
                                onRegister = viewModel::register,
                                onFindPasswordEditorChange = viewModel::updateFindPasswordEditor,
                                onRefreshFindPasswordCaptcha = viewModel::refreshFindPasswordCaptcha,
                                onFindPassword = viewModel::findPassword,
                                onSendEmailCode = viewModel::sendEmailBindCode,
                                onBindEmail = viewModel::bindEmail,
                                onUnbindEmail = viewModel::unbindEmail,
                                onRefreshCrashLog = viewModel::refreshCrashLog,
                                onClearCrashLog = viewModel::clearCrashLog
                            )
                        }
                        composable("announcements") {
                            LaunchedEffect(Unit) {
                                viewModel.refreshNotices()
                            }
                            AnnouncementListScreen(
                                state = viewModel.noticeState,
                                onBack = { navController.popBackStack() },
                                onRefresh = { viewModel.refreshNotices(forceRefresh = true) },
                                onOpenNotice = { noticeId ->
                                    viewModel.markNoticeOpened(noticeId)
                                    navController.navigate("announcement/${Uri.encode(noticeId)}")
                                }
                            )
                        }
                        composable(
                            route = "announcement/{noticeId}",
                            arguments = listOf(navArgument("noticeId") { type = NavType.StringType })
                        ) { entry ->
                            val noticeId = entry.arguments?.getString("noticeId").orEmpty()
                            LaunchedEffect(noticeId) {
                                viewModel.markNoticeOpened(noticeId)
                                if (viewModel.findNotice(noticeId) == null) {
                                    viewModel.refreshNotices(forceRefresh = true)
                                }
                            }
                            AnnouncementDetailScreen(
                                notice = viewModel.findNotice(noticeId),
                                isLoading = viewModel.noticeState.isLoading,
                                onBack = { navController.popBackStack() },
                                onRefresh = { viewModel.refreshNotices(forceRefresh = true) }
                            )
                        }
                        composable(
                            route = "detail/{vodId}",
                            arguments = listOf(navArgument("vodId") { type = NavType.StringType })
                        ) { entry ->
                            val vodId = entry.arguments?.getString("vodId").orEmpty()
                            LaunchedEffect(vodId) {
                                viewModel.loadDetail(vodId)
                            }
                            DetailScreen(
                                state = viewModel.detailState,
                                isLoggedIn = viewModel.accountState.session.isLoggedIn,
                                onBack = { navController.popBackStack() },
                                onSelectSource = viewModel::selectSource,
                                onFavorite = viewModel::addCurrentDetailFavorite,
                                onDismissActionMessage = viewModel::dismissDetailActionMessage,
                                onPlay = { title, sourceIndex, episodeIndex ->
                                    viewModel.openPlayer(
                                        title = title,
                                        item = viewModel.detailState.item,
                                        sources = viewModel.detailState.sources,
                                        sourceIndex = sourceIndex,
                                        episodeIndex = episodeIndex
                                    )
                                    navController.navigate("player")
                                }
                            )
                        }
                        composable("player") {
                            LaunchedEffect(viewModel.playerState.item?.vodId) {
                                viewModel.refreshPlayerSources()
                            }
                            PlayerScreen(
                                state = viewModel.playerState,
                                onBack = { navController.popBackStack() },
                                onSelectEpisode = viewModel::selectPlayerEpisode,
                                onSelectSource = viewModel::selectPlayerSource,
                                onPlayNext = viewModel::playNextEpisode,
                                onPlaybackSnapshotChange = viewModel::updatePlaybackSnapshot
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shouldAnimateRouteTransition(fromRoute: String?, toRoute: String?): Boolean {
    if (fromRoute == toRoute) return false
    val fromTopLevel = fromRoute in topLevelRoutes
    val toTopLevel = toRoute in topLevelRoutes
    return !fromTopLevel && !toTopLevel
}

private fun normalizeHeartbeatRoute(route: String?): String = when {
    route.isNullOrBlank() -> "home"
    route.startsWith("search/results/") || route == "search/results/{query}" -> "search_results"
    route.startsWith("detail/") || route == "detail/{vodId}" -> "detail"
    route.startsWith("announcement/") || route == "announcement/{noticeId}" -> "announcement_detail"
    else -> route
}

@Composable
private fun AnnouncementPromptDialog(
    notice: AppNotice,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                brush = Brush.linearGradient(colors = listOf(UiPalette.Accent, UiPalette.AccentSoft)),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = UiPalette.AccentText
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "公告提醒",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.Ink
                            )
                            if (notice.isPinned) {
                                Box(
                                    modifier = Modifier
                                        .background(UiPalette.AccentSoft.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
                                        .border(1.dp, UiPalette.Accent.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "置顶",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = UiPalette.Accent
                                    )
                                }
                            }
                        }
                        Text(
                            text = notice.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.Ink
                        )
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text(
                        text = notice.displayContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = UiPalette.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("稍后查看")
                    }
                    Button(
                        onClick = onOpenDetail,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.Accent,
                            contentColor = UiPalette.AccentText
                        )
                    ) {
                        Text("查看详情", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatePromptDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
    onUpdate: () -> Unit
) {
    val notes = updateInfo.notes
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(UiPalette.Accent, UiPalette.AccentSoft)
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = UiPalette.AccentText
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "发现新版本",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Text(
                            text = "建议更新到最新版本，获得更好的稳定性和体验。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(y = 2.dp)
                            .background(
                                color = UiPalette.AccentGlow,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .border(1.dp, UiPalette.DangerBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "可更新",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.DangerText
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateVersionPill(
                        modifier = Modifier.weight(1f),
                        label = "当前版本",
                        value = updateInfo.currentVersion.ifBlank { "未知" }
                    )
                    UpdateVersionPill(
                        modifier = Modifier.weight(1f),
                        label = "最新版本",
                        value = updateInfo.latestVersion.ifBlank { "未知" }
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "更新说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.Ink
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (notes.isEmpty()) {
                                Text(
                                    text = "本次版本已发布，建议直接更新体验最新内容。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiPalette.TextSecondary
                                )
                            } else {
                                notes.forEach { note ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = UiPalette.Accent
                                        )
                                        Text(
                                            text = note.removePrefix("•").removePrefix("-").trim(),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = UiPalette.TextPrimary,
                                            maxLines = 8,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "稍后再说",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenRelease,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.width(4.dp))
                            Text(
                                text = "发布页",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = onUpdate,
                            modifier = Modifier
                                .weight(1.25f)
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.Accent,
                                contentColor = UiPalette.AccentText
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.width(4.dp))
                            Text(
                                text = "立即更新",
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateVersionPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = UiPalette.TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val items = listOf(
        Triple("home", "首页", Icons.Rounded.Home),
        Triple("categories", "片库", Icons.Rounded.Category),
        Triple("search", "搜索", Icons.Rounded.Search),
        Triple("account", "我的", Icons.Rounded.Person)
    )

    NavigationBar(
        containerColor = Color(0xF8FFFFFF),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = UiPalette.AccentText,
                    selectedTextColor = UiPalette.Ink,
                    indicatorColor = UiPalette.Accent,
                    unselectedIconColor = UiPalette.TextMuted,
                    unselectedTextColor = UiPalette.TextMuted
                ),
                icon = { Icon(icon, contentDescription = label) },
                label = {
                    Text(
                        text = label,
                        color = if (currentRoute == route) UiPalette.Ink else UiPalette.TextMuted
                    )
                }
            )
        }
    }
}
