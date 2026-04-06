package top.jlen.vod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import top.jlen.vod.data.MembershipInfo
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.MembershipSignInInfo

@Composable
internal fun MembershipPaneV2(
    isLoading: Boolean,
    info: MembershipInfo,
    plans: List<MembershipPlan>,
    signInInfo: MembershipSignInInfo,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit,
    onSignIn: () -> Unit,
    onOpenPointLogs: () -> Unit
) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    LegacyMembershipPaneV2(
        isLoading = isLoading,
        info = info,
        plans = plans,
        signInInfo = signInInfo,
        isActionLoading = isActionLoading,
        onUpgrade = onUpgrade,
        onSignIn = onSignIn,
        onOpenPointLogs = onOpenPointLogs
    )
}

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
