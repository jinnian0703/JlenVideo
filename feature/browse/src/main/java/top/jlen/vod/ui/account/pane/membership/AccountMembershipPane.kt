package top.jlen.vod.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import top.jlen.vod.data.MembershipInfo
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.MembershipSignInInfo
import top.jlen.vod.data.PointLogItem
import top.jlen.vod.data.PointTrendPoint

@Composable
internal fun MembershipPaneV2(
    isLoading: Boolean,
    info: MembershipInfo,
    plans: List<MembershipPlan>,
    signInInfo: MembershipSignInInfo,
    pointLogs: List<PointLogItem>,
    isActionLoading: Boolean,
    message: String? = null,
    onUpgrade: (MembershipPlan) -> Unit,
    onRedeemCard: (String, String) -> Unit,
    onSignIn: () -> Unit,
    onOpenPointLogs: () -> Unit
) {
    if (isLoading && plans.isEmpty() && info.groupName.isBlank()) {
        LoadingPane("会员信息加载中...")
        return
    }

    val shouldHighlightPoints = message?.isMembershipSuccessMessage() == true || signInInfo.rewardPoints.isNotBlank()
    val trendPoints = remember(pointLogs) { buildPointTrendPoints(pointLogs, days = 30) }
    var showRechargeDialog by remember { mutableStateOf(false) }
    var plansExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

        MembershipRechargeCard(
            isActionLoading = isActionLoading,
            onOpen = { showRechargeDialog = true }
        )

        MembershipTrendCard(points = trendPoints)

        MembershipPlansCard(
            plans = plans,
            expanded = plansExpanded,
            isActionLoading = isActionLoading,
            onToggleExpanded = { plansExpanded = !plansExpanded },
            onUpgrade = onUpgrade
        )
    }

    if (showRechargeDialog) {
        MembershipCardRechargeDialog(
            isActionLoading = isActionLoading,
            onDismiss = { showRechargeDialog = false },
            onConfirm = { cardNo, cardPassword ->
                showRechargeDialog = false
                onRedeemCard(cardNo, cardPassword)
            }
        )
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
        shape = RoundedCornerShape(UiDimens.CardRadius),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiDimens.CardPadding),
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
                    shape = RoundedCornerShape(UiDimens.ControlRadius),
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
    valueColor: Color = UiPalette.Ink,
    highlight: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiDimens.ControlRadius))
            .background(UiPalette.SurfaceSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp)
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
}

@Composable
private fun MembershipSignInCard(
    signInInfo: MembershipSignInInfo,
    isActionLoading: Boolean,
    onSignIn: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(UiDimens.CardRadius),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiDimens.CardPadding),
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
                        shape = RoundedCornerShape(UiDimens.ControlRadius)
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
                    modifier = Modifier.height(UiDimens.CompactButtonHeight),
                    shape = RoundedCornerShape(UiDimens.ControlRadius),
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

@Composable
private fun MembershipPlansCard(
    plans: List<MembershipPlan>,
    expanded: Boolean,
    isActionLoading: Boolean,
    onToggleExpanded: () -> Unit,
    onUpgrade: (MembershipPlan) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(UiDimens.CardRadius),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(UiPalette.Accent.copy(alpha = 0.10f), RoundedCornerShape(UiDimens.ControlRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Payments,
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
                        text = "会员套餐",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = if (plans.isEmpty()) {
                            "暂无可升级套餐"
                        } else {
                            "${plans.size} 个套餐"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }

                TextButton(
                    onClick = onToggleExpanded,
                    shape = RoundedCornerShape(UiDimens.ControlRadius),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (expanded) "收起" else "展开",
                        color = UiPalette.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (expanded) {
                if (plans.isEmpty()) {
                    MembershipPlansEmptyRow()
                } else {
                    plans.forEach { plan ->
                        MembershipPlanRow(
                            plan = plan,
                            isActionLoading = isActionLoading,
                            onUpgrade = onUpgrade
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MembershipRechargeCard(
    isActionLoading: Boolean,
    onOpen: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(UiDimens.CardRadius),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiDimens.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(UiPalette.Accent.copy(alpha = 0.10f), RoundedCornerShape(UiDimens.ControlRadius)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Payments,
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
                    text = "充值卡",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "输入卡号和密码兑换积分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
            }

            Button(
                onClick = onOpen,
                enabled = !isActionLoading,
                modifier = Modifier.height(UiDimens.CompactButtonHeight),
                shape = RoundedCornerShape(UiDimens.ControlRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiPalette.Accent,
                    contentColor = UiPalette.AccentText,
                    disabledContainerColor = UiPalette.SurfaceSoft,
                    disabledContentColor = UiPalette.TextMuted
                )
            ) {
                Text(if (isActionLoading) "处理中..." else "立即充值")
            }
        }
    }
}

@Composable
private fun MembershipPlansEmptyRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiDimens.ControlRadius))
            .background(UiPalette.SurfaceSoft)
            .border(BorderStroke(1.dp, UiPalette.BorderSoft), RoundedCornerShape(UiDimens.ControlRadius))
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "暂无可升级套餐",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
            Text(
                text = "当前站点暂未提供可购买的会员方案",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun MembershipCardRechargeDialog(
    isActionLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var cardNo by remember { mutableStateOf("") }
    var cardPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isActionLoading) onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "充值卡兑换",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = "输入卡号和密码完成兑换。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextSecondary
                    )
                }

                RechargeTextField(
                    value = cardNo,
                    onValueChange = {
                        cardNo = it
                        localError = null
                    },
                    label = "卡号",
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                )
                RechargeTextField(
                    value = cardPassword,
                    onValueChange = {
                        cardPassword = it
                        localError = null
                    },
                    label = "卡密密码",
                    password = true,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )

                localError?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(UiDimens.ControlRadius))
                            .background(UiPalette.Accent.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = UiPalette.Accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isActionLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(UiDimens.SecondaryButtonHeight),
                        shape = RoundedCornerShape(UiDimens.ControlRadius),
                        colors = ButtonDefaults.textButtonColors(contentColor = UiPalette.Accent)
                    ) {
                        Text("取消", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            when {
                                cardNo.trim().isBlank() -> localError = "请输入充值卡卡号"
                                cardPassword.trim().isBlank() -> localError = "请输入充值卡密码"
                                else -> onConfirm(cardNo, cardPassword)
                            }
                        },
                        enabled = !isActionLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(UiDimens.SecondaryButtonHeight),
                        shape = RoundedCornerShape(UiDimens.ControlRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.Accent,
                            contentColor = UiPalette.AccentText
                        )
                    ) {
                        Text(if (isActionLoading) "处理中..." else "确认充值")
                    }
                }
            }
        }
    }
}

@Composable
private fun RechargeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboardType: KeyboardType,
    imeAction: ImeAction
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        shape = RoundedCornerShape(UiDimens.ControlRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = UiPalette.Accent,
            unfocusedBorderColor = UiPalette.BorderSoft,
            focusedLabelColor = UiPalette.Accent,
            cursorColor = UiPalette.Accent
        )
    )
}

@Composable
private fun MembershipTrendCard(points: List<PointTrendPoint>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(UiDimens.CardRadius),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(UiDimens.ControlRadius))
                        .background(UiPalette.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                        contentDescription = null,
                        tint = UiPalette.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "近30天积分趋势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = UiPalette.Ink
                    )
                    Text(
                        text = "按最近 30 天积分净变化统计",
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }
            }

            if (points.isEmpty() || points.all { it.delta == 0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(UiDimens.ControlRadius))
                        .background(UiPalette.SurfaceSoft)
                        .padding(horizontal = 14.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "近30天暂无积分变动",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.TextMuted
                    )
                }
            } else {
                val totalDelta = points.sumOf { it.delta }
                val activeDays = points.count { it.delta != 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrendStatChip(
                        label = "净变化",
                        value = if (totalDelta > 0) "+$totalDelta" else totalDelta.toString()
                    )
                    TrendStatChip(label = "活跃天数", value = "$activeDays 天")
                }
                PointTrendChart(points = points)
            }
        }
    }
}

@Composable
private fun TrendStatChip(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(UiDimens.ControlRadius))
            .background(UiPalette.SurfaceSoft)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = UiPalette.TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Ink
            )
        }
    }
}

@Composable
private fun PointTrendChart(points: List<PointTrendPoint>) {
    val maxAbsDelta = points.maxOf { abs(it.delta) }.coerceAtLeast(1)
    val startLabel = points.firstOrNull()?.label.orEmpty()
    val middleLabel = points.getOrNull(points.lastIndex / 2)?.label.orEmpty()
    val endLabel = points.lastOrNull()?.label.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(UiDimens.ControlRadius))
                .background(UiPalette.SurfaceSoft)
        ) {
            val baseline = size.height * 0.58f
            val gap = size.width / points.size.coerceAtLeast(1)
            val barWidth = (gap * 0.52f).coerceAtLeast(4f)

            drawLine(
                color = UiPalette.BorderSoft.copy(alpha = 0.8f),
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 2f
            )

            points.forEachIndexed { index, point ->
                val ratio = point.delta.toFloat() / maxAbsDelta.toFloat()
                val barHeight = (size.height * 0.34f * abs(ratio)).coerceAtLeast(4f)
                val left = index * gap + (gap - barWidth) / 2f
                val top = if (point.delta >= 0) baseline - barHeight else baseline
                val color = if (point.delta >= 0) UiPalette.Accent else UiPalette.TextMuted
                drawRoundRect(
                    color = color.copy(alpha = 0.9f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = UiPalette.TextMuted)
            Text(middleLabel, style = MaterialTheme.typography.labelSmall, color = UiPalette.TextMuted)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = UiPalette.TextMuted)
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

private fun String.isMembershipSuccessMessage(): Boolean =
    contains("签到成功") ||
        contains("充值成功") ||
        contains("兑换成功") ||
        contains("会员信息已更新") ||
        (contains("获得") && contains("积分")) ||
        (contains("增加") && contains("积分"))

@Composable
private fun MembershipPlanRow(
    plan: MembershipPlan,
    isActionLoading: Boolean,
    onUpgrade: (MembershipPlan) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiDimens.ControlRadius))
            .background(UiPalette.SurfaceSoft)
            .border(BorderStroke(1.dp, UiPalette.BorderSoft), RoundedCornerShape(UiDimens.ControlRadius))
            .padding(14.dp),
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
            modifier = Modifier.height(UiDimens.CompactButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (isActionLoading) "处理中..." else "升级")
        }
    }
}

private fun buildPointTrendPoints(
    pointLogs: List<PointLogItem>,
    days: Int
): List<PointTrendPoint> {
    if (days <= 0) return emptyList()
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }
    val labelFormatter = SimpleDateFormat("MM-dd", Locale.getDefault())
    val startDate = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -(days - 1))
    }
    val endDateMillis = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
    val pointsByDate = pointLogs.fold(linkedMapOf<String, Int>()) { acc, log ->
        val date = parsePointLogDate(log)
        val delta = parsePointLogDelta(log)
        if (date != null && date.time >= startDate.timeInMillis && date.time <= endDateMillis && delta != null) {
            val dateKey = dateFormatter.format(date)
            acc[dateKey] = (acc[dateKey] ?: 0) + delta
        }
        acc
    }
    val currentDate = startDate.clone() as Calendar
    return (0 until days).map { offset ->
        if (offset > 0) currentDate.add(Calendar.DAY_OF_YEAR, 1)
        val date = currentDate.time
        val dateKey = dateFormatter.format(date)
        PointTrendPoint(
            date = dateKey,
            delta = pointsByDate[dateKey] ?: 0,
            label = labelFormatter.format(date)
        )
    }
}

private fun parsePointLogDate(log: PointLogItem): Date? {
    val candidates = listOf(log.timeText, log.time)
    val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }
    candidates.forEach { raw ->
        val text = raw.trim()
        if (text.isBlank()) return@forEach
        dateRegex.find(text)?.value?.let { value ->
            return runCatching { dateFormatter.parse(value) }.getOrNull()
        }
        text.toLongOrNull()?.let { epoch ->
            val millis = if (text.length <= 10) epoch * 1000 else epoch
            return Date(millis)
        }
    }
    return null
}

private fun parsePointLogDelta(log: PointLogItem): Int? {
    val raw = (log.pointsText.ifBlank { log.points })
        .replace("积分", "")
        .replace(" ", "")
        .trim()
    val numeric = Regex("""[-+]?\d+""").find(raw)?.value?.toIntOrNull()
        ?: return null
    return if (log.isIncome) abs(numeric) else -abs(numeric)
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
