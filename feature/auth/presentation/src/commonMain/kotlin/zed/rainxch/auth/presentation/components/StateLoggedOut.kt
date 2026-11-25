package zed.rainxch.auth.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import zed.rainxch.auth.presentation.AuthenticationAction
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.auth_hide_signin_options
import zed.rainxch.githubstore.core.presentation.res.auth_more_signin_options
import zed.rainxch.githubstore.core.presentation.res.auth_use_device_code_instead
import zed.rainxch.githubstore.core.presentation.res.continue_as_guest
import zed.rainxch.githubstore.core.presentation.res.ic_github
import zed.rainxch.githubstore.core.presentation.res.more_requests
import zed.rainxch.githubstore.core.presentation.res.more_requests_description
import zed.rainxch.githubstore.core.presentation.res.pat_use_token_instead
import zed.rainxch.githubstore.core.presentation.res.sign_in_with_github
import zed.rainxch.githubstore.core.presentation.res.unlock_full_experience

@Composable
internal fun StateLoggedOut(
    isAdvancedAuthVisible: Boolean,
    onAction: (AuthenticationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KomiText(
            text = stringResource(Res.string.unlock_full_experience),
            role = KomiTextRole.Display,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        BenefitsCard()

        Spacer(Modifier.weight(1f))

        PrimaryPillButton(
            text = stringResource(Res.string.sign_in_with_github),
            leadingIcon = vectorResource(Res.drawable.ic_github),
            onClick = { onAction(AuthenticationAction.StartWebAuth) },
        )

        Spacer(Modifier.height(10.dp))

        KomiButton(
            onClick = { onAction(AuthenticationAction.SkipLogin) },
            label = stringResource(Res.string.continue_as_guest),
            variant = KomiButtonVariant.Text,
            size = KomiButtonSize.Sm,
        )

        KomiButton(
            onClick = {
                onAction(
                    if (isAdvancedAuthVisible) {
                        AuthenticationAction.DismissAdvancedAuth
                    } else {
                        AuthenticationAction.OpenAdvancedAuth
                    },
                )
            },
            label =
                if (isAdvancedAuthVisible) {
                    stringResource(Res.string.auth_hide_signin_options)
                } else {
                    stringResource(Res.string.auth_more_signin_options)
                },
            variant = KomiButtonVariant.Text,
            size = KomiButtonSize.Sm,
            trailingIcon = Icons.Default.ExpandMore,
        )

        AnimatedVisibility(visible = isAdvancedAuthVisible) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(4.dp))

                KomiButton(
                    onClick = { onAction(AuthenticationAction.OpenPatSheet) },
                    label = stringResource(Res.string.pat_use_token_instead),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )

                KomiButton(
                    onClick = { onAction(AuthenticationAction.StartLogin) },
                    label = stringResource(Res.string.auth_use_device_code_instead),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BenefitsCard(modifier: Modifier = Modifier) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        paper = KomiSurfacePaper.Surface,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(shape.corner))
                        .background(colors.primaryTint),
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    painter = painterResource(Res.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colors.primary,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                KomiText(
                    text = stringResource(Res.string.more_requests),
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                )

                KomiText(
                    text = stringResource(Res.string.more_requests_description),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
