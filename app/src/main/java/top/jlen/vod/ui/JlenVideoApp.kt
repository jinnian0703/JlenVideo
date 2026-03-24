package top.jlen.vod.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

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
    val showBottomBar = currentRoute in setOf("home", "categories", "search", "account")
    val updateInfo = viewModel.accountState.updateInfo
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf("") }
    val shouldShowUpdateDialog = updateInfo?.hasUpdate == true &&
        updateInfo.latestVersion.isNotBlank() &&
        dismissedUpdateVersion != updateInfo.latestVersion
    val openUpdateLink: () -> Unit = {
        val targetUrl = updateInfo?.downloadUrl
            ?.takeIf { it.isNotBlank() }
            ?: updateInfo?.releasePageUrl
            ?.takeIf { it.isNotBlank() }
            ?: "https://github.com/jinnian0703/JlenVideo/releases"
        openExternalUrl(context, targetUrl)
    }

    MaterialTheme(colorScheme = appColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(modifier = Modifier.fillMaxSize().background(appBackground)) {
                if (shouldShowUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            dismissedUpdateVersion = updateInfo?.latestVersion.orEmpty()
                        },
                        title = {
                            Text("发现新版本")
                        },
                        text = {
                            Text(
                                buildString {
                                    append("当前版本：")
                                    append(updateInfo?.currentVersion.orEmpty().ifBlank { "未知" })
                                    append("\n最新版本：")
                                    append(updateInfo?.latestVersion.orEmpty())
                                    updateInfo?.notes
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let {
                                            append("\n\n更新说明：\n")
                                            append(it)
                                        }
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dismissedUpdateVersion = updateInfo?.latestVersion.orEmpty()
                                    openUpdateLink()
                                }
                            ) {
                                Text("立即更新")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    dismissedUpdateVersion = updateInfo?.latestVersion.orEmpty()
                                }
                            ) {
                                Text("稍后再说")
                            }
                        }
                    )
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.statusBars,
                    bottomBar = {
                        if (showBottomBar) {
                            AppBottomBar(
                                currentRoute = currentRoute.orEmpty(),
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                state = viewModel.homeState,
                                onRefresh = viewModel::refreshHome,
                                onLoadMore = viewModel::loadMoreHome,
                                onOpenDetail = { navController.navigate("detail/$it") },
                                onOpenCategory = { navController.navigate("categories") },
                                onOpenSearch = { navController.navigate("search") }
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
                                onSearch = viewModel::search,
                                onSearchHistory = viewModel::searchHistory,
                                onClearHistory = viewModel::clearSearchHistory,
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
                                onRefresh = viewModel::refreshAccount,
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
                                onPlaybackSnapshotChange = viewModel::updatePlaybackSnapshot,
                                onFullscreenResult = viewModel::syncFromFullscreen
                            )
                        }
                    }
                }
            }
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
