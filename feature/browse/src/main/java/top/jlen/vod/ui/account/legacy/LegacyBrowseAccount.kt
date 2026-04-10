package top.jlen.vod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.asImageBitmap
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
import top.jlen.vod.AppRuntimeInfo
import top.jlen.vod.PLAYER_DESKTOP_UA
import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppleCmsCategory
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
import top.jlen.vod.data.sanitizeUserFacingComposite


@Composable
internal fun LegacyAccountScreen(
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
    val visibleSections = remember {
        AccountSection.entries.filterNot { it == AccountSection.Favorites }
    }

    LaunchedEffect(showLoggedInContent, state.selectedSection) {
        if (showLoggedInContent && state.selectedSection == AccountSection.Favorites) {
            onSelectSection(AccountSection.Profile)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
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
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, UiPalette.Border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (state.session.portraitUrl.isNotBlank()) {
                                AuthenticatedAvatar(
                                    imageUrl = state.session.portraitUrl,
                                    contentDescription = state.session.userName,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onChangePortrait),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
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
                                onClick = onSignInMembership,
                                enabled = !state.isActionLoading && !state.membershipSignInInfo.signedToday,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = UiPalette.SurfaceSoft,
                                    contentColor = UiPalette.Accent,
                                    disabledContainerColor = UiPalette.SurfaceStrong,
                                    disabledContentColor = UiPalette.Ink
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

            item {
                AccountSegmentBar {
                    items(
                        items = visibleSections,
                        key = { it.name },
                        contentType = { "account_section" }
                    ) { section ->
                        AccountUnderlineTab(
                            text = when (section) {
                                AccountSection.Profile -> "资料"
                                AccountSection.History -> "记录"
                                AccountSection.Member -> "会员"
                                AccountSection.About -> "关于"
                                AccountSection.Favorites -> "追剧"
                            },
                            selected = state.selectedSection == section,
                            onClick = { onSelectSection(section) }
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
                    AccountSection.Favorites -> EmptyPane(
                        message = "追剧入口已移到底栏",
                        description = "想追的影片请在详情页加入追剧，然后到底栏“追剧”里查看更新和续播",
                        style = FeedbackPaneStyle.Card
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
                        signInInfo = state.membershipSignInInfo,
                        pointLogs = state.membershipPointLogs,
                        isActionLoading = state.isActionLoading,
                        message = state.message,
                        onUpgrade = onUpgradeMembership,
                        onSignIn = onSignInMembership,
                        onOpenPointLogs = onOpenPointLogs
                    )
                    AccountSection.About -> AboutPane(
                        currentVersion = state.updateInfo?.currentVersion?.ifBlank { AppRuntimeInfo.versionName }
                            ?: AppRuntimeInfo.versionName,
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
                                shape = RoundedCornerShape(28.dp),
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
                                currentVersion = state.updateInfo?.currentVersion?.ifBlank { AppRuntimeInfo.versionName }
                                    ?: AppRuntimeInfo.versionName,
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
internal fun LegacyAboutPane(
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(UiPalette.SurfaceSoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "版本信息",
                            style = MaterialTheme.typography.labelLarge,
                            color = UiPalette.TextSecondary
                        )
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
                        Text(
                            text = when {
                                isUpdateLoading -> "正在检查更新"
                                hasUpdate -> "发现新版本，可直接前往下载"
                                latestVersion.isNotBlank() -> "当前已经是最新版本"
                                else -> "可手动检查 GitHub Releases"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = UiPalette.TextSecondary
                        )
                    }
                }
                if (notes.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(UiPalette.SurfaceSoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "更新说明",
                                style = MaterialTheme.typography.labelLarge,
                                color = UiPalette.TextSecondary
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = UiPalette.Ink,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
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
        }
        if (hasCrashLog) {
            CrashLogCard(
                logText = crashLogText,
                onRefresh = onRefreshCrashLog,
                onClear = onClearCrashLog
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, UiPalette.Border)
            ) {
                Text(
                    text = "暂无崩溃日志",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
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
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft),
                    shape = RoundedCornerShape(16.dp)
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
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("复制日志")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = logText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, UiPalette.BorderSoft),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("清空日志")
                }
            }
        }
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
internal fun LegacyAccountFindPasswordPane(
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

@Composable
private fun AccountSegmentBar(content: LazyListScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(UiPalette.SurfaceSoft.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun AccountUnderlineTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
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
    onTabChange: (Boolean) -> Unit,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit,
    onSendEmailCode: () -> Unit,
    onBindEmail: () -> Unit,
    onUnbindEmail: () -> Unit
) {
    val selectedTab = if (isEditTab) AccountProfileTab.Edit else AccountProfileTab.Overview
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
                    items(AccountProfileTab.entries.toList()) { tab ->
                        AccountUnderlineTab(
                            text = when (tab) {
                                AccountProfileTab.Overview -> "基本资料"
                                AccountProfileTab.Edit -> "修改信息"
                            },
                            selected = tab == selectedTab,
                            onClick = { onTabChange(tab == AccountProfileTab.Edit) }
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
                                actionText = if (isSaving) "解绑中..." else "解绑邮箱",
                                onAction = if (isSaving) null else onUnbindEmail
                            )
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
internal fun LegacyAccountProfilePane(
    isLoading: Boolean,
    fields: List<Pair<String, String>>,
    editor: UserProfileEditor,
    isSaving: Boolean,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit
) {
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
internal fun LegacyProfileEditorField(
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(label, color = UiPalette.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Text(value, color = UiPalette.Ink, style = MaterialTheme.typography.bodyLarge)
            if (!actionText.isNullOrBlank()) {
                TextButton(
                    onClick = { onAction?.invoke() },
                    enabled = onAction != null,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    colors = ButtonDefaults.textButtonColors(
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
                .padding(18.dp),
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(UiPalette.SurfaceSoft)
                        .border(BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.7f)), RoundedCornerShape(16.dp))
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
                        .height(44.dp),
                    shape = RoundedCornerShape(18.dp),
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
                        .height(44.dp),
                    shape = RoundedCornerShape(18.dp),
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
