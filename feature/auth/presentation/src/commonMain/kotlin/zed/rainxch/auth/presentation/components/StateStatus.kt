package zed.rainxch.auth.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.auth.presentation.AuthenticationAction
import zed.rainxch.auth.presentation.mapper.hintRes
import zed.rainxch.auth.presentation.mapper.titleRes
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.continue_as_guest
import zed.rainxch.githubstore.core.presentation.res.redirecting_message
import zed.rainxch.githubstore.core.presentation.res.signed_in
import zed.rainxch.githubstore.core.presentation.res.try_again
import zed.rainxch.githubstore.core.presentation.res.waiting_for_authorization

@Composable
internal fun StatePending(modifier: Modifier = Modifier) {
    val colors = LocalPersonality.current.colors

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KomiCircularProgress(modifier = Modifier.size(56.dp))

        Spacer(Modifier.height(20.dp))

        KomiText(
            text = stringResource(Res.string.waiting_for_authorization),
            role = KomiTextRole.CardTitle,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun StateLoggedIn(modifier: Modifier = Modifier) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val checkmarkVisibility = remember { MutableTransitionState(false).apply { targetState = true } }

        AnimatedVisibility(
            visibleState = checkmarkVisibility,
            enter = scaleIn(LocalPersonality.current.motion.fastSpatial()) + fadeIn(),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .background(colors.primaryTint),
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colors.primary,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        KomiText(
            text = stringResource(Res.string.signed_in),
            role = KomiTextRole.CardTitle,
            color = colors.onBackground,
        )

        Spacer(Modifier.height(6.dp))

        KomiText(
            text = stringResource(Res.string.redirecting_message),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StateError(
    authState: AuthLoginState.Error,
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.errorTint)
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KomiIcon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = colors.error,
                )

                Spacer(Modifier.height(12.dp))

                KomiText(
                    text = stringResource(authState.error.titleRes()),
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                KomiText(
                    text = stringResource(authState.error.hintRes()),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        PrimaryPillButton(
            text = stringResource(Res.string.try_again),
            onClick = { onAction(AuthenticationAction.StartLogin) },
        )

        Spacer(Modifier.height(6.dp))

        KomiButton(
            onClick = { onAction(AuthenticationAction.SkipLogin) },
            label = stringResource(Res.string.continue_as_guest),
            variant = KomiButtonVariant.Text,
            size = KomiButtonSize.Sm,
        )

        Spacer(Modifier.weight(2f))
    }
}
