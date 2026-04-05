package top.jlen.vod.ui

import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.FindPasswordPage
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.RegisterPage
import top.jlen.vod.data.UserProfileEditor

internal fun accountStateWithValidationError(
    accountState: AccountUiState,
    message: String
): AccountUiState = accountState.copy(error = message)

internal fun accountStateWithUserName(
    accountState: AccountUiState,
    value: String
): AccountUiState = accountState.copy(userName = value)

internal fun accountStateWithPassword(
    accountState: AccountUiState,
    value: String
): AccountUiState = accountState.copy(password = value)

internal fun accountStateWithAuthMode(
    accountState: AccountUiState,
    mode: AccountAuthMode
): AccountUiState = accountState.copy(
    authMode = mode,
    error = null,
    message = null
)

internal fun accountStateWithRegisterEditor(
    accountState: AccountUiState,
    editor: RegisterEditor
): AccountUiState = accountState.copy(
    registerEditor = editor,
    error = null,
    message = null
)

internal fun accountStateWithFindPasswordEditor(
    accountState: AccountUiState,
    editor: FindPasswordEditor
): AccountUiState = accountState.copy(
    findPasswordEditor = editor,
    error = null,
    message = null
)

internal fun accountStateWithProfileEditor(
    accountState: AccountUiState,
    editor: UserProfileEditor
): AccountUiState = accountState.copy(
    profileEditor = editor,
    error = null,
    message = null
)

internal fun accountStateWithProfileEditTab(
    accountState: AccountUiState,
    editMode: Boolean
): AccountUiState = accountState.copy(isProfileEditTab = editMode)

internal fun accountStateWithRegisterCaptcha(
    accountState: AccountUiState,
    bytes: ByteArray
): AccountUiState = accountState.copy(
    isContentLoading = false,
    registerCaptcha = bytes
)

internal fun accountStateWithFindPasswordCaptcha(
    accountState: AccountUiState,
    bytes: ByteArray
): AccountUiState = accountState.copy(
    isContentLoading = false,
    findPasswordCaptcha = bytes
)

internal fun accountStateWithRegisterPage(
    accountState: AccountUiState,
    page: RegisterPage
): AccountUiState = accountState.copy(
    isContentLoading = false,
    registerChannel = page.channel,
    registerContactLabel = page.contactLabel,
    registerCodeLabel = page.codeLabel,
    registerRequiresCode = page.requiresCode,
    registerRequiresVerify = page.requiresVerify,
    registerCaptchaUrl = page.captchaUrl,
    registerCaptcha = page.captchaBytes,
    registerEditor = accountState.registerEditor.copy(channel = page.channel)
)

internal fun accountStateWithFindPasswordPage(
    accountState: AccountUiState,
    page: FindPasswordPage
): AccountUiState = accountState.copy(
    isContentLoading = false,
    findPasswordRequiresVerify = page.requiresVerify,
    findPasswordCaptchaUrl = page.captchaUrl,
    findPasswordCaptcha = page.captchaBytes
)

internal fun accountStateAfterProfileSaved(accountState: AccountUiState): AccountUiState =
    accountState.copy(
        profileEditor = accountState.profileEditor.copy(
            currentPassword = "",
            newPassword = "",
            confirmPassword = ""
        )
    )

internal fun accountStateAfterEmailBound(
    accountState: AccountUiState,
    email: String
): AccountUiState = accountState.copy(
    profileEditor = accountState.profileEditor.copy(
        email = email,
        pendingEmail = "",
        emailCode = ""
    )
)

internal fun accountStateAfterEmailUnbound(accountState: AccountUiState): AccountUiState =
    accountState.copy(
        isProfileEditTab = true,
        profileFields = accountState.profileFields.filterNot { it.first == "邮箱" },
        profileEditor = accountState.profileEditor.copy(
            email = "",
            pendingEmail = "",
            emailCode = ""
        )
    )

internal fun accountStateAfterRegisterSuccess(
    accountState: AccountUiState,
    userName: String
): AccountUiState = accountState.copy(
    authMode = AccountAuthMode.Login,
    userName = userName,
    password = "",
    registerEditor = RegisterEditor(channel = accountState.registerChannel)
)

internal fun accountStateAfterFindPasswordSuccess(
    accountState: AccountUiState,
    userName: String
): AccountUiState = accountState.copy(
    authMode = AccountAuthMode.Login,
    userName = userName,
    password = "",
    findPasswordEditor = FindPasswordEditor()
)
