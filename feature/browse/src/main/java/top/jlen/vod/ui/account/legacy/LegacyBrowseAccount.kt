package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Whatshot
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
import androidx.compose.ui.window.Dialog
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.jlen.vod.AppConfig
import top.jlen.vod.PLAYER_DESKTOP_UA
import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.CacheRetentionOption
import top.jlen.vod.data.CacheSizeSummary
import top.jlen.vod.data.CategoryFilterGroup
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.HotSearchGroup
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.MembershipSignInInfo
import top.jlen.vod.data.PersistentCookieJar
import top.jlen.vod.data.PointLogItem
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem
import top.jlen.vod.data.formatCacheSize
import top.jlen.vod.data.sanitizeUserFacingComposite


@Composable
internal fun LegacyAccountScreen(
    state: AccountUiState,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSelectSection: (AccountSection) -> Unit,
    onChangePortrait: () -> Unit,
    onOpenHistoryRecord: (top.jlen.vod.data.UserCenterItem) -> Unit,
    onOpenFollow: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onUpgradeMembership: (MembershipPlan) -> Unit,
    onRedeemMembershipCard: (String, String) -> Unit,
    onSignInMembership: () -> Unit,
    onOpenPointLogs: () -> Unit,
    onProfileEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onProfileTabChange: (Boolean) -> Unit,
    onSaveProfile: () -> Unit,
    onAuthModeChange: (AccountAuthMode) -> Unit,
    onRegisterEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshRegisterCaptcha: () -> Unit,
    onSendRegisterCode: () -> Unit,
    onRegister: () -> Unit,
    onFindPasswordEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onSendFindPasswordCode: () -> Unit,
    onFindPassword: () -> Unit,
    onOpenSettingsUpdate: () -> Unit,
    onOpenSettingsCache: () -> Unit,
    onOpenSettingsAgreement: () -> Unit,
    onOpenSettingsLogs: () -> Unit,
    onSendEmailCode: () -> Unit,
    onBindEmail: () -> Unit,
    onUnbindEmail: () -> Unit
) {
    val showLoggedInContent = state.session.isLoggedIn
    val visibleSections = remember {
        listOf(
            AccountSection.Overview,
            AccountSection.Profile,
            AccountSection.History,
            AccountSection.Member,
            AccountSection.About
        )
    }

    LaunchedEffect(showLoggedInContent, state.selectedSection) {
        if (showLoggedInContent) {
            when (state.selectedSection) {
                AccountSection.Favorites -> onSelectSection(AccountSection.Overview)
                else -> Unit
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
    ) {
        item(key = "account_title", contentType = "account_header") {
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
            item(key = "account_signed_in_header", contentType = "account_header") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                    shape = RoundedCornerShape(28.dp),
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
                                AuthenticatedAvatar(
                                    imageUrl = state.session.portraitUrl,
                                    contentDescription = state.session.userName,
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onChangePortrait),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
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

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
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
                                onClick = onSignInMembership,
                                enabled = !state.isActionLoading && !state.membershipSignInInfo.signedToday,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.72f),
                                    contentColor = UiPalette.Accent,
                                    disabledContainerColor = UiPalette.SurfaceStrong,
                                    disabledContentColor = UiPalette.TextPrimary
                                )
                            ) {
                                Text(
                                    when {
                                        state.isActionLoading -> "处理中..."
                                        state.membershipSignInInfo.signedToday -> "今日已签"
                                        else -> "立即签到"
                                    }
                                )
                            }
                            Button(
                                onClick = onLogout,
                                enabled = !state.isLoading,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
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

            item(key = "account_section_tabs", contentType = "account_tabs") {
                AccountSegmentBar {
                    visibleSections.forEach { section ->
                        AccountUnderlineTab(
                            text = when (section) {
                                AccountSection.Overview -> "总览"
                                AccountSection.Profile -> "资料"
                                AccountSection.History -> "记录"
                                AccountSection.Member -> "会员"
                                AccountSection.About -> "设置"
                                AccountSection.Favorites -> ""
                            },
                            selected = state.selectedSection == section,
                            onClick = { onSelectSection(section) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            when (state.selectedSection) {
                AccountSection.History -> accountRecordPaneItems(
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
                else -> item(
                    key = "account_section_${state.selectedSection.name}",
                    contentType = "account_section"
                ) {
                    when (state.selectedSection) {
                        AccountSection.Overview -> AccountOverviewPane(
                            state = state,
                            isActionLoading = state.isActionLoading,
                            onEditProfile = {
                                onSelectSection(AccountSection.Profile)
                                onProfileTabChange(true)
                            },
                            onBindEmail = {
                                onSelectSection(AccountSection.Profile)
                                onProfileTabChange(true)
                            },
                            onSignIn = onSignInMembership,
                            onOpenPointLogs = onOpenPointLogs,
                            onOpenFollow = onOpenFollow,
                            onOpenLogs = { onSelectSection(AccountSection.About) }
                        )
                        AccountSection.Profile -> AccountProfilePaneV2(
                            isLoading = state.isContentLoading,
                            fields = state.profileFields,
                            editor = state.profileEditor,
                            isSaving = state.isActionLoading,
                            isEditTab = state.isProfileEditTab,
                            emailBindCodeCountdown = state.emailBindCodeCountdown,
                            onTabChange = onProfileTabChange,
                            onEditorChange = onProfileEditorChange,
                            onSave = onSaveProfile,
                            onSendEmailCode = onSendEmailCode,
                            onBindEmail = onBindEmail,
                            onUnbindEmail = onUnbindEmail
                        )
                        AccountSection.Favorites -> Unit
                        AccountSection.History -> Unit
                        AccountSection.Member -> MembershipPaneV2(
                            isLoading = state.isContentLoading,
                            info = state.membershipInfo,
                            plans = state.membershipPlans,
                            signInInfo = state.membershipSignInInfo,
                            pointLogs = state.membershipPointLogs,
                            isActionLoading = state.isActionLoading,
                            message = state.message,
                            onUpgrade = onUpgradeMembership,
                            onRedeemCard = onRedeemMembershipCard,
                            onSignIn = onSignInMembership,
                            onOpenPointLogs = onOpenPointLogs
                        )
                        AccountSection.About -> AccountSettingsHomePane(
                            currentVersion = state.updateInfo?.currentVersion?.ifBlank { "--" } ?: "--",
                            latestVersion = state.updateInfo?.latestVersion.orEmpty(),
                            hasUpdate = state.updateInfo?.hasUpdate == true,
                            isUpdateLoading = state.isUpdateLoading,
                            cacheRetention = state.cacheRetention,
                            cacheSizeSummary = state.cacheSizeSummary,
                            isCacheSizeLoading = state.isCacheSizeLoading,
                            hasCrashLog = state.hasCrashLog,
                            onOpenUpdate = onOpenSettingsUpdate,
                            onOpenCache = onOpenSettingsCache,
                            onOpenAgreement = onOpenSettingsAgreement,
                            onOpenLogs = onOpenSettingsLogs
                        )
                    }
                }
            }
        } else {
            item(key = "account_guest_intro", contentType = "account_guest_intro") {
                AccountGuestIntroCard()
            }

            item(
                key = "account_guest_auth_${state.authMode.name}",
                contentType = "account_guest_auth"
            ) {
                when (state.authMode) {
                    AccountAuthMode.Register -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, UiPalette.Border)
                        ) {
                            Column {
                                AccountGuestModeHeader(
                                    title = "注册账号",
                                    description = "填写账号信息并完成验证。",
                                    onBack = { onAuthModeChange(AccountAuthMode.Login) }
                                )
                                AccountRegisterPane(
                                    state = state,
                                    onEditorChange = onRegisterEditorChange,
                                    onRefreshCaptcha = onRefreshRegisterCaptcha,
                                    onSendCode = onSendRegisterCode,
                                    onSubmit = onRegister
                                )
                            }
                        }
                    }

                    AccountAuthMode.FindPassword -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, UiPalette.Border)
                        ) {
                            Column {
                                AccountGuestModeHeader(
                                    title = "找回密码",
                                    description = "通过邮箱验证码重置登录密码。",
                                    onBack = { onAuthModeChange(AccountAuthMode.Login) }
                                )
                                AccountFindPasswordPane(
                                    state = state,
                                    onEditorChange = onFindPasswordEditorChange,
                                    onSendCode = onSendFindPasswordCode,
                                    onSubmit = onFindPassword
                                )
                            }
                        }
                    }

                    AccountAuthMode.About -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, UiPalette.Border)
                        ) {
                            Column {
                                AccountGuestModeHeader(
                                    title = "设置与工具",
                                    description = "查看版本、缓存、协议和问题日志。",
                                    onBack = { onAuthModeChange(AccountAuthMode.Login) }
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 18.dp)
                                ) {
                                    AccountSettingsHomePane(
                                        currentVersion = state.updateInfo?.currentVersion?.ifBlank { "--" } ?: "--",
                                        latestVersion = state.updateInfo?.latestVersion.orEmpty(),
                                        hasUpdate = state.updateInfo?.hasUpdate == true,
                                        isUpdateLoading = state.isUpdateLoading,
                                        cacheRetention = state.cacheRetention,
                                        cacheSizeSummary = state.cacheSizeSummary,
                                        isCacheSizeLoading = state.isCacheSizeLoading,
                                        hasCrashLog = state.hasCrashLog,
                                        onOpenUpdate = onOpenSettingsUpdate,
                                        onOpenCache = onOpenSettingsCache,
                                        onOpenAgreement = onOpenSettingsAgreement,
                                        onOpenLogs = onOpenSettingsLogs
                                    )
                                }
                            }
                        }
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
                                    .padding(horizontal = 20.dp, vertical = 22.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.userName,
                                    onValueChange = onUserNameChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    singleLine = true,
                                    label = { Text("用户名") },
                                    placeholder = { Text("请输入站内用户名") },
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
                                OutlinedTextField(
                                    value = state.password,
                                    onValueChange = onPasswordChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
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
                                        focusedContainerColor = UiPalette.SurfaceSoft,
                                        unfocusedContainerColor = UiPalette.SurfaceSoft
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

                                AccountGuestAuxiliaryActions(
                                    onRegister = { onAuthModeChange(AccountAuthMode.Register) },
                                    onFindPassword = { onAuthModeChange(AccountAuthMode.FindPassword) },
                                    onAbout = { onAuthModeChange(AccountAuthMode.About) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun LegacyAboutPane(
    currentVersion: String,
    latestVersion: String,
    notes: String,
    hasUpdate: Boolean,
    isUpdateLoading: Boolean,
    cacheRetention: CacheRetentionOption,
    cacheSizeSummary: CacheSizeSummary,
    isCacheSizeLoading: Boolean,
    isCacheClearing: Boolean,
    crashLogText: String,
    hasCrashLog: Boolean,
    onCheckUpdate: () -> Unit,
    onRefreshCacheSize: () -> Unit,
    onSetCacheRetention: (CacheRetentionOption) -> Unit,
    onClearAppCache: () -> Unit,
    onRefreshCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
    onOpenRelease: () -> Unit,
    onDownloadUpdate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "工具中心",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                AccountToolSection(
                    title = "版本更新",
                    description = when {
                        isUpdateLoading -> "正在检查更新"
                        hasUpdate -> "发现新版本"
                        latestVersion.isNotBlank() -> "当前已是最新版本"
                        else -> "可手动检查发布页"
                    }
                ) {
                    Text(
                        text = buildString {
                            append("当前版本：")
                            append(currentVersion)
                            if (latestVersion.isNotBlank()) {
                                append("\n最新版本：")
                                append(latestVersion)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.Ink
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckUpdate,
                            enabled = !isUpdateLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            border = BorderStroke(1.dp, UiPalette.BorderSoft),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (isUpdateLoading) "检查中..." else "检查更新")
                        }
                        Button(
                            onClick = if (hasUpdate) onDownloadUpdate else onOpenRelease,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.Accent,
                                contentColor = UiPalette.AccentText
                            )
                        ) {
                            Text(if (hasUpdate) "前往下载" else "查看发布")
                        }
                    }
                }
                if (notes.isNotBlank()) {
                    UpdateNotesSection(notes = notes)
                }
                CacheSettingsSection(
                    retention = cacheRetention,
                    summary = cacheSizeSummary,
                    isLoading = isCacheSizeLoading,
                    isClearing = isCacheClearing,
                    onRefreshSize = onRefreshCacheSize,
                    onSetRetention = onSetCacheRetention,
                    onClearCache = onClearAppCache
                )
                AccountToolSection(
                    title = "用户协议与隐私说明",
                    description = "用户协议、隐私和免责声明"
                ) {
                    Text(
                        text = JlenUserAgreementPlainText,
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextPrimary
                    )
                }
                AccountToolSection(
                    title = "问题日志",
                    description = if (hasCrashLog) "已有本机日志" else "暂无本机日志"
                ) {
                    if (hasCrashLog) {
                        CrashLogCard(
                            logText = crashLogText,
                            onRefresh = onRefreshCrashLog,
                            onClear = onClearCrashLog
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "当前没有本机问题日志。",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = UiPalette.TextSecondary
                            )
                            OutlinedButton(
                                onClick = onRefreshCrashLog,
                                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("刷新")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LegacyCrashLogCard(
    logText: String,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val logScrollState = rememberScrollState()

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
                text = "本机问题日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UiPalette.SurfaceSoft.copy(alpha = 0.7f))
                    .verticalScroll(logScrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = logText.ifBlank { "暂无问题日志" },
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("刷新", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("issue_log", logText))
                        Toast.makeText(context, "问题日志已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = logText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("复制", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = logText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("清空", maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun CacheSettingsSection(
    retention: CacheRetentionOption,
    summary: CacheSizeSummary,
    isLoading: Boolean,
    isClearing: Boolean,
    onRefreshSize: () -> Unit,
    onSetRetention: (CacheRetentionOption) -> Unit,
    onClearCache: () -> Unit
) {
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    if (showClearConfirm) {
        ClearCacheConfirmDialog(
            totalSize = if (summary.isAvailable) formatCacheSize(summary.totalBytes) else "无法统计",
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                showClearConfirm = false
                onClearCache()
            }
        )
    }

    AccountToolSection(
        title = "缓存设置",
        description = "管理内容缓存和图片缓存"
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.74f)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CacheSizeLine(
                    title = "总缓存大小",
                    value = when {
                        isLoading -> "统计中..."
                        !summary.isAvailable -> "无法统计"
                        else -> formatCacheSize(summary.totalBytes)
                    },
                    highlight = true
                )
                CacheSizeLine(
                    title = "内容缓存",
                    value = if (summary.isAvailable) formatCacheSize(summary.contentBytes) else "无法统计"
                )
                CacheSizeLine(
                    title = "图片缓存",
                    value = if (summary.isAvailable) formatCacheSize(summary.imageBytes) else "无法统计"
                )
            }
        }
        Text(
            text = "缓存保存时间",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = UiPalette.Ink
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CacheRetentionOption.entries, key = { it.key }) { option ->
                val selected = option == retention
                OutlinedButton(
                    onClick = { onSetRetention(option) },
                    modifier = Modifier.height(40.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) UiPalette.Accent else UiPalette.BorderSoft
                    ),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) UiPalette.AccentSoft else UiPalette.Surface,
                        contentColor = if (selected) UiPalette.Accent else UiPalette.TextPrimary
                    )
                ) {
                    Text(option.label, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRefreshSize,
                enabled = !isLoading && !isClearing,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isLoading) "统计中..." else "刷新大小")
            }
            Button(
                onClick = { showClearConfirm = true },
                enabled = !isClearing,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiPalette.Accent,
                    contentColor = UiPalette.AccentText
                )
            ) {
                Text(if (isClearing) "清除中..." else "清除缓存", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CacheSizeLine(
    title: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = UiPalette.TextSecondary
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (highlight) UiPalette.Ink else UiPalette.TextPrimary
        )
    }
}

@Composable
internal fun LegacyAccountRegisterPane(
    state: AccountUiState,
    onEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
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
                enabled = !state.isActionLoading && state.registerCodeCountdown <= 0,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, UiPalette.BorderSoft)
            ) {
                Text(
                    if (state.registerCodeCountdown > 0) {
                        "${state.registerCodeCountdown}s"
                    } else if (state.isActionLoading) {
                        "发送中..."
                    } else {
                        "发送${state.registerCodeLabel}"
                    }
                )
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
            CaptchaImageBox(
                bytes = state.registerCaptcha,
                isLoading = state.isContentLoading,
                onRefresh = onRefreshCaptcha
            )
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
internal fun LegacyAccountFindPasswordPane(
    state: AccountUiState,
    onEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = state.findPasswordEditor.email,
            onValueChange = { value -> onEditorChange { it.copy(email = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            label = { Text("邮箱") },
            placeholder = { Text("请输入绑定邮箱") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.findPasswordEditor.code,
                onValueChange = { value -> onEditorChange { it.copy(code = value) } },
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                label = { Text("邮箱验证码") },
                placeholder = { Text("请输入验证码") },
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
                enabled = !state.isActionLoading && state.findPasswordCodeCountdown <= 0,
                modifier = Modifier
                    .width(98.dp)
                    .height(44.dp),
                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.72f),
                    contentColor = UiPalette.Accent,
                    disabledContainerColor = UiPalette.SurfaceSoft.copy(alpha = 0.56f),
                    disabledContentColor = UiPalette.TextMuted
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (state.findPasswordCodeCountdown > 0) {
                        "${state.findPasswordCodeCountdown}s"
                    } else if (state.isActionLoading) {
                        "发送中"
                    } else {
                        "获取验证码"
                    },
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
            Text(if (state.isActionLoading) "重置中..." else "重置密码", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountSegmentBar(content: @Composable RowScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(UiPalette.SurfaceSoft.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun AccountUnderlineTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) UiPalette.Accent else UiPalette.TextSecondary
        )
        Box(
            modifier = Modifier
                .width(if (selected) 26.dp else 16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) UiPalette.Accent else Color.Transparent)
        )
    }
}

@Composable
private fun AccountOverviewPane(
    state: AccountUiState,
    isActionLoading: Boolean,
    onEditProfile: () -> Unit,
    onBindEmail: () -> Unit,
    onSignIn: () -> Unit,
    onOpenPointLogs: () -> Unit,
    onOpenFollow: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val membershipStatus = remember(
        state.session.groupName,
        state.membershipInfo.expiry,
        state.membershipInfo.points,
        state.membershipSignInInfo.signedToday
    ) {
        buildMembershipStatusText(state)
    }
    val email = state.profileEditor.email.trim()
    val hasEmail = email.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "账号总览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Text(
                            text = membershipStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = 74.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(UiPalette.AccentGlow)
                            .border(1.dp, UiPalette.BorderSoft, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (state.membershipSignInInfo.signedToday) "今日已签" else "待签到",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.membershipSignInInfo.signedToday) UiPalette.Accent else UiPalette.DangerText,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountOverviewMetric(
                        label = "用户 ID",
                        value = state.session.userId.ifBlank { "--" },
                        modifier = Modifier.weight(1f)
                    )
                    AccountOverviewMetric(
                        label = "剩余积分",
                        value = state.membershipInfo.points.ifBlank { "--" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountOverviewMetric(
                        label = "到期时间",
                        value = state.membershipInfo.expiry.ifBlank { "--" },
                        modifier = Modifier.weight(1f)
                    )
                    AccountOverviewMetric(
                        label = "播放记录",
                        value = "${state.historyItems.size} 条",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "快捷处理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountOverviewActionButton(
                        title = "资料编辑",
                        subtitle = "完善资料",
                        icon = Icons.Rounded.Person,
                        onClick = onEditProfile,
                        modifier = Modifier.weight(1f)
                    )
                    AccountOverviewActionButton(
                        title = if (hasEmail) "管理邮箱" else "绑定邮箱",
                        subtitle = email.takeIf { hasEmail }?.maskEmailForAccount() ?: "保护账号安全",
                        icon = Icons.Rounded.CheckCircle,
                        onClick = onBindEmail,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountOverviewActionButton(
                        title = if (state.membershipSignInInfo.signedToday) "今日已签" else "会员签到",
                        subtitle = signInRewardHint(state.membershipSignInInfo),
                        icon = Icons.Rounded.Star,
                        enabled = !state.membershipSignInInfo.signedToday && !isActionLoading,
                        onClick = onSignIn,
                        modifier = Modifier.weight(1f)
                    )
                    AccountOverviewActionButton(
                        title = "积分日志",
                        subtitle = "查看明细",
                        icon = Icons.Rounded.History,
                        onClick = onOpenPointLogs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, UiPalette.BorderSoft)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccountOverviewLinkRow(
                    title = "去追剧",
                    description = "查看已加入追剧的更新和续播",
                    icon = Icons.Rounded.GridView,
                    onClick = onOpenFollow
                )
                AccountOverviewLinkRow(
                    title = "设置与工具",
                    description = if (state.hasCrashLog) "有问题日志可查看" else "检查更新、查看协议和日志",
                    icon = Icons.Rounded.Info,
                    onClick = onOpenLogs
                )
            }
        }
    }
}

@Composable
private fun AccountOverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(UiPalette.SurfaceSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = UiPalette.TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountOverviewActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = UiPalette.SurfaceSoft,
            contentColor = UiPalette.Ink,
            disabledContainerColor = UiPalette.SurfaceStrong,
            disabledContentColor = UiPalette.TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(UiPalette.Accent.copy(alpha = if (enabled) 0.12f else 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) UiPalette.Accent else UiPalette.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) UiPalette.TextSecondary else UiPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AccountOverviewLinkRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(UiPalette.SurfaceSoft.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(UiPalette.Accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = UiPalette.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = UiPalette.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun signInRewardHint(signInInfo: MembershipSignInInfo): String = when {
    signInInfo.signedToday -> "明天再来"
    signInInfo.rewardPoints.isNotBlank() -> "+${signInInfo.rewardPoints} 积分"
    signInInfo.rewardMinPoints.isNotBlank() && signInInfo.rewardMaxPoints.isNotBlank() ->
        "${signInInfo.rewardMinPoints}-${signInInfo.rewardMaxPoints} 积分"
    signInInfo.rewardMinPoints.isNotBlank() -> "${signInInfo.rewardMinPoints} 积分起"
    else -> "领取积分"
}

private fun buildMembershipStatusText(state: AccountUiState): String {
    val groupName = state.session.groupName.ifBlank { state.membershipInfo.groupName }.ifBlank { "普通用户" }
    val expiry = state.membershipInfo.expiry.trim()
    val points = state.membershipInfo.points.trim()
    return when {
        expiry.isNotBlank() && expiry != "--" -> "$groupName 有效至 $expiry"
        points.isNotBlank() && !state.membershipSignInInfo.signedToday -> "$groupName，签到可领取积分"
        points.isNotBlank() -> "$groupName，剩余 $points 积分"
        else -> groupName
    }
}

private fun String.maskEmailForAccount(): String {
    val atIndex = indexOf('@')
    if (atIndex <= 0 || atIndex == lastIndex) return this
    val name = take(atIndex)
    val domain = drop(atIndex)
    val visiblePrefix = name.take(2)
    return "$visiblePrefix***$domain"
}

private enum class AccountProfileTab {
    Overview,
    Edit
}

@Composable
internal fun LegacyAccountProfilePaneV2(
    isLoading: Boolean,
    fields: List<Pair<String, String>>,
    editor: UserProfileEditor,
    isSaving: Boolean,
    isEditTab: Boolean,
    emailBindCodeCountdown: Int,
    onTabChange: (Boolean) -> Unit,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit,
    onSendEmailCode: () -> Unit,
    onBindEmail: () -> Unit,
    onUnbindEmail: () -> Unit
) {
    val selectedTab = if (isEditTab) AccountProfileTab.Edit else AccountProfileTab.Overview
    var showBindEmailDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showUnbindEmailConfirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showBindEmailDialog, editor.email) {
        if (showBindEmailDialog && editor.email.contains("@") && editor.email.contains(".")) {
            showBindEmailDialog = false
        }
    }
    val overviewFields = remember(fields, editor.email) {
        if (editor.email.isBlank() || fields.any { it.first == "邮箱" }) {
            fields
        } else {
            val expiryIndex = fields.indexOfFirst { it.first == "到期时间" }
            if (expiryIndex >= 0) {
                buildList {
                    addAll(fields.take(expiryIndex + 1))
                    add("邮箱" to editor.email)
                    addAll(fields.drop(expiryIndex + 1))
                }
            } else {
                fields + ("邮箱" to editor.email)
            }
        }
    }

    if (showUnbindEmailConfirm) {
        UnbindEmailConfirmDialog(
            email = editor.email,
            onDismiss = { showUnbindEmailConfirm = false },
            onConfirm = {
                showUnbindEmailConfirm = false
                onUnbindEmail()
            }
        )
    }
    if (showBindEmailDialog) {
        BindEmailDialog(
            editor = editor,
            isSaving = isSaving,
            emailBindCodeCountdown = emailBindCodeCountdown,
            onEditorChange = onEditorChange,
            onSendEmailCode = onSendEmailCode,
            onBindEmail = onBindEmail,
            onDismiss = { showBindEmailDialog = false }
        )
    }
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            editor = editor,
            isSaving = isSaving,
            onEditorChange = onEditorChange,
            onSave = onSave,
            onDismiss = {
                showChangePasswordDialog = false
                onEditorChange {
                    it.copy(
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )
                }
            }
        )
    }

    when {
        isLoading -> LoadingPane("资料加载中...")
        else -> Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AccountSegmentBar {
                    AccountProfileTab.entries.forEach { tab ->
                        AccountUnderlineTab(
                            text = when (tab) {
                                AccountProfileTab.Overview -> "基本资料"
                                AccountProfileTab.Edit -> "修改信息"
                            },
                            selected = tab == selectedTab,
                            onClick = { onTabChange(tab == AccountProfileTab.Edit) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                when (selectedTab) {
                    AccountProfileTab.Overview -> {
                        if (overviewFields.isEmpty()) {
                            Text(
                                text = "暂无资料",
                                color = UiPalette.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            overviewFields.forEach { (label, value) ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(UiPalette.SurfaceSoft)
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    }

                    AccountProfileTab.Edit -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "资料修改",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.Ink
                            )
                            Text(
                                text = "管理账号资料、邮箱绑定和密码设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = UiPalette.TextSecondary
                            )
                        }

                        AccountEditSectionCard(title = "资料补充") {
                            ProfileEditorField(
                                label = "QQ号码",
                                value = editor.qq,
                                onValueChange = { value -> onEditorChange { it.copy(qq = value) } }
                            )
                        }

                        val hasBoundEmail = editor.email.contains("@") && editor.email.contains(".")
                        if (!hasBoundEmail) {
                            AccountEditSectionCard(
                                title = "邮箱绑定",
                                description = "绑定后可用于找回账号和接收验证码"
                            ) {
                                ProfileActionCard(
                                    title = "未绑定邮箱",
                                    description = "绑定邮箱后可用于找回账号和接收验证码。",
                                    actionText = "绑定邮箱",
                                    enabled = !isSaving,
                                    onAction = { showBindEmailDialog = true }
                                )
                            }
                        } else {
                            AccountEditSectionCard(
                                title = "邮箱绑定",
                                description = "当前账号邮箱已绑定，可按需解绑后重新绑定"
                            ) {
                                ReadonlyBindingField(
                                    label = "邮箱",
                                    value = editor.email,
                                    actionText = if (isSaving) "解绑中..." else "解绑邮箱",
                                    onAction = if (isSaving) null else ({ showUnbindEmailConfirm = true })
                                )
                            }
                        }

                        AccountEditSectionCard(
                            title = "密码设置",
                            description = "点击后在弹窗中修改账号登录密码"
                        ) {
                            ProfileActionCard(
                                title = "登录密码",
                                description = "需要原密码、新密码和确认密码。",
                                actionText = "修改密码",
                                enabled = !isSaving,
                                onAction = { showChangePasswordDialog = true }
                            )
                        }
                        Button(
                            onClick = {
                                onEditorChange {
                                    it.copy(
                                        currentPassword = "",
                                        newPassword = "",
                                        confirmPassword = ""
                                    )
                                }
                                onSave()
                            },
                            enabled = !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(UiDimens.PrimaryButtonHeight),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.Accent,
                                contentColor = UiPalette.AccentText
                            )
                        ) {
                            Text(if (isSaving) "保存中..." else "保存资料", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileActionCard(
    title: String,
    description: String,
    actionText: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
            OutlinedButton(
                onClick = onAction,
                enabled = enabled,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, UiPalette.Accent.copy(alpha = 0.22f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = UiPalette.Accent.copy(alpha = 0.06f),
                    contentColor = UiPalette.Accent
                )
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AccountEditSectionCard(
    title: String,
    description: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                description
                    .takeIf { it.isNotBlank() }
                    ?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = UiPalette.TextSecondary
                        )
                    }
            }
            content()
        }
    }
}

@Composable
internal fun LegacyAccountProfilePane(
    isLoading: Boolean,
    fields: List<Pair<String, String>>,
    editor: UserProfileEditor,
    isSaving: Boolean,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit
) {
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            editor = editor,
            isSaving = isSaving,
            onEditorChange = onEditorChange,
            onSave = onSave,
            onDismiss = {
                showChangePasswordDialog = false
                onEditorChange {
                    it.copy(
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )
                }
            }
        )
    }

    when {
        isLoading -> LoadingPane("资料加载中...")
        fields.isEmpty() -> EmptyPane(
            message = "暂无资料",
            description = "当前账号资料还没有可展示的信息",
            style = FeedbackPaneStyle.Card
        )
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
                    label = "邮箱",
                    value = editor.email,
                    onValueChange = { value -> onEditorChange { it.copy(email = value) } }
                )
                ProfileEditorField(
                    label = "手机号",
                    value = editor.phone,
                    onValueChange = { value -> onEditorChange { it.copy(phone = value) } }
                )
                ProfileActionCard(
                    title = "登录密码",
                    description = "点击后在弹窗中修改账号登录密码。",
                    actionText = "修改密码",
                    enabled = !isSaving,
                    onAction = { showChangePasswordDialog = true }
                )
                Button(
                    onClick = {
                        onEditorChange {
                            it.copy(
                                currentPassword = "",
                                newPassword = "",
                                confirmPassword = ""
                            )
                        }
                        onSave()
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UiPalette.Accent,
                        contentColor = UiPalette.AccentText
                    )
                ) {
                    Text(if (isSaving) "保存中..." else "保存资料", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegacyProfileEditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    keyboardType: KeyboardType? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Done,
    modifier: Modifier = Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val resolvedKeyboardType = keyboardType ?: if (password) KeyboardType.Password else KeyboardType.Text
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = resolvedKeyboardType,
            imeAction = imeAction,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
            autoCorrect = resolvedKeyboardType == KeyboardType.Text && !password
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        ),
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
internal fun LegacyReadonlyBindingField(
    label: String,
    value: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.78f))
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
                Text(label, color = UiPalette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                if (!actionText.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onAction?.invoke() },
                        enabled = onAction != null,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, UiPalette.Accent.copy(alpha = 0.22f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = UiPalette.Accent.copy(alpha = 0.06f),
                            contentColor = UiPalette.Accent
                        )
                    ) {
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(value, color = UiPalette.Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (!actionText.isNullOrBlank()) {
                Text(
                    text = "解绑后可重新绑定新的邮箱地址",
                    color = UiPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun LegacyAccountRecordPane(
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
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }

    if (showClearAllConfirm) {
        ClearHistoryConfirmDialog(
            count = items.size,
            onDismiss = { showClearAllConfirm = false },
            onConfirm = {
                showClearAllConfirm = false
                onClearAll()
            }
        )
    }

    when {
        isLoading && items.isEmpty() -> LoadingPane("$title 加载中...", style = FeedbackPaneStyle.Card)
        items.isEmpty() -> EmptyPane(
            message = emptyMessage,
            description = "这里会展示你最近关注和操作过的内容",
            style = FeedbackPaneStyle.Card
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, UiPalette.BorderSoft)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    UiPalette.Surface,
                                    UiPalette.SurfaceStrong,
                                    UiPalette.AccentGlow.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Text(
                            text = "共 ${items.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = UiPalette.TextSecondary
                        )
                    }
                    TextButton(
                        onClick = { showClearAllConfirm = true },
                        enabled = !isActionLoading
                    ) {
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

private fun LazyListScope.accountRecordPaneItems(
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
        isLoading && items.isEmpty() -> item(key = "account_record_loading") {
            LoadingPane("$title 加载中...", style = FeedbackPaneStyle.Card)
        }
        items.isEmpty() -> item(key = "account_record_empty") {
            EmptyPane(
                message = emptyMessage,
                description = "这里会展示你最近关注和操作过的内容",
                style = FeedbackPaneStyle.Card
            )
        }
        else -> {
            item(key = "account_record_header") {
                AccountRecordHeaderCard(
                    title = title,
                    count = items.size,
                    isActionLoading = isActionLoading,
                    onClearAll = onClearAll
                )
            }
            items(
                items = items,
                key = { item ->
                    item.recordId.ifBlank {
                        listOf(item.vodId, item.title, item.playUrl).joinToString("|")
                    }
                },
                contentType = { "account_record" }
            ) { item ->
                AccountRecordCard(
                    item = item,
                    isActionLoading = isActionLoading,
                    onPrimaryAction = onPrimaryAction,
                    onDelete = onDeleteItem
                )
            }
            item(key = "account_record_footer") {
                LoadMoreFooter(
                    hasMore = hasMore,
                    isLoading = isLoading && items.isNotEmpty(),
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun AccountRecordHeaderCard(
    title: String,
    count: Int,
    isActionLoading: Boolean,
    onClearAll: () -> Unit
) {
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }

    if (showClearAllConfirm) {
        ClearHistoryConfirmDialog(
            count = count,
            onDismiss = { showClearAllConfirm = false },
            onConfirm = {
                showClearAllConfirm = false
                onClearAll()
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(UiDimens.CardRadius),
        border = BorderStroke(1.dp, UiPalette.BorderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            UiPalette.Surface,
                            UiPalette.SurfaceStrong,
                            UiPalette.AccentGlow.copy(alpha = 0.18f)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "共 $count 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
            TextButton(
                onClick = { showClearAllConfirm = true },
                enabled = !isActionLoading
            ) {
                Text(if (isActionLoading) "处理中..." else "清空")
            }
        }
    }
}

@Composable
internal fun LegacyAccountRecordCard(
    item: top.jlen.vod.data.UserCenterItem,
    isActionLoading: Boolean,
    onPrimaryAction: (top.jlen.vod.data.UserCenterItem) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.BorderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            val subtitle = sanitizeUserFacingComposite(item.subtitle)
            val watchedEpisodeLabel = buildHistoryWatchedEpisodeLabel(
                item = item,
                subtitle = subtitle
            )
            val recordSummary = listOfNotNull(
                watchedEpisodeLabel.takeIf { it.isNotBlank() },
                subtitle.takeIf { it.isNotBlank() }
            ).joinToString(" | ")
            if (recordSummary.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(UiDimens.ControlRadius))
                        .background(UiPalette.SurfaceSoft)
                        .border(BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.7f)), RoundedCornerShape(UiDimens.ControlRadius))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = recordSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onPrimaryAction(item) },
                    enabled = !isActionLoading && (item.vodId.isNotBlank() || item.playUrl.isNotBlank()),
                    modifier = Modifier
                        .weight(1f)
                        .height(UiDimens.CompactButtonHeight),
                    shape = RoundedCornerShape(UiDimens.ControlRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UiPalette.Accent,
                        contentColor = UiPalette.AccentText,
                        disabledContainerColor = UiPalette.SurfaceStrong,
                        disabledContentColor = UiPalette.TextMuted
                    )
                ) {
                    Text(item.actionLabel.ifBlank { "查看详情" })
                }
                OutlinedButton(
                    onClick = { onDelete(item.recordId) },
                    enabled = item.recordId.isNotBlank() && !isActionLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(UiDimens.CompactButtonHeight),
                    shape = RoundedCornerShape(UiDimens.ControlRadius),
                    border = BorderStroke(1.dp, UiPalette.DangerBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = UiPalette.DangerSurface,
                        contentColor = UiPalette.DangerText
                    )
                ) {
                    Text("删除")
                }
            }
        }
    }
}

private fun buildHistoryWatchedEpisodeLabel(
    item: top.jlen.vod.data.UserCenterItem,
    subtitle: String = ""
): String {
    val episodeLabel = item.episodeIndex
        .takeIf { it >= 0 }
        ?.let { "观看至第${it + 1}集" }
        .orEmpty()
    val sourceLabel = item.sourceName.trim()
        .takeIf { it.isNotBlank() && !subtitle.contains(it, ignoreCase = true) }
        .orEmpty()
    return when {
        episodeLabel.isNotBlank() && sourceLabel.isNotBlank() -> "$episodeLabel · $sourceLabel"
        episodeLabel.isNotBlank() -> episodeLabel
        sourceLabel.isNotBlank() -> sourceLabel
        else -> ""
    }
}

@Composable
internal fun LegacyMembershipPaneV2(
    isLoading: Boolean,
    info: top.jlen.vod.data.MembershipInfo,
    plans: List<MembershipPlan>,
    signInInfo: MembershipSignInInfo,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit,
    onSignIn: () -> Unit,
    onOpenPointLogs: () -> Unit
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("会员信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        TextButton(
                            onClick = onOpenPointLogs,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "积分日志",
                                color = UiPalette.Accent,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "查看积分日志",
                                tint = UiPalette.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text("当前分组：${info.groupName.ifBlank { "普通会员" }}", color = UiPalette.Ink)
                    Text("剩余积分：${info.points.ifBlank { "--" }}", color = UiPalette.Ink)
                    Text("到期时间：${info.expiry.ifBlank { "--" }}", color = UiPalette.Ink)
                }
            }

            if (signInInfo.enabled || signInInfo.signedToday) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, UiPalette.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(UiPalette.Accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (signInInfo.signedToday) "已签" else "签到",
                                color = UiPalette.Accent,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (signInInfo.signedToday) "今日已签到" else "每日签到",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = UiPalette.Ink
                            )
                            val rewardHint = when {
                                signInInfo.rewardPoints.isNotBlank() -> "今日获得 ${signInInfo.rewardPoints} 积分"
                                signInInfo.rewardMinPoints.isNotBlank() && signInInfo.rewardMaxPoints.isNotBlank() ->
                                    "签到可获得 ${signInInfo.rewardMinPoints} - ${signInInfo.rewardMaxPoints} 积分"
                                signInInfo.rewardMinPoints.isNotBlank() -> "签到可获得 ${signInInfo.rewardMinPoints} 积分起"
                                else -> "完成签到即可领取积分奖励"
                            }
                            Text(
                                text = rewardHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = UiPalette.TextSecondary
                            )
                            signInInfo.signedAt.takeIf(String::isNotBlank)?.let { signedAt ->
                                Text(
                                    text = signedAt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UiPalette.TextMuted
                                )
                            }
                        }
                        Button(
                            onClick = onSignIn,
                            enabled = signInInfo.enabled && !signInInfo.signedToday && !isActionLoading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.Accent,
                                contentColor = UiPalette.AccentText,
                                disabledContainerColor = UiPalette.SurfaceSoft,
                                disabledContentColor = UiPalette.TextMuted
                            )
                        ) {
                            Text(
                                when {
                                    isActionLoading -> "处理中..."
                                    signInInfo.signedToday -> "今日已签"
                                    else -> "立即签到"
                                }
                            )
                        }
                    }
                }
            }

            if (plans.isEmpty()) {
                EmptyPane(
                    message = "暂无套餐",
                    description = "当前没有可展示的会员方案",
                    style = FeedbackPaneStyle.Card
                )
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
fun AccountPointLogScreen(
    pointLogs: List<PointLogItem>,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = UiPalette.Ink
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "积分日志",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = "查看签到、升级和积分变动记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }
            }
        }

        if (pointLogs.isEmpty()) {
            item {
                EmptyPane(
                    message = "暂无积分日志",
                    description = "签到、升级和积分变动记录会显示在这里",
                    style = FeedbackPaneStyle.Card
                )
            }
        } else {
            items(pointLogs, key = { it.logId.ifBlank { it.time + it.typeText } }) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, UiPalette.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (log.isIncome) UiPalette.Accent.copy(alpha = 0.12f)
                                    else UiPalette.SurfaceSoft
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (log.isIncome) "+" else "-",
                                color = if (log.isIncome) UiPalette.Accent else UiPalette.Ink,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = log.typeText.ifBlank { log.remarks.ifBlank { "积分变动" } },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = UiPalette.Ink
                            )
                            log.remarks.takeIf(String::isNotBlank)?.let { remarks ->
                                Text(
                                    text = remarks,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UiPalette.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = log.timeText.ifBlank { log.time.ifBlank { "--" } },
                                style = MaterialTheme.typography.labelMedium,
                                color = UiPalette.TextMuted
                            )
                        }
                        Text(
                            text = log.pointsText.ifBlank {
                                when {
                                    log.points.isBlank() -> "--"
                                    log.isIncome && !log.points.startsWith("+") -> "+${log.points}"
                                    !log.isIncome && !log.points.startsWith("-") -> "-${log.points}"
                                    else -> log.points
                                }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (log.isIncome) UiPalette.Accent else UiPalette.Ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LegacyMembershipPane(
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
                EmptyPane(
                    message = "暂无套餐",
                    description = "当前没有可展示的会员方案",
                    style = FeedbackPaneStyle.Card
                )
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

internal fun String.toLegacyMembershipDuration(): String = when (lowercase()) {
    "day" -> "包天"
    "week" -> "包周"
    "month" -> "包月"
    "year" -> "包年"
    else -> this
}
