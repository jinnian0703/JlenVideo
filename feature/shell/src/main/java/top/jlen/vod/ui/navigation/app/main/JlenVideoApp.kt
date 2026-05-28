package top.jlen.vod.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import java.io.File
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import top.jlen.vod.RuntimeEndpoints
import top.jlen.vod.data.AppUpdateInfo
import top.jlen.vod.data.VodItem

private const val ONBOARDING_PREFS = "jlen_video_onboarding"
private const val KEY_ACCEPTED_USER_AGREEMENT = "accepted_user_agreement"
private const val KEY_COMPLETED_FIRST_LOGIN_PROMPT = "completed_first_login_prompt"

private const val ROUTE_ONBOARDING_AGREEMENT = "onboarding/agreement"
private const val ROUTE_ONBOARDING_LOGIN = "onboarding/login"
private const val TRAFFIC_CAPTURE_CHECK_MS = 2_000L

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
    val context = LocalContext.current
    val activity = context.findActivity()
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
    var trafficCaptureState by remember(context) {
        mutableStateOf(detectTrafficCaptureState(context))
    }
    LaunchedEffect(context) {
        while (true) {
            delay(TRAFFIC_CAPTURE_CHECK_MS)
            trafficCaptureState = detectTrafficCaptureState(context)
        }
    }
    if (trafficCaptureState.blocked) {
        MaterialTheme(colorScheme = appColors) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                Box(modifier = Modifier.fillMaxSize().background(appBackground)) {
                    TrafficCaptureBlockedScreen(
                        state = trafficCaptureState,
                        onRetry = { trafficCaptureState = detectTrafficCaptureState(context) },
                        onExit = { activity?.finish() }
                    )
                }
            }
        }
        return
    }
    val viewModel: AppViewModel = viewModel()
    val navController = rememberNavController()
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
    var homeScrollIndex by rememberSaveable { mutableStateOf(0) }
    var homeScrollOffset by rememberSaveable { mutableStateOf(0) }
    var categoryScrollIndex by rememberSaveable { mutableStateOf(0) }
    var categoryScrollOffset by rememberSaveable { mutableStateOf(0) }
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
    LaunchedEffect(heartbeatRoute, heartbeatPlaybackKey) {
        viewModel.recordIssueRoute(heartbeatPlaybackKey)
    }
    val accountToastMessage = viewModel.accountState.toastMessage
    val accountToastSerial = viewModel.accountState.toastSerial
    LaunchedEffect(currentRoute, accountToastSerial) {
        if (!accountToastMessage.isNullOrBlank()) {
            if (isAccountRoute(currentRoute)) {
                Toast.makeText(context, accountToastMessage, Toast.LENGTH_SHORT).show()
            }
            viewModel.consumeAccountToast()
        }
    }
    val showBottomBar = currentTopLevelRoute != null &&
        !isSearchResultsRoute(currentRoute) &&
        !isAccountSettingsDetailRoute(currentRoute)
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
            ?: RuntimeEndpoints.githubReleasesUrl
        openExternalUrl(context, targetUrl)
    }
    val openUpdateLink: () -> Unit = {
        val targetUrl = updateInfo?.downloadUrl
            ?.takeIf { it.isNotBlank() }
            ?: updateInfo?.releasePageUrl
            ?.takeIf { it.isNotBlank() }
            ?: RuntimeEndpoints.githubReleasesUrl
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
    val openAccountSettingsChild: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
        }
    }
    val backToAccount: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate("account") {
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
                                initialScrollIndex = homeScrollIndex,
                                initialScrollOffset = homeScrollOffset,
                                onScrollPositionChange = { index, offset ->
                                    homeScrollIndex = index
                                    homeScrollOffset = offset
                                },
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
                                initialScrollIndex = categoryScrollIndex,
                                initialScrollOffset = categoryScrollOffset,
                                onScrollPositionChange = { index, offset ->
                                    categoryScrollIndex = index
                                    categoryScrollOffset = offset
                                },
                                onSelectCategory = viewModel::selectCategory,
                                onSelectFilter = viewModel::updateCategoryFilter,
                                onRetryCategory = { viewModel.refreshCategoryTab(forceRefresh = true) },
                                onLoadMore = viewModel::loadMoreCategory,
                                onOpenDetail = { navController.navigate("detail/$it") }
                            )
                        }
                        composable("search") {
                            LaunchedEffect(Unit) {
                                viewModel.refreshHotSearches()
                            }
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
                                viewModel.showCachedFollowContent()
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
                                onOpenSettingsUpdate = { openAccountSettingsChild("account/settings/update") },
                                onOpenSettingsCache = { openAccountSettingsChild("account/settings/cache") },
                                onOpenSettingsLogs = { openAccountSettingsChild("account/settings/logs") },
                                onOpenSettingsAbout = { openAccountSettingsChild("account/settings/about") },
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
                        composable("account/settings/update") {
                            AccountUpdateSettingsScreen(
                                currentVersion = viewModel.accountState.updateInfo?.currentVersion
                                    ?.ifBlank { "--" }
                                    ?: "--",
                                latestVersion = viewModel.accountState.updateInfo?.latestVersion.orEmpty(),
                                notes = viewModel.accountState.updateInfo?.notes.orEmpty(),
                                hasUpdate = viewModel.accountState.updateInfo?.hasUpdate == true,
                                isUpdateLoading = viewModel.accountState.isUpdateLoading,
                                onBack = backToAccount,
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
                                cacheSizeLimit = viewModel.accountState.cacheSizeLimit,
                                cacheSizeSummary = viewModel.accountState.cacheSizeSummary,
                                isCacheSizeLoading = viewModel.accountState.isCacheSizeLoading,
                                isCacheClearing = viewModel.accountState.isCacheClearing,
                                onBack = backToAccount,
                                onRefreshCacheSize = viewModel::refreshCacheSize,
                                onSetCacheRetention = viewModel::setCacheRetention,
                                onSetCacheSizeLimit = viewModel::setCacheSizeLimit,
                                onClearAppCache = viewModel::clearAppCache
                            )
                        }
                        composable("account/settings/agreement") {
                            AccountAgreementSettingsScreen(onBack = backToAccount)
                        }
                        composable("account/settings/about") {
                            AccountAboutSettingsScreen(
                                currentVersion = viewModel.accountState.updateInfo?.currentVersion?.ifBlank { "--" } ?: "--",
                                onBack = backToAccount,
                                onOpenUpdate = { openAccountSettingsChild("account/settings/update") },
                                onOpenLogs = { openAccountSettingsChild("account/settings/logs") },
                                onOpenAgreement = { openAccountSettingsChild("account/settings/agreement") },
                                onOpenUrl = { url -> openExternalUrl(context, url) }
                            )
                        }
                        composable("account/settings/logs") {
                            LaunchedEffect(Unit) {
                                viewModel.refreshCrashLog()
                            }
                            AccountCrashLogSettingsScreen(
                                crashLogText = viewModel.accountState.latestCrashLog,
                                issueLogEntries = viewModel.accountState.issueLogEntries,
                                hasCrashLog = viewModel.accountState.hasCrashLog,
                                onBack = backToAccount,
                                onRefreshCrashLog = viewModel::refreshCrashLog,
                                onClearCrashLog = viewModel::clearCrashLog,
                                onOpenIssueLog = { issueLog ->
                                    navController.navigate("account/settings/logs/detail/${Uri.encode(issueLog.id)}")
                                },
                                onReadIssueLog = viewModel::readIssueLog,
                                onDeleteIssueLog = viewModel::deleteIssueLog
                            )
                        }
                        composable(
                            route = "account/settings/logs/detail/{logId}",
                            arguments = listOf(navArgument("logId") { type = NavType.StringType })
                        ) { entry ->
                            val logId = entry.arguments?.getString("logId").orEmpty()
                            val issueLog = viewModel.accountState.issueLogEntries.firstOrNull { it.id == logId }
                            val logText = remember(logId, viewModel.accountState.latestCrashLog) {
                                viewModel.readIssueLog(logId)
                            }
                            if (issueLog == null) {
                                LaunchedEffect(logId) {
                                    viewModel.refreshCrashLog()
                                }
                            }
                            AccountIssueLogDetailScreen(
                                entry = issueLog ?: AccountIssueLogEntry(
                                    id = logId,
                                    title = "问题日志",
                                    time = "",
                                    summary = ""
                                ),
                                logText = logText,
                                onBack = { navController.popBackStack() },
                                onCopy = { copyTextToClipboard(context, "issue_log", logText, "问题日志已复制") },
                                onShare = { shareIssueLogText(context, logId, issueLog?.title ?: "问题日志", logText) },
                                onDelete = {
                                    viewModel.deleteIssueLog(logId)
                                    navController.popBackStack()
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
                            val routeDetailState = viewModel.detailState.takeIf { state ->
                                state.item?.matchesDetailRoute(vodId) == true
                            } ?: DetailUiState(isLoading = true)
                            DetailScreen(
                                state = routeDetailState,
                                isLoggedIn = viewModel.accountState.session.isLoggedIn,
                                onBack = { navController.popBackStack() },
                                onSelectSource = viewModel::selectSource,
                                onFavorite = {
                                    if (
                                        viewModel.accountState.session.isLoggedIn &&
                                        routeDetailState.isFavorited
                                    ) {
                                        showRemoveFavoriteDialog = true
                                    } else if (routeDetailState.item != null) {
                                        viewModel.addCurrentDetailFavorite()
                                    }
                                },
                                onDismissActionMessage = viewModel::dismissDetailActionMessage,
                                onPlay = { title, sourceIndex, episodeIndex ->
                                    val pendingResume = routeDetailState.pendingResumePlayback
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
                                        item = routeDetailState.item,
                                        sources = routeDetailState.sources,
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

private data class TrafficCaptureState(
    val blocked: Boolean,
    val reason: String
)

private fun detectTrafficCaptureState(context: Context): TrafficCaptureState {
    val reasons = buildList {
        if (hasSystemProxy(context) || hasJvmProxy()) {
            add("当前网络已启用代理")
        }
        if (hasVpnNetwork(context) || hasVpnInterface()) {
            add("当前网络存在 VPN 通道")
        }
        if (hasHookRuntime()) {
            add("检测到运行环境异常")
        }
    }
    return TrafficCaptureState(
        blocked = reasons.isNotEmpty(),
        reason = reasons.joinToString("、")
    )
}

private fun hasSystemProxy(context: Context): Boolean {
    val resolver = context.contentResolver
    val proxy = Settings.Global.getString(resolver, Settings.Global.HTTP_PROXY)
        ?.trim()
        .orEmpty()
    return proxy.isNotBlank()
}

private fun hasJvmProxy(): Boolean {
    val proxyKeys = arrayOf(
        "http.proxyHost",
        "https.proxyHost",
        "socksProxyHost"
    )
    return proxyKeys.any { key -> !System.getProperty(key).isNullOrBlank() }
}

private fun hasVpnNetwork(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    return connectivityManager.allNetworks.any { network ->
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}

private fun hasHookRuntime(): Boolean =
    hasHookClass() || hasHookStackTrace() || hasHookRuntimeMap()

private fun hasHookClass(): Boolean {
    val classNames = arrayOf(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XposedHelpers",
        "de.robv.android.xposed.IXposedHookLoadPackage",
        "org.lsposed.lspd.impl.LSPosedBridge",
        "org.lsposed.hiddenapibypass.HiddenApiBypass",
        "com.elderdrivers.riru.edxp.core.Yahfa",
        "com.swift.sandhook.SandHook",
        "com.saurik.substrate.MS",
        "re.frida.server.Frida"
    )
    val loader = Thread.currentThread().contextClassLoader
    return classNames.any { className ->
        runCatching { Class.forName(className, false, loader) }.isSuccess
    }
}

private fun hasHookStackTrace(): Boolean {
    val keywords = arrayOf(
        "xposed",
        "lsposed",
        "edxp",
        "riru",
        "zygisk",
        "frida",
        "substrate",
        "sandhook",
        "justtrustme"
    )
    return Throwable().stackTrace.any { element ->
        val text = "${element.className}.${element.methodName}".lowercase()
        keywords.any(text::contains)
    }
}

private fun hasHookRuntimeMap(): Boolean {
    val keywords = arrayOf(
        "xposed",
        "lsposed",
        "lspd",
        "edxp",
        "riru",
        "zygisk",
        "frida",
        "substrate",
        "sandhook",
        "justtrustme",
        "sslunpin",
        "trustme"
    )
    return runCatching {
        File("/proc/self/maps").useLines { lines ->
            lines.take(3500).any { line ->
                val lower = line.lowercase()
                keywords.any(lower::contains)
            }
        }
    }.getOrDefault(false)
}

private fun hasVpnInterface(): Boolean {
    val vpnPrefixes = arrayOf("tun", "tap", "ppp", "wg", "utun", "ipsec")
    return runCatching {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.any { networkInterface ->
                networkInterface.isUp && vpnPrefixes.any { prefix ->
                    networkInterface.name.startsWith(prefix, ignoreCase = true)
                }
            } == true
    }.getOrDefault(false)
}

@Composable
private fun TrafficCaptureBlockedScreen(
    state: TrafficCaptureState,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(UiPalette.DangerSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = UiPalette.DangerText,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "网络环境不可用",
                    color = UiPalette.Ink,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "请关闭代理、VPN 或抓包工具后继续使用。",
                    color = UiPalette.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                if (state.reason.isNotBlank()) {
                    Text(
                        text = state.reason,
                        color = UiPalette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UiPalette.Accent,
                        contentColor = UiPalette.AccentText
                    )
                ) {
                    Text("重新检测", fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.TextButton(onClick = onExit) {
                    Text("退出应用", color = UiPalette.TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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

private fun isSearchResultsRoute(route: String?): Boolean =
    route?.startsWith("search/results/") == true || route == "search/results/{query}"

private fun isAccountSettingsDetailRoute(route: String?): Boolean =
    route?.startsWith("account/settings/") == true

private fun isAccountRoute(route: String?): Boolean =
    route == "account" || route == ROUTE_ONBOARDING_LOGIN || route?.startsWith("account/") == true

private fun normalizeHeartbeatRoute(route: String?): String = when {
    route.isNullOrBlank() -> "home"
    isSearchResultsRoute(route) -> "search_results"
    route.startsWith("detail/") || route == "detail/{vodId}" -> "detail"
    route.startsWith("announcement/") || route == "announcement/{noticeId}" -> "announcement_detail"
    else -> route
}

private fun VodItem.matchesDetailRoute(vodId: String): Boolean {
    val normalizedId = vodId.trim()
    if (normalizedId.isBlank()) return false
    return linkedSetOf(
        this.vodId.trim(),
        this.siteVodId.trim(),
        Regex("""/voddetail/([^/.]+)""").find(detailUrl)?.groupValues?.getOrNull(1).orEmpty(),
        Regex("""/vodplay/([^/-?.]+)""").find(detailUrl)?.groupValues?.getOrNull(1).orEmpty()
    ).any { it.isNotBlank() && it == normalizedId }
}

private fun copyTextToClipboard(context: Context, label: String, text: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

private fun shareIssueLogText(context: Context, logId: String, title: String, text: String) {
    runCatching {
        val safeName = logId.ifBlank { "issue_log" }.removeSuffix(".txt")
        val dir = File(context.cacheDir, "shared_logs").apply { mkdirs() }
        val file = File(dir, "$safeName.txt")
        file.writeText(text, StandardCharsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享问题日志"))
    }.onFailure {
        Toast.makeText(context, "分享日志失败", Toast.LENGTH_SHORT).show()
    }
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

