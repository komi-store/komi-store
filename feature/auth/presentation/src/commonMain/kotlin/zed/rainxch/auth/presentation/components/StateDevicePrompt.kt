package zed.rainxch.auth.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import zed.rainxch.auth.presentation.AuthenticationAction
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.auth_check_status
import zed.rainxch.githubstore.core.presentation.res.auth_polling_status
import zed.rainxch.githubstore.core.presentation.res.auth_rate_limited
import zed.rainxch.githubstore.core.presentation.res.copy_code
import zed.rainxch.githubstore.core.presentation.res.enter_code_on_github
import zed.rainxch.githubstore.core.presentation.res.ic_github
import zed.rainxch.githubstore.core.presentation.res.open_github

@Composable
internal fun StateDevicePrompt(
    authState: AuthLoginState.DevicePrompt,
    copied: Boolean,
    info: String?,
    isPolling: Boolean,
    pollIntervalSec: Int,
    onAction: (AuthenticationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        KomiSurface(
            modifier = Modifier.fillMaxWidth(),
            paper = KomiSurfacePaper.Surface,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KomiText(
                    text = stringResource(Res.string.enter_code_on_github),
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(14.dp))

                UserCodeRow(
                    authState = authState,
                    copied = copied,
                    onAction = onAction,
                )

                if (info != null) {
                    Spacer(Modifier.height(10.dp))

                    KomiText(
                        text = info,
                        role = KomiTextRole.Label,
                        color = colors.primary,
                        textAlign = TextAlign.Center,
                    )
                }

                if (authState.remainingSeconds > 0) {
                    Spacer(Modifier.height(16.dp))

                    CountdownRow(authState = authState)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        PrimaryPillButton(
            text = stringResource(Res.string.open_github),
            leadingIcon = vectorResource(Res.drawable.ic_github),
            onClick = { onAction(AuthenticationAction.OpenGitHub(authState.start)) },
        )

        Spacer(Modifier.height(10.dp))

        KomiButton(
            onClick = { onAction(AuthenticationAction.PollNow) },
            label =
                if (isPolling) {
                    stringResource(Res.string.auth_polling_status)
                } else {
                    stringResource(Res.string.auth_check_status)
                },
            variant = KomiButtonVariant.Outline,
            size = KomiButtonSize.Md,
            fullWidth = true,
            enabled = !isPolling,
            loading = isPolling,
            leadingIcon = if (isPolling) null else Icons.Default.Refresh,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
        )

        if (pollIntervalSec > 0) {
            Spacer(Modifier.height(8.dp))

            KomiText(
                text = stringResource(Res.string.auth_rate_limited, pollIntervalSec),
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(2f))
    }
}

@Composable
private fun UserCodeRow(
    authState: AuthLoginState.DevicePrompt,
    copied: Boolean,
    onAction: (AuthenticationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        val type = LocalPersonality.current.type
        // Typed by hand on another device, so it stays monospace at screen-title size.
        BasicText(
            text = authState.start.userCode,
            style =
                type.mono.copy(
                    fontSize = type.screenTitle.fontSize,
                    lineHeight = type.screenTitle.lineHeight,
                    color = colors.onSurface,
                ),
        )

        Spacer(Modifier.width(12.dp))

        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(shape.corner))
                    .background(colors.primaryTint)
                    .clickable {
                        onAction(AuthenticationAction.CopyCode(authState.start))
                    },
            contentAlignment = Alignment.Center,
        ) {
            val motion = LocalPersonality.current.motion
            AnimatedContent(
                targetState = copied,
                transitionSpec = {
                    (scaleIn(motion.fastSpatial()) + fadeIn()) togetherWith
                        (scaleOut() + fadeOut())
                },
                label = "copy_icon",
            ) { isCopied ->
                KomiIcon(
                    imageVector = if (isCopied) Icons.Default.DoneAll else Icons.Default.ContentCopy,
                    contentDescription = stringResource(Res.string.copy_code),
                    modifier = Modifier.size(18.dp),
                    tint = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun CountdownRow(
    authState: AuthLoginState.DevicePrompt,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    val animatedProgress by animateFloatAsState(
        targetValue = authState.progressFraction,
        animationSpec = LocalPersonality.current.motion.long(),
        label = "countdown_progress",
    )
    val progressColor by animateColorAsState(
        targetValue = if (authState.isUrgent) colors.error else colors.primary,
        animationSpec = LocalPersonality.current.motion.defaultEffects(),
        label = "progress_color",
    )
    val timerColor by animateColorAsState(
        targetValue = if (authState.isUrgent) colors.error else colors.onSurfaceVariant,
        animationSpec = LocalPersonality.current.motion.defaultEffects(),
        label = "timer_color",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        KomiLinearProgress(
            progress = { animatedProgress },
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(shape.cornerSmall)),
            color = progressColor,
        )

        Spacer(Modifier.width(12.dp))

        KomiText(
            text = authState.formattedTimer,
            role = KomiTextRole.Mono,
            color = timerColor,
        )
    }
}
