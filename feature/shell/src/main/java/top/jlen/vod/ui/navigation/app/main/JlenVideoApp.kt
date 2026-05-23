package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Bookmark
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
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
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.RegisterEditor

private const val ONBOARDING_PREFS = "jlen_video_onboarding"
private const val KEY_ACCEPTED_USER_AGREEMENT = "accepted_user_agreement"
private const val KEY_COMPLETED_FIRST_LOGIN_PROMPT = "completed_first_login_prompt"

private const val ROUTE_ONBOARDING_AGREEMENT = "onboarding/agreement"
private const val ROUTE_ONBOARDING_LOGIN = "onboarding/login"

private val topLevelRoutes = setOf("home", "categories", "follow", "search", "account")

private val bottomBarItems = listOf(
    Triple("home", "首页", Icons.Rounded.Home),
    Triple("categories", "片库", Icons.Rounded.Category),
    Triple("follow", "追剧", Icons.Rounded.Bookmark),
    Triple("search", "搜索", Icons.Rounded.Search),
    Triple("account", "我的", Icons.Rounded.Person)
)

@Composable
fun JlenVideoApp() {
    val isDarkTheme = isSystemInDarkTheme()
    val activity = LocalContext.current.findActivity()
    SideEffect {
        UiPalette.syncWithSystem(isDarkTheme)
        activity?.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
    val appBackground = remember(isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(UiPalette.HeroEnd, UiPalette.BackgroundTop, UiPalette.BackgroundBottom)
        )
    }
    val appColors = remember(isDarkTheme) {
        if (isDarkTheme) {
            darkColorScheme(
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
        } else {
            lightColorScheme(
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
        }
    }
    val viewModel: AppViewModel = viewModel()
    val navController = rememberNavController()
    val context = LocalContext.current
    val onboardingPrefs = remember(context) {
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
    }
    var hasAcceptedUserAgreement by rememberSaveable {
        mutableStateOf(onboardingPrefs.getBoolean(KEY_ACCEPTED_USER_AGREEMENT, false))
    }
    var hasCompletedFirstLoginPrompt by rememberSaveable {
        mutableStateOf(onboardingPrefs.getBoolean(KEY_COMPLETED_FIRST_LOGIN_PROMPT, false))
    }
    val startDestination = rememberSaveable {
        when {
            !hasAcceptedUserAgreement -> ROUTE_ONBOARDING_AGREEMENT
            !hasCompletedFirstLoginPrompt -> ROUTE_ONBOARDING_LOGIN
            else -> "home"
        }
    }
    val acceptAgreement: () -> Unit = {
        onboardingPrefs.edit().putBoolean(KEY_ACCEPTED_USER_AGREEMENT, true).apply()
        hasAcceptedUserAgreement = true
        navController.navigate(ROUTE_ONBOARDING_LOGIN) {
            popUpTo(ROUTE_ONBOARDING_AGREEMENT) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }
    val completeFirstLoginPrompt: (String) -> Unit = { route ->
        onboardingPrefs.edit().putBoolean(KEY_COMPLETED_FIRST_LOGIN_PROMPT, true).apply()
        hasCompletedFirstLoginPrompt = true
        navController.navigate(route) {
            popUpTo(ROUTE_ONBOARDING_LOGIN) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }
    val portraitPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.uploadPortrait(uri)
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTopLevelRoute = normalizeTopLevelRoute(currentRoute)
    var pendingTopLevelRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val heartbeatRoute = normalizeHeartbeatRoute(currentRoute)
    val heartbeatPlaybackKey = if (heartbeatRoute == "player") {
        listOf(
            viewModel.playerState.item?.siteVodId,
            viewModel.playerState.item?.vodId,
            viewModel.playerState.selectedSourceIndex,
            viewModel.playerState.selectedEpisodeIndex
        ).joinToString("|")
    } else {
        heartbeatRoute
    }
    val showBottomBar = currentTopLevelRoute != null
    val rootContentInsets = WindowInsets(0, 0, 0, 0)
    val updateInfo = viewModel.accountState.updateInfo
    val noticeDialog = viewModel.noticeState.dialogNotice
    val canShowGlobalDialogs = currentTopLevelRoute != null
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf("") }
    val shouldShowUpdateDialog = canShowGlobalDialogs &&
        updateInfo?.hasUpdate == true &&
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
    val openAnnouncementLink: (String) -> Unit = { url ->
        openExternalUrl(context, url.normalizeAnnouncementUrl(), "无法打开公告链接")
    }
    val navigateToTopLevel: (String) -> Unit = { route ->
        if (currentTopLevelRoute != route && pendingTopLevelRoute != route) {
            pendingTopLevelRoute = route
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    LaunchedEffect(currentTopLevelRoute, pendingTopLevelRoute) {
        if (pendingTopLevelRoute != null && pendingTopLevelRoute == currentTopLevelRoute) {
            pendingTopLevelRoute = null
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
    LaunchedEffect(heartbeatRoute, heartbeatPlaybackKey, viewModel.accountState.session.userId) {
        viewModel.reportHeartbeat(heartbeatRoute)
        while (true) {
            delay(60_000)
            viewModel.reportHeartbeat(heartbeatRoute)
        }
    }

    MaterialTheme(colorScheme = appColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(modifier = Modifier.fillMaxSize().background(appBackground)) {
                if (canShowGlobalDialogs && noticeDialog != null) {
                    AnnouncementPromptDialog(
                        notice = noticeDialog,
                        onDismiss = viewModel::dismissNoticeDialog,
                        onOpenLink = openAnnouncementLink,
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
                                currentRoute = currentTopLevelRoute.orEmpty(),
                                onNavigate = navigateToTopLevel
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable(ROUTE_ONBOARDING_AGREEMENT) {
                            UserAgreementOnboardingScreen(
                                onAccept = acceptAgreement,
                                onExit = { activity?.finish() }
                            )
                        }
                        composable(ROUTE_ONBOARDING_LOGIN) {
                            LaunchedEffect(viewModel.accountState.session.isLoggedIn) {
                                if (viewModel.accountState.session.isLoggedIn) {
                                    completeFirstLoginPrompt("home")
                                }
                            }
                            FirstLoginOnboardingScreen(
                                state = viewModel.accountState,
                                onUserNameChange = viewModel::updateLoginUserName,
                                onPasswordChange = viewModel::updateLoginPassword,
                                onLogin = viewModel::login,
                                onSkip = { completeFirstLoginPrompt("home") },
                                onAuthModeChange = viewModel::setAccountAuthMode,
                                onRegisterEditorChange = viewModel::updateRegisterEditor,
                                onRefreshRegisterCaptcha = viewModel::refreshRegisterCaptcha,
                                onSendRegisterCode = viewModel::sendRegisterCode,
                                onRegister = viewModel::register,
                                onFindPasswordEditorChange = viewModel::updateFindPasswordEditor,
                                onRefreshFindPasswordCaptcha = viewModel::refreshFindPasswordCaptcha,
                                onFindPassword = viewModel::findPassword
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                state = viewModel.homeState,
                                noticeState = viewModel.noticeState,
                                onRefresh = viewModel::refreshHomeAndClearCaches,
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
                                onSelectFilter = viewModel::updateCategoryFilter,
                                onRetryCategory = { viewModel.refreshCategoryTab(forceRefresh = true) },
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
                        composable("follow") {
                            LaunchedEffect(Unit) {
                                viewModel.refreshFollowContent()
                            }
                            LaunchedEffect(
                                viewModel.accountState.session.isLoggedIn,
                                viewModel.accountState.favoriteItems,
                                viewModel.accountState.historyItems
                            ) {
                                viewModel.rebuildFollowContent()
                            }
                            FollowScreen(
                                state = viewModel.followState,
                                onRefresh = { viewModel.refreshFollowContent(forceRefresh = true) },
                                onOpenDetail = { navController.navigate("detail/$it") },
                                onOpenAccount = { navigateToTopLevel("account") },
                                onOpenLibrary = { navigateToTopLevel("categories") }
                            )
                        }
                        composable(
                            route = "search/results/{query}",
                            arguments = listOf(navArgument("query") { type = NavType.StringType })
                        ) { entry ->
                            val query = entry.arguments?.getString("query").orEmpty()
                            val scrollPosition = viewModel.getSearchResultScroll(query)
                            LaunchedEffect(query) {
                                viewModel.ensureSearchResults(query)
                            }
                            SearchResultsScreen(
                                state = viewModel.searchState,
                                resultKey = query.trim(),
                                initialScrollIndex = scrollPosition.index,
                                initialScrollOffset = scrollPosition.offset,
                                onScrollPositionChange = { index, offset ->
                                    viewModel.updateSearchResultScroll(query, index, offset)
                                },
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
                                onPickSuggestion = { keyword ->
                                    viewModel.searchHistory(keyword)
                                    openSearchResults(keyword)
                                },
                                onLoadMore = viewModel::loadMoreSearchResults,
                                onOpenDetail = { navController.navigate("detail/$it") }
                            )
                        }
                        composable("account") {
                            LaunchedEffect(Unit) {
                                viewModel.ensureAccountScreenReady()
                            }
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
                                onOpenHistoryRecord = { item ->
                                    viewModel.resumeHistoryRecord(item)
                                    navController.navigate("player")
                                },
                                onOpenFollow = { navigateToTopLevel("follow") },
                                onLoadMoreHistory = viewModel::loadMoreHistory,
                                onDeleteHistory = viewModel::deleteHistory,
                                onClearHistory = viewModel::clearHistory,
                                onUpgradeMembership = viewModel::upgradeMembership,
                                onSignInMembership = viewModel::signInMembership,
                                onOpenPointLogs = { navController.navigate("account/points") },
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
                        composable("account/points") {
                            AccountPointLogScreen(
                                pointLogs = viewModel.accountState.membershipPointLogs,
                                onBack = { navController.popBackStack() }
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
                                onRefresh = { viewModel.refreshNotices(forceRefresh = true) },
                                onOpenLink = openAnnouncementLink
                            )
                        }
                        composable(
                            route = "detail/{vodId}",
                            arguments = listOf(navArgument("vodId") { type = NavType.StringType })
                        ) { entry ->
                            val vodId = entry.arguments?.getString("vodId").orEmpty()
                            var showRemoveFavoriteDialog by remember(vodId) { mutableStateOf(false) }
                            LaunchedEffect(vodId) {
                                viewModel.loadDetail(vodId)
                            }
                            DetailScreen(
                                state = viewModel.detailState,
                                isLoggedIn = viewModel.accountState.session.isLoggedIn,
                                onBack = { navController.popBackStack() },
                                onSelectSource = viewModel::selectSource,
                                onFavorite = {
                                    if (
                                        viewModel.accountState.session.isLoggedIn &&
                                        viewModel.detailState.isFavorited
                                    ) {
                                        showRemoveFavoriteDialog = true
                                    } else {
                                        viewModel.addCurrentDetailFavorite()
                                    }
                                },
                                onDismissActionMessage = viewModel::dismissDetailActionMessage,
                                onPlay = { title, sourceIndex, episodeIndex ->
                                    val pendingResume = viewModel.detailState.pendingResumePlayback
                                    val resumeSnapshot = if (
                                        pendingResume != null &&
                                        pendingResume.sourceIndex == sourceIndex &&
                                        pendingResume.episodeIndex == episodeIndex
                                    ) {
                                        PlaybackSnapshot(
                                            positionMs = pendingResume.positionMs,
                                            speed = pendingResume.speed
                                        )
                                    } else {
                                        PlaybackSnapshot()
                                    }
                                    viewModel.openPlayer(
                                        title = title,
                                        item = viewModel.detailState.item,
                                        sources = viewModel.detailState.sources,
                                        sourceIndex = sourceIndex,
                                        episodeIndex = episodeIndex,
                                        snapshot = resumeSnapshot
                                    )
                                    navController.navigate("player")
                                }
                            )
                            if (showRemoveFavoriteDialog) {
                                FollowRemoveConfirmDialog(
                                    onDismiss = { showRemoveFavoriteDialog = false },
                                    onConfirm = {
                                        showRemoveFavoriteDialog = false
                                        viewModel.cancelCurrentDetailFavorite()
                                    }
                                )
                            }
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

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun normalizeTopLevelRoute(route: String?): String? = when {
    route == null -> null
    route == "home" || route.startsWith("home/") -> "home"
    route == "categories" || route.startsWith("categories/") -> "categories"
    route == "follow" || route.startsWith("follow/") -> "follow"
    route == "search" || route.startsWith("search/") -> "search"
    route == "account" || route.startsWith("account/") -> "account"
    else -> null
}

private fun normalizeHeartbeatRoute(route: String?): String = when {
    route.isNullOrBlank() -> "home"
    route.startsWith("search/results/") || route == "search/results/{query}" -> "search_results"
    route.startsWith("detail/") || route == "detail/{vodId}" -> "detail"
    route.startsWith("announcement/") || route == "announcement/{noticeId}" -> "announcement_detail"
    else -> route
}

@Composable
private fun UserAgreementOnboardingScreen(
    onAccept: () -> Unit,
    onExit: () -> Unit
) {
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    if (showExitConfirm) {
        Dialog(
            onDismissRequest = { showExitConfirm = false },
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(UiPalette.DangerSurface, RoundedCornerShape(UiDimens.ControlRadius))
                                .border(1.dp, UiPalette.DangerBorder, RoundedCornerShape(UiDimens.ControlRadius)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.DangerText
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "确定不同意并退出？",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.Ink
                            )
                            Text(
                                text = "协议是首次使用前的必要确认",
                                style = MaterialTheme.typography.bodySmall,
                                color = UiPalette.TextSecondary
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
                        shape = RoundedCornerShape(UiDimens.ControlRadius)
                    ) {
                        Text(
                            text = "不同意用户协议与隐私说明将无法继续使用 JlenVideo。你可以返回继续阅读，或确定退出应用。",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showExitConfirm = false },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = UiDimens.SecondaryButtonHeight),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.TextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("返回阅读", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onExit,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = UiDimens.SecondaryButtonHeight),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.DangerText,
                                contentColor = UiPalette.Surface
                            )
                        ) {
                            Text("确定退出", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiDimens.PagePadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "用户协议与隐私说明",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = "请先阅读并同意以下内容，再继续使用 JlenVideo。",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AgreementSection(
                    title = "应用用途",
                    body = "JlenVideo 是视频浏览与播放客户端，用于展示站点提供的影视信息、搜索结果、播放记录和追剧内容。"
                )
                AgreementSection(
                    title = "内容与播放",
                    body = "应用本身不生产影视内容，列表、详情和播放地址来自当前配置站点。请在合法合规的前提下使用相关内容。"
                )
                AgreementSection(
                    title = "账号与数据",
                    body = "登录后会使用站点账号能力同步资料、会员积分、追剧和播放记录；本地会保存必要的引导状态、搜索历史、播放进度和问题日志。"
                )
                AgreementSection(
                    title = "隐私与日志",
                    body = "崩溃日志仅用于排查运行问题，通常保存在本机。请不要在问题日志中主动填写敏感账号、密码或验证码。"
                )
                AgreementSection(
                    title = "合规使用",
                    body = "继续使用即表示你承诺遵守所在地法律法规、站点规则和内容版权要求，并自行承担因不当使用产生的责任。"
                )
            }
        }

        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiDimens.PrimaryButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text("同意并继续", fontWeight = FontWeight.ExtraBold)
        }
        TextButton(
            onClick = { showExitConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "不同意",
                fontWeight = FontWeight.Bold,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun AgreementSection(
    title: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = UiPalette.Ink
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = UiPalette.TextPrimary
        )
    }
}

@Composable
private fun FirstLoginOnboardingScreen(
    state: AccountUiState,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSkip: () -> Unit,
    onAuthModeChange: (AccountAuthMode) -> Unit,
    onRegisterEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshRegisterCaptcha: () -> Unit,
    onSendRegisterCode: () -> Unit,
    onRegister: () -> Unit,
    onFindPasswordEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onRefreshFindPasswordCaptcha: () -> Unit,
    onFindPassword: () -> Unit
) {
    val title = when (state.authMode) {
        AccountAuthMode.Register -> "注册账号"
        AccountAuthMode.FindPassword -> "找回密码"
        else -> "登录账号"
    }
    val subtitle = when (state.authMode) {
        AccountAuthMode.Register -> "创建账号后，可继续在这里登录并完成首次引导。"
        AccountAuthMode.FindPassword -> "重置密码后，可返回登录并继续进入应用。"
        else -> "登录后可同步追剧、播放记录和会员积分，也可以先跳过进入首页。"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.error?.takeIf { it.isNotBlank() }?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = UiPalette.DangerSurface),
                        shape = RoundedCornerShape(UiDimens.ControlRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.DangerBorder)
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.DangerText
                        )
                    }
                }

                when (state.authMode) {
                    AccountAuthMode.Register -> FirstLoginRegisterPane(
                        state = state,
                        onEditorChange = onRegisterEditorChange,
                        onRefreshCaptcha = onRefreshRegisterCaptcha,
                        onSendCode = onSendRegisterCode,
                        onSubmit = onRegister
                    )

                    AccountAuthMode.FindPassword -> FirstLoginFindPasswordPane(
                        state = state,
                        onEditorChange = onFindPasswordEditorChange,
                        onRefreshCaptcha = onRefreshFindPasswordCaptcha,
                        onSubmit = onFindPassword
                    )

                    else -> FirstLoginLoginPane(
                        state = state,
                        onUserNameChange = onUserNameChange,
                        onPasswordChange = onPasswordChange,
                        onLogin = onLogin
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.authMode == AccountAuthMode.Login) {
                        OutlinedButton(
                            onClick = { onAuthModeChange(AccountAuthMode.Register) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("注册账号", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onAuthModeChange(AccountAuthMode.FindPassword) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("找回密码", maxLines = 1)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAuthModeChange(AccountAuthMode.Login) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("返回登录", maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "跳过，先逛逛",
                fontWeight = FontWeight.Bold,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun FirstLoginLoginPane(
    state: AccountUiState,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingTextField(
            value = state.userName,
            onValueChange = onUserNameChange,
            label = "用户名",
            placeholder = "请输入站内用户名"
        )
        OnboardingTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "密码",
            placeholder = "请输入密码",
            isPassword = true
        )

        Button(
            onClick = onLogin,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiDimens.PrimaryButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isLoading) "正在登录..." else "立即登录", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun FirstLoginRegisterPane(
    state: AccountUiState,
    onEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingTextField(
            value = state.registerEditor.userName,
            onValueChange = { value -> onEditorChange { it.copy(userName = value) } },
            label = "用户名",
            placeholder = "请输入注册用户名"
        )
        OnboardingTextField(
            value = state.registerEditor.password,
            onValueChange = { value -> onEditorChange { it.copy(password = value) } },
            label = "密码",
            placeholder = "请输入注册密码",
            isPassword = true
        )
        OnboardingTextField(
            value = state.registerEditor.confirmPassword,
            onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
            label = "确认密码",
            placeholder = "请再次输入密码",
            isPassword = true
        )
        OnboardingTextField(
            value = state.registerEditor.contact,
            onValueChange = { value -> onEditorChange { it.copy(contact = value) } },
            label = state.registerContactLabel,
            placeholder = "请输入${state.registerContactLabel}",
            keyboardType = if (state.registerChannel == "phone") KeyboardType.Phone else KeyboardType.Email
        )
        if (state.registerRequiresCode) {
            OnboardingTextField(
                value = state.registerEditor.code,
                onValueChange = { value -> onEditorChange { it.copy(code = value) } },
                label = state.registerCodeLabel,
                placeholder = "请输入${state.registerCodeLabel}"
            )
            OutlinedButton(
                onClick = onSendCode,
                enabled = !state.isActionLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
            ) {
                Text(if (state.isActionLoading) "发送中..." else "发送${state.registerCodeLabel}")
            }
        }
        if (state.registerRequiresVerify) {
            OnboardingTextField(
                value = state.registerEditor.verify,
                onValueChange = { value -> onEditorChange { it.copy(verify = value) } },
                label = "图片验证码",
                placeholder = "请输入图片验证码"
            )
            OnboardingCaptchaBox(
                bytes = state.registerCaptcha,
                isLoading = state.isContentLoading,
                onRefresh = onRefreshCaptcha
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isActionLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isActionLoading) "注册中..." else "立即注册", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun FirstLoginFindPasswordPane(
    state: AccountUiState,
    onEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingTextField(
            value = state.findPasswordEditor.userName,
            onValueChange = { value -> onEditorChange { it.copy(userName = value) } },
            label = "用户名",
            placeholder = "请输入登录账号"
        )
        OnboardingTextField(
            value = state.findPasswordEditor.question,
            onValueChange = { value -> onEditorChange { it.copy(question = value) } },
            label = "密保问题",
            placeholder = "请输入密保问题"
        )
        OnboardingTextField(
            value = state.findPasswordEditor.answer,
            onValueChange = { value -> onEditorChange { it.copy(answer = value) } },
            label = "密保答案",
            placeholder = "请输入密保答案"
        )
        OnboardingTextField(
            value = state.findPasswordEditor.password,
            onValueChange = { value -> onEditorChange { it.copy(password = value) } },
            label = "新密码",
            placeholder = "请输入新的登录密码",
            isPassword = true
        )
        OnboardingTextField(
            value = state.findPasswordEditor.confirmPassword,
            onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
            label = "确认新密码",
            placeholder = "请再次输入新密码",
            isPassword = true
        )
        if (state.findPasswordRequiresVerify) {
            OnboardingTextField(
                value = state.findPasswordEditor.verify,
                onValueChange = { value -> onEditorChange { it.copy(verify = value) } },
                label = "验证码",
                placeholder = "请输入图片验证码"
            )
            OnboardingCaptchaBox(
                bytes = state.findPasswordCaptcha,
                isLoading = state.isContentLoading,
                onRefresh = onRefreshCaptcha
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isActionLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isActionLoading) "提交中..." else "立即找回", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = UiPalette.Accent,
            unfocusedBorderColor = UiPalette.BorderSoft,
            focusedTextColor = UiPalette.Ink,
            unfocusedTextColor = UiPalette.Ink,
            cursorColor = UiPalette.Accent,
            focusedContainerColor = UiPalette.SurfaceSoft,
            unfocusedContainerColor = UiPalette.SurfaceSoft
        )
    )
}

@Composable
private fun OnboardingCaptchaBox(
    bytes: ByteArray?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val captchaBitmap = remember(bytes) {
        runCatching {
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }.getOrNull()
    }

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
                .background(UiPalette.SurfaceSoft)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            if (captchaBitmap != null) {
                Image(
                    bitmap = captchaBitmap,
                    contentDescription = "图片验证码",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = if (isLoading) "加载中..." else "点击刷新验证码",
                    color = UiPalette.TextSecondary
                )
            }
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
        ) {
            Text("刷新")
        }
    }
}

@Suppress("unused")
@Composable
private fun RemoveFavoriteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(UiPalette.DangerSurface, RoundedCornerShape(999.dp))
                            .border(1.dp, UiPalette.DangerBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "已追剧",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = UiPalette.DangerText
                        )
                    }
                    Text(
                        text = "取消追剧",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = "当前影片已加入追剧，确认将其从追剧列表中移除吗？",
                        style = MaterialTheme.typography.bodyLarge,
                        color = UiPalette.TextSecondary
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "执行后，该影片将从“追剧”中移除，播放记录不会受到影响。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.TextPrimary)
                    ) {
                        Text("先保留", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .width(122.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.DangerText,
                            contentColor = UiPalette.Surface
                        )
                    ) {
                        Text("确认移除", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRemoveConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(UiPalette.DangerSurface.copy(alpha = 0.68f), RoundedCornerShape(999.dp))
                        .border(1.dp, UiPalette.DangerBorder.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "已追剧",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.DangerText
                    )
                }
                Text(
                    text = "取消追剧",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "确认把这部影片从追剧列表中移除吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.76f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "移除后不会影响播放记录和续播进度。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.36f),
                            contentColor = UiPalette.TextPrimary
                        )
                    ) {
                        Text("先保留", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.width(122.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.DangerText,
                            contentColor = UiPalette.Surface
                        )
                    ) {
                        Text("确认移除", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementPromptDialog(
    notice: AppNotice,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        AnnouncementRichContent(
                            notice = notice,
                            onOpenLink = onOpenLink
                        )
                    }
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
                            text = "建议更新到最新版本，获得更好的稳定性和使用体验。",
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
    NavigationBar(
        containerColor = UiPalette.Surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        bottomBarItems.forEach { (route, label, icon) ->
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

