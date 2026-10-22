package zed.rainxch.githubstore.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.error.RateLimitInfo
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.rate_limit_close
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import zed.rainxch.githubstore.core.presentation.res.rate_limit_ok
import zed.rainxch.githubstore.core.presentation.res.rate_limit_resets_in_minutes
import zed.rainxch.githubstore.core.presentation.res.rate_limit_sign_in
import zed.rainxch.githubstore.core.presentation.res.rate_limit_tip_sign_in
import zed.rainxch.githubstore.core.presentation.res.rate_limit_used_all
import zed.rainxch.githubstore.core.presentation.res.rate_limit_used_all_free

@Composable
fun RateLimitDialog(
    rateLimitInfo: RateLimitInfo,
    isAuthenticated: Boolean,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
) {
    val timeUntilReset =
        remember(rateLimitInfo) {
            rateLimitInfo.timeUntilReset().inWholeMinutes.toInt()
        }

    val colors = LocalPersonality.current.colors
    KomiDialog(
        onDismissRequest = onDismiss,
        icon = {
            KomiIcon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.error,
            )
        },
        title = {
            KomiText(
                text = stringResource(Res.string.rate_limit_exceeded),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text =
                        if (isAuthenticated) {
                            stringResource(
                                Res.string.rate_limit_used_all,
                                rateLimitInfo.limit,
                            )
                        } else {
                            stringResource(
                                Res.string.rate_limit_used_all_free,
                                60,
                            )
                        },
                    role = KomiTextRole.Body,
                    color = colors.outline,
                )

                KomiText(
                    text =
                        stringResource(
                            Res.string.rate_limit_resets_in_minutes,
                            timeUntilReset,
                        ),
                    role = KomiTextRole.Label,
                    color = colors.onSurface,
                )

                if (!isAuthenticated) {
                    Spacer(modifier = Modifier.height(8.dp))

                    KomiText(
                        text = stringResource(Res.string.rate_limit_tip_sign_in),
                        role = KomiTextRole.Body,
                        color = colors.primary,
                    )
                }
            }
        },
        confirmButton = {
            if (!isAuthenticated) {
                KomiButton(
                    onClick = onSignIn,
                    label = stringResource(Res.string.rate_limit_sign_in),
                    variant = KomiButtonVariant.Primary,
                    size = KomiButtonSize.Sm,
                )
            } else {
                KomiButton(
                    onClick = onDismiss,
                    label = stringResource(Res.string.rate_limit_ok),
                    variant = KomiButtonVariant.Primary,
                    size = KomiButtonSize.Sm,
                )
            }
        },
        dismissButton = {
            KomiButton(
                onClick = onDismiss,
                label = stringResource(Res.string.rate_limit_close),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
                modifier = Modifier.testTag(KomiTestTags.RATE_LIMIT_DISMISS),
            )
        },
    )
}

@Preview
@Composable
fun RateLimitDialogPreview() {
    PersonalityPreview {
        RateLimitDialog(
            rateLimitInfo =
                RateLimitInfo(
                    limit = 1000,
                    remaining = 2000,
                    resetTimestamp = 0L,
                ),
            isAuthenticated = false,
            onDismiss = {
            },
            onSignIn = {
            },
        )
    }
}
