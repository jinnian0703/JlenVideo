package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import top.jlen.vod.data.AppUpdateInfo

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
    var homeScrollToTopSignal by rememberSaveable { mutableStateOf(0) }
    var categoryScrollToTopSignal by rememberSaveable { mutableStateOf(0) }
    var searchScrollToTopSignal by rememberSaveable { mutableStateOf(0) }
    val scrollTopSignals = mapOf(
        "home" to homeScrollToTopSignal,
        "categories" to categoryScrollToTopSignal,
        "search" to searchScrollToTopSignal
    )
    val triggerTopLevelScrollToTop: (String) -> Unit = { route ->
        when (route) {
            "home" -> homeScrollToTopSignal += 1
            "categories" -> categoryScrollToTopSignal += 1
            "search" -> searchScrollToTopSignal += 1
        }
    }
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
    val openAccountSettings: () -> Unit = {
        viewModel.refreshCacheSettings()
        viewModel.refreshCrashLog()
        navController.navigate("account/settings") {
            launchSingleTop = true
        }
    }
    val openAccountSettingsChild: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
        }
    }
    val backToAccountSettings: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate("account/settings") {
                launchSingleTop = true
            }
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
                        onOpenLink = openAnnouncementLink
                    )
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = rootContentInsets,
                    bottomBar = {
                        if (showBottomBar) {
                            AppBottomBar(
                                currentRoute = currentTopLevelRoute.orEmpty(),
                                scrollTopSignals = scrollTopSignals,
                                onNavigate = navigateToTopLevel,
                                onScrollToTop = triggerTopLevelScrollToTop
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
                                onSendFindPasswordCode = viewModel::sendFindPasswordCode,
                                onFindPassword = viewModel::findPassword
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                state = viewModel.homeState,
                                noticeState = viewModel.noticeState,
                                scrollToTopSignal = homeScrollToTopSignal,
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
                                scrollToTopSignal = categoryScrollToTopSignal,
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
                                scrollToTopSignal = searchScrollToTopSignal,
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
                                scrollToTopSignal = searchScrollToTopSignal,
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
                                onRedeemMembershipCard = viewModel::redeemMembershipCard,
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
                                onSendFindPasswordCode = viewModel::sendFindPasswordCode,
                                onFindPassword = viewModel::findPassword,
                                onOpenSettings = openAccountSettings,
                                onSendEmailCode = viewModel::sendEmailBindCode,
                                onBindEmail = viewModel::bindEmail,
                                onUnbindEmail = viewModel::unbindEmail
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
                        composable("account/settings") {
                            LaunchedEffect(Unit) {
                                viewModel.refreshCacheSettings()
                                viewModel.refreshCrashLog()
                            }
                            AccountSettingsHomeScreen(
                                currentVersion = viewModel.accountState.updateInfo?.currentVersion
                                    ?.ifBlank { "--" }
                                    ?: "--",
                                latestVersion = viewModel.accountState.updateInfo?.latestVersion.orEmpty(),
                                hasUpdate = viewModel.accountState.updateInfo?.hasUpdate == true,
                                isUpdateLoading = viewModel.accountState.isUpdateLoading,
                                cacheRetention = viewModel.accountState.cacheRetention,
                                cacheSizeSummary = viewModel.accountState.cacheSizeSummary,
                                isCacheSizeLoading = viewModel.accountState.isCacheSizeLoading,
                                hasCrashLog = viewModel.accountState.hasCrashLog,
                                onBack = { navController.popBackStack() },
                                onOpenUpdate = { openAccountSettingsChild("account/settings/update") },
                                onOpenCache = { openAccountSettingsChild("account/settings/cache") },
                                onOpenAgreement = { openAccountSettingsChild("account/settings/agreement") },
                                onOpenLogs = { openAccountSettingsChild("account/settings/logs") }
                            )
                        }
                        composable("account/settings/update") {
                            AccountUpdateSettingsScreen(
                                currentVersion = viewModel.accountState.updateInfo?.currentVersion
                                    ?.ifBlank { "--" }
                                    ?: "--",
                                latestVersion = viewModel.accountState.updateInfo?.latestVersion.orEmpty(),
                                notes = viewModel.accountState.updateInfo?.notes.orEmpty(),
                                hasUpdate = viewModel.accountState.updateInfo?.hasUpdate == true,
                                isUpdateLoading = viewModel.accountState.isUpdateLoading,
                                onBack = backToAccountSettings,
                                onCheckUpdate = viewModel::checkAppUpdate,
                                onOpenRelease = openReleaseLink,
                                onDownloadUpdate = openUpdateLink
                            )
                        }
                        composable("account/settings/cache") {
                            LaunchedEffect(Unit) {
                                viewModel.refreshCacheSettings()
                            }
                            AccountCacheSettingsScreen(
                                cacheRetention = viewModel.accountState.cacheRetention,
                                cacheSizeSummary = viewModel.accountState.cacheSizeSummary,
                                isCacheSizeLoading = viewModel.accountState.isCacheSizeLoading,
                                isCacheClearing = viewModel.accountState.isCacheClearing,
                                onBack = backToAccountSettings,
                                onRefreshCacheSize = viewModel::refreshCacheSize,
                                onSetCacheRetention = viewModel::setCacheRetention,
                                onClearAppCache = viewModel::clearAppCache
                            )
                        }
                        composable("account/settings/agreement") {
                            AccountAgreementSettingsScreen(onBack = backToAccountSettings)
                        }
                        composable("account/settings/logs") {
                            LaunchedEffect(Unit) {
                                viewModel.refreshCrashLog()
                            }
                            AccountCrashLogSettingsScreen(
                                crashLogText = viewModel.accountState.latestCrashLog,
                                hasCrashLog = viewModel.accountState.hasCrashLog,
                                onBack = backToAccountSettings,
                                onRefreshCrashLog = viewModel::refreshCrashLog,
                                onClearCrashLog = viewModel::clearCrashLog
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
                                onRefreshSources = viewModel::refreshPlayerSources,
                                onPlayNext = viewModel::playNextEpisode,
                                onPlaybackSnapshotChange = viewModel::updatePlaybackSnapshot,
                                onDetectedStream = viewModel::adoptDetectedStream,
                                onResolveFallbackFailed = viewModel::reportTakeoverFailure
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
private fun AppBottomBar(
    currentRoute: String,
    scrollTopSignals: Map<String, Int>,
    onNavigate: (String) -> Unit,
    onScrollToTop: (String) -> Unit
) {
    var lastTapRoute by remember { mutableStateOf("") }
    var lastTapAt by remember { mutableStateOf(0L) }
    NavigationBar(
        containerColor = UiPalette.Surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        bottomBarItems.forEach { (route, label, icon) ->
            val selected = currentRoute == route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    val now = SystemClock.elapsedRealtime()
                    val isDoubleTap = selected &&
                        lastTapRoute == route &&
                        now - lastTapAt <= BOTTOM_BAR_DOUBLE_TAP_MS
                    lastTapRoute = route
                    lastTapAt = now
                    if (isDoubleTap && route in scrollTopSignals) {
                        onScrollToTop(route)
                    } else {
                        onNavigate(route)
                    }
                },
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
                        color = if (selected) UiPalette.Ink else UiPalette.TextMuted
                    )
                }
            )
        }
    }
}

private const val BOTTOM_BAR_DOUBLE_TAP_MS = 450L

