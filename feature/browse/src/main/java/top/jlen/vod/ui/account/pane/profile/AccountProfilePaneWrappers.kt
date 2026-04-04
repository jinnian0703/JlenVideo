package top.jlen.vod.ui

import androidx.compose.runtime.Composable
import top.jlen.vod.data.UserProfileEditor

@Composable
internal fun AccountProfilePaneV2(
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
) = LegacyAccountProfilePaneV2(
    isLoading = isLoading,
    fields = fields,
    editor = editor,
    isSaving = isSaving,
    isEditTab = isEditTab,
    onTabChange = onTabChange,
    onEditorChange = onEditorChange,
    onSave = onSave,
    onSendEmailCode = onSendEmailCode,
    onBindEmail = onBindEmail,
    onUnbindEmail = onUnbindEmail
)

@Composable
internal fun AccountProfilePane(
    isLoading: Boolean,
    fields: List<Pair<String, String>>,
    editor: UserProfileEditor,
    isSaving: Boolean,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit
) = LegacyAccountProfilePane(
    isLoading = isLoading,
    fields = fields,
    editor = editor,
    isSaving = isSaving,
    onEditorChange = onEditorChange,
    onSave = onSave
)

@Composable
internal fun ProfileEditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false
) = LegacyProfileEditorField(
    label = label,
    value = value,
    onValueChange = onValueChange,
    password = password
)

@Composable
internal fun ReadonlyBindingField(
    label: String,
    value: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) = LegacyReadonlyBindingField(
    label = label,
    value = value,
    actionText = actionText,
    onAction = onAction
)
