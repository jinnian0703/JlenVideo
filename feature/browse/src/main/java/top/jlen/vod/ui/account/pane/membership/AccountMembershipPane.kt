package top.jlen.vod.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    message: String? = null,
    onUpgrade: (MembershipPlan) -> Unit,
    onSignIn: () -> Unit,
    onOpenPointLogs: () -> Unit
) {
    if (isLoading && plans.isEmpty() && info.groupName.isBlank()) {
        LoadingPane("会员信息加载中...")
        return
    }

    val signInSuccessMessage = message
        ?.takeIf { it.contains("签到成功") || it.contains("获得") && it.contains("积分") }
        ?.trim()
        .orEmpty()
    val shouldHighlightPoints = signInSuccessMessage.isNotBlank() || signInInfo.rewardPoints.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (signInSuccessMessage.isNotBlank()) {
            MembershipSuccessBanner(message = signInSuccessMessage)
        }

        MembershipSummaryCard(
            info = info,
            signInInfo = signInInfo,
            highlightPoints = shouldHighlightPoints,
            onOpenPointLogs = onOpenPointLogs
        )

        MembershipSignInCard(
            signInInfo = signInInfo,
            isActionLoading = isActionLoading,
            onSignIn = onSignIn
        )

        if (plans.isEmpty()) {
            EmptyPane("暂无可升级套餐")
        } else {
            plans.forEach { plan ->
                MembershipPlanCard(
                    plan = plan,
                    isActionLoading = isActionLoading,
                    onUpgrade = onUpgrade
                )
            }
        }
    }
}

@Composable
private fun MembershipSuccessBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Accent.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Accent.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(UiPalette.Accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = UiPalette.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.Ink,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MembershipSummaryCard(
    info: MembershipInfo,
    signInInfo: MembershipSignInInfo,
    highlightPoints: Boolean,
    onOpenPointLogs: () -> Unit
) {
    val pointTextColor by animateColorAsState(
        targetValue = if (highlightPoints) UiPalette.Accent else UiPalette.Ink,
        animationSpec = tween(durationMillis = 420),
        label = "membershipPointColor"
    )
    var animatePoints by remember(signInInfo.rewardPoints, highlightPoints) { mutableStateOf(false) }
    LaunchedEffect(signInInfo.rewardPoints, highlightPoints) {
        if (highlightPoints) {
            animatePoints = true
            delay(900)
            animatePoints = false
        }
    }

    Card(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "会员信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
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

            MembershipSummaryRow("当前分组", info.groupName.ifBlank { "普通会员" })
            MembershipSummaryRow(
                label = "剩余积分",
                value = info.points.ifBlank { "--" },
                valueColor = pointTextColor,
                highlight = animatePoints
            )
            MembershipSummaryRow("到期时间", info.expiry.ifBlank { "--" })
            signInRangeHint(signInInfo)?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = UiPalette.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun MembershipSummaryRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = UiPalette.Ink,
    highlight: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = UiPalette.TextSecondary
        )
        Text(
            text = value,
            modifier = Modifier.scale(if (highlight) 1.04f else 1f),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MembershipSignInCard(
    signInInfo: MembershipSignInInfo,
    isActionLoading: Boolean,
    onSignIn: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = if (signInInfo.signedToday) {
                            UiPalette.Accent.copy(alpha = 0.10f)
                        } else {
                            UiPalette.SurfaceSoft
                        },
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (signInInfo.signedToday) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.AutoAwesome
                    },
                    contentDescription = null,
                    tint = UiPalette.Accent,
                    modifier = Modifier.size(20.dp)
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
                Text(
                    text = membershipRewardHint(signInInfo),
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

            if (!signInInfo.signedToday) {
                Button(
                    onClick = onSignIn,
                    enabled = signInInfo.enabled && !isActionLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UiPalette.Accent,
                        contentColor = UiPalette.AccentText,
                        disabledContainerColor = UiPalette.SurfaceSoft,
                        disabledContentColor = UiPalette.TextMuted
                    )
                ) {
                    Text(if (isActionLoading) "处理中..." else "立即签到")
                }
            }
        }
    }
}

private fun membershipRewardHint(signInInfo: MembershipSignInInfo): String =
    when {
        signInInfo.signedToday && signInInfo.rewardPoints.isNotBlank() ->
            "今日获得 ${signInInfo.rewardPoints} 积分"
        signInInfo.rewardMinPoints.isNotBlank() && signInInfo.rewardMaxPoints.isNotBlank() ->
            "签到可得 ${signInInfo.rewardMinPoints}-${signInInfo.rewardMaxPoints} 积分"
        signInInfo.rewardMinPoints.isNotBlank() ->
            "签到可得 ${signInInfo.rewardMinPoints} 积分起"
        signInInfo.signedToday ->
            "今日签到已完成"
        signInInfo.enabled ->
            "完成签到即可领取积分奖励"
        else ->
            "当前站点未开启签到功能"
    }

private fun signInRangeHint(signInInfo: MembershipSignInInfo): String? =
    when {
        signInInfo.rewardMinPoints.isNotBlank() && signInInfo.rewardMaxPoints.isNotBlank() ->
            "签到可得 ${signInInfo.rewardMinPoints}-${signInInfo.rewardMaxPoints} 积分"
        signInInfo.rewardMinPoints.isNotBlank() ->
            "签到可得 ${signInInfo.rewardMinPoints} 积分起"
        else -> null
    }

@Composable
private fun MembershipPlanCard(
    plan: MembershipPlan,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit
) {
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
