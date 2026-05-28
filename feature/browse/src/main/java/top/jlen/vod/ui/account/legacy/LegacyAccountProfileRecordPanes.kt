package top.jlen.vod.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.jlen.vod.data.MembershipSignInInfo
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.sanitizeUserFacingComposite

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
    var pendingPasswordSave by rememberSaveable { mutableStateOf(false) }
    var showUnbindEmailConfirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showBindEmailDialog, editor.email) {
        if (showBindEmailDialog && editor.email.contains("@") && editor.email.contains(".")) {
            showBindEmailDialog = false
        }
    }
    LaunchedEffect(
        showChangePasswordDialog,
        pendingPasswordSave,
        isSaving,
        editor.currentPassword,
        editor.newPassword,
        editor.confirmPassword
    ) {
        if (
            showChangePasswordDialog &&
            pendingPasswordSave &&
            !isSaving &&
            editor.currentPassword.isBlank() &&
            editor.newPassword.isBlank() &&
            editor.confirmPassword.isBlank()
        ) {
            pendingPasswordSave = false
            showChangePasswordDialog = false
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
            onSave = {
                pendingPasswordSave = true
                onSave()
            },
            onDismiss = {
                pendingPasswordSave = false
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
                                text = "资料、邮箱、密码",
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
                                description = "找回账号和接收验证码"
                            ) {
                                ProfileActionCard(
                                    title = "未绑定邮箱",
                                    description = "用于找回账号和接收验证码",
                                    actionText = "绑定邮箱",
                                    enabled = !isSaving,
                                    onAction = { showBindEmailDialog = true }
                                )
                            }
                        } else {
                            AccountEditSectionCard(
                                title = "邮箱绑定",
                                description = "当前邮箱已绑定"
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
                            description = "修改登录密码"
                        ) {
                            ProfileActionCard(
                                title = "登录密码",
                                description = "原密码与新密码",
                                actionText = "修改密码",
                                enabled = !isSaving,
                                onAction = { showChangePasswordDialog = true }
                            )
                        }
                        Button(
                            onClick = onSave,
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
    var pendingPasswordSave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(
        showChangePasswordDialog,
        pendingPasswordSave,
        isSaving,
        editor.currentPassword,
        editor.newPassword,
        editor.confirmPassword
    ) {
        if (
            showChangePasswordDialog &&
            pendingPasswordSave &&
            !isSaving &&
            editor.currentPassword.isBlank() &&
            editor.newPassword.isBlank() &&
            editor.confirmPassword.isBlank()
        ) {
            pendingPasswordSave = false
            showChangePasswordDialog = false
        }
    }
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            editor = editor,
            isSaving = isSaving,
            onEditorChange = onEditorChange,
            onSave = {
                pendingPasswordSave = true
                onSave()
            },
            onDismiss = {
                pendingPasswordSave = false
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
            description = "",
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
                    description = "修改登录密码",
                    actionText = "修改密码",
                    enabled = !isSaving,
                    onAction = { showChangePasswordDialog = true }
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
                Spacer(modifier = Modifier.height(2.dp))
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
            description = "",
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

internal fun LazyListScope.accountRecordPaneItems(
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
                description = "",
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

