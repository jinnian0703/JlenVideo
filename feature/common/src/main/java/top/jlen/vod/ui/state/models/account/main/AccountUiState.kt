package top.jlen.vod.ui

import top.jlen.vod.data.AppUpdateInfo
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.MembershipInfo
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.MembershipSignInInfo
import top.jlen.vod.data.PointLogItem
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfileEditor

enum class AccountSection {
    Profile,
    Favorites,
    History,
    Member,
    About
}

enum class AccountAuthMode {
    Login,
    Register,
    FindPassword,
    About
}

data class AccountUiState(
    val isLoading: Boolean = false,
    val isContentLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val isUpdateLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val userName: String = "",
    val password: String = "",
    val authMode: AccountAuthMode = AccountAuthMode.Login,
    val registerChannel: String = "email",
    val registerContactLabel: String = "邮箱",
    val registerCodeLabel: String = "邮箱验证码",
    val registerRequiresCode: Boolean = true,
    val registerRequiresVerify: Boolean = true,
    val registerCaptchaUrl: String = "",
    val registerCaptcha: ByteArray? = null,
    val registerEditor: RegisterEditor = RegisterEditor(),
    val findPasswordRequiresVerify: Boolean = true,
    val findPasswordCaptchaUrl: String = "",
    val findPasswordCaptcha: ByteArray? = null,
    val findPasswordEditor: FindPasswordEditor = FindPasswordEditor(),
    val session: AuthSession = AuthSession(),
    val selectedSection: AccountSection = AccountSection.Profile,
    val isProfileEditTab: Boolean = false,
    val profileFields: List<Pair<String, String>> = emptyList(),
    val profileEditor: UserProfileEditor = UserProfileEditor(),
    val favoriteItems: List<UserCenterItem> = emptyList(),
    val favoriteNextPageUrl: String? = null,
    val historyItems: List<UserCenterItem> = emptyList(),
    val historyNextPageUrl: String? = null,
    val membershipInfo: MembershipInfo = MembershipInfo(),
    val membershipPlans: List<MembershipPlan> = emptyList(),
    val membershipSignInInfo: MembershipSignInInfo = MembershipSignInInfo(),
    val membershipPointLogs: List<PointLogItem> = emptyList(),
    val updateInfo: AppUpdateInfo? = null,
    val hasCrashLog: Boolean = false,
    val latestCrashLog: String = ""
)
