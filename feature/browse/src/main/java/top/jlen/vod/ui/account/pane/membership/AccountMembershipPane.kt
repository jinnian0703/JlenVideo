package top.jlen.vod.ui

import androidx.compose.runtime.Composable
import top.jlen.vod.data.MembershipInfo
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.MembershipSignInInfo
import top.jlen.vod.data.PointLogItem

@Composable
internal fun MembershipPaneV2(
    isLoading: Boolean,
    info: MembershipInfo,
    plans: List<MembershipPlan>,
    signInInfo: MembershipSignInInfo,
    pointLogs: List<PointLogItem>,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit,
    onSignIn: () -> Unit
) = LegacyMembershipPaneV2(
    isLoading = isLoading,
    info = info,
    plans = plans,
    signInInfo = signInInfo,
    pointLogs = pointLogs,
    isActionLoading = isActionLoading,
    onUpgrade = onUpgrade,
    onSignIn = onSignIn
)

@Composable
internal fun MembershipPane(
    isLoading: Boolean,
    info: MembershipInfo,
    plans: List<MembershipPlan>,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit
) = LegacyMembershipPane(
    isLoading = isLoading,
    info = info,
    plans = plans,
    isActionLoading = isActionLoading,
    onUpgrade = onUpgrade
)

internal fun String.toMembershipDuration(): String = toLegacyMembershipDuration()
