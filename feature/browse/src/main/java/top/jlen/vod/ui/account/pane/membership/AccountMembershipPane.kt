package top.jlen.vod.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        border = BorderStroke(1.dp, UiPalette.Border),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "签到调试",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
            Text(
                text = "enabled=${signInInfo.enabled}",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
            Text(
                text = "signedToday=${signInInfo.signedToday}",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
            Text(
                text = "rewardPoints=${signInInfo.rewardPoints.ifBlank { "<empty>" }}",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
            Text(
                text = "pointsMin=${signInInfo.rewardMinPoints.ifBlank { "<empty>" }}",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
            Text(
                text = "pointsMax=${signInInfo.rewardMaxPoints.ifBlank { "<empty>" }}",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
            Text(
                text = "signedAt=${signInInfo.signedAt.ifBlank { "<empty>" }}",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
        }
    }
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
