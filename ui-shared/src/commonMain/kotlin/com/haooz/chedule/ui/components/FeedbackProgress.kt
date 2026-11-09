package com.haooz.chedule.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.platform.FeedbackKind
import com.haooz.chedule.i18n.FeedbackStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一条反馈走过的阶段。
 *
 * 服务端的 9 个状态压到这 5 段上：`reviewing` 并入首段（都还没排期），
 * `done` 并入「待发版」（代码做完但用户还拿不到，对用户是同一件事）。
 * `rejected` / `duplicate` 不画进度 —— 那是终止态。
 */
enum class FeedbackStage(val index: Int) {
    Received(0),
    Planned(1),
    InProgress(2),
    PendingRelease(3),
    Released(4),
    Closed(-1),
    ;

    fun label(kind: FeedbackKind): String = when (this) {
        Received -> FeedbackStr.stageReceived()
        Planned -> FeedbackStr.stagePlanned()
        InProgress -> if (kind == FeedbackKind.Bug) FeedbackStr.stageFixing() else FeedbackStr.stageInProgress()
        PendingRelease -> FeedbackStr.stagePendingRelease()
        Released -> FeedbackStr.stageReleased()
        Closed -> FeedbackStr.stageClosed()
    }

    companion object {
        val steps: List<FeedbackStage> = listOf(Received, Planned, InProgress, PendingRelease, Released)

        fun of(status: String): FeedbackStage = when (status) {
            "received", "reviewing" -> Received
            "planned" -> Planned
            "in_progress" -> InProgress
            "done", "pending_release" -> PendingRelease
            "released" -> Released
            "rejected", "duplicate" -> Closed
            else -> Received
        }
    }
}

/**
 * 阶段主色。每个阶段一档：灰（已收到）→ 蓝（已排期）→ 橙（开发中）→ 紫（待发版）→ 绿（已发布）。
 *
 * 主题里只有 primary / secondary 这些角色色，凑不出五档语义色，所以这里写死一组
 * 浅/深两套值（项目里其他需要语义色的地方也是这个做法）。只用于进度条，不参与正文。
 */
@Composable
private fun stageColor(stage: FeedbackStage): Color {
    val dark = isAppDarkTheme()
    return when (stage) {
        FeedbackStage.Received -> if (dark) Color(0xFF9A9AA0) else Color(0xFF8C8C92)
        FeedbackStage.Planned -> if (dark) Color(0xFF7EB0FF) else Color(0xFF3D7DE0)
        FeedbackStage.InProgress -> if (dark) Color(0xFFFFB454) else Color(0xFFE08919)
        FeedbackStage.PendingRelease -> if (dark) Color(0xFFB3A1FF) else Color(0xFF7559E8)
        FeedbackStage.Released -> if (dark) Color(0xFF5ACB78) else Color(0xFF2E9C52)
        FeedbackStage.Closed -> if (dark) Color(0xFF7A7A80) else Color(0xFF8C8C92)
    }
}

/**
 * 阶段进度条：五段胶囊拼成一条轨，走到的段填当前阶段色，没走到的留淡轨，
 * 右侧跟同色的阶段文字。
 *
 * 不再用「小圆点 + 10sp 标签」挤一排 —— 那个既看不出进度，字也太小。
 * 颜色只表达「现在在哪一段」，具体是哪一段由右侧文字说清。
 */
@Composable
fun FeedbackStageBar(
    status: String,
    kind: FeedbackKind,
    modifier: Modifier = Modifier,
) {
    val theme = MiuixTheme.colorScheme
    val stage = FeedbackStage.of(status)

    if (stage == FeedbackStage.Closed) {
        Text(
            text = FeedbackStr.stageClosedWithReason(FeedbackStr.statusLabel(status, kind)),
            style = MiuixTheme.textStyles.body2,
            color = theme.onSurfaceVariantSummary,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    val activeColor by animateColorAsState(stageColor(stage), tween(260), label = "feedbackStageColor")
    val trackColor = theme.onSurface.copy(alpha = 0.08f)
    val filled = stage.index + 1

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(FeedbackStage.steps.size) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i < filled) activeColor else trackColor),
                )
            }
        }
        Text(
            text = stage.label(kind),
            style = MiuixTheme.textStyles.body2,
            color = activeColor,
            modifier = Modifier.padding(start = 10.dp),
            maxLines = 1,
        )
    }
}

/** 类型标签：一行小字，不占胶囊 */
@Composable
fun FeedbackKindLabel(
    kind: FeedbackKind,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (kind == FeedbackKind.Bug) FeedbackStr.typeBug() else FeedbackStr.typeFeature(),
        modifier = modifier,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
    )
}
