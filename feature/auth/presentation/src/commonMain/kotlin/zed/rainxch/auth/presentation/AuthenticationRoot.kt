package zed.rainxch.auth.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.auth.presentation.mapper.hintRes
import zed.rainxch.auth.presentation.mapper.messageRes
import zed.rainxch.auth.presentation.mapper.titleRes
import zed.rainxch.auth.presentation.model.AuthError
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.auth.presentation.model.GithubDeviceStartUi
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.constrainedContentWidth
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.auth_use_device_code_instead
import zed.rainxch.githubstore.core.presentation.res.app_icon
import zed.rainxch.githubstore.core.presentation.res.auth_check_status
import zed.rainxch.githubstore.core.presentation.res.auth_polling_status
import zed.rainxch.githubstore.core.presentation.res.auth_rate_limited
import zed.rainxch.githubstore.core.presentation.res.auth_hide_signin_options
import zed.rainxch.githubstore.core.presentation.res.auth_more_signin_options
import zed.rainxch.githubstore.core.presentation.res.continue_as_guest
import zed.rainxch.githubstore.core.presentation.res.copy_code
import zed.rainxch.githubstore.core.presentation.res.enter_code_on_github
import zed.rainxch.githubstore.core.presentation.res.ic_github
import zed.rainxch.githubstore.core.presentation.res.more_requests
import zed.rainxch.githubstore.core.presentation.res.more_requests_description
import zed.rainxch.githubstore.core.presentation.res.open_github
import zed.rainxch.githubstore.core.presentation.res.pat_cancel
import zed.rainxch.githubstore.core.presentation.res.pat_input_label
import zed.rainxch.githubstore.core.presentation.res.pat_input_placeholder
import zed.rainxch.githubstore.core.presentation.res.pat_open_settings
import zed.rainxch.githubstore.core.presentation.res.pat_sheet_description
import zed.rainxch.githubstore.core.presentation.res.pat_sheet_title
import zed.rainxch.githubstore.core.presentation.res.pat_submit
import zed.rainxch.githubstore.core.presentation.res.pat_use_token_instead
import zed.rainxch.githubstore.core.presentation.res.redirecting_message
import zed.rainxch.githubstore.core.presentation.res.sign_in_with_github
import zed.rainxch.githubstore.core.presentation.res.signed_in
import zed.rainxch.githubstore.core.presentation.res.try_again
import zed.rainxch.githubstore.core.presentation.res.unlock_full_experience
import zed.rainxch.githubstore.core.presentation.res.waiting_for_authorization

@Composable
fun AuthenticationRoot(
    onNavigateToHome: () -> Unit,
    viewModel: AuthenticationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAction(AuthenticationAction.OnResumed)
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            AuthenticationEvents.OnNavigateToMain -> onNavigateToHome()
        }
    }

    AuthenticationScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun AuthenticationScreen(
    state: AuthenticationState,
    onAction: (AuthenticationAction) -> Unit,
) {
    val shape = LocalPersonality.current.shape

    KomiScaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .constrainedContentWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))

                val iconScale by animateFloatAsState(
                    targetValue = when (state.loginState) {
                        is AuthLoginState.LoggedIn -> 0.92f
                        is AuthLoginState.Error -> 0.96f
                        else -> 1f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "icon_scale",
                )

                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(RoundedCornerShape(shape.corner)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(Modifier.height(24.dp))

                AnimatedContent(
                    targetState = state.loginState,
                    transitionSpec = {
                        val enter = fadeIn(tween(350)) + slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            initialOffsetY = { it / 5 },
                        )
                        val exit = fadeOut(tween(200))
                        enter togetherWith exit
                    },
                    contentKey = { it::class },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = "auth_state",
                ) { authState ->
                    when (authState) {
                        is AuthLoginState.LoggedOut -> StateLoggedOut(
                            isAdvancedAuthVisible = state.isAdvancedAuthVisible,
                            onAction = onAction,
                        )

                        is AuthLoginState.DevicePrompt -> StateDevicePrompt(
                            state = state,
                            authState = authState,
                            onAction = onAction,
                        )

                        is AuthLoginState.Pending -> StatePending()

                        is AuthLoginState.LoggedIn -> StateLoggedIn()

                        is AuthLoginState.Error -> StateError(authState = authState, onAction = onAction)
                    }
                }
            }

            if (state.isPatSheetVisible) {
                PatSignInSheet(
                    input = state.patInput,
                    error = state.patError?.let { stringResource(it.messageRes()) },
                    isSubmitting = state.isPatSubmitting,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun StateLoggedOut(
    isAdvancedAuthVisible: Boolean,
    onAction: (AuthenticationAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KomiText(
            text = stringResource(Res.string.unlock_full_experience),
            role = KomiTextRole.Display,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            uppercase = false,
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
            label = if (isAdvancedAuthVisible) {
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
private fun BenefitsCard() {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
        paper = KomiSurfacePaper.Surface,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(shape.corner))
                    .background(colors.primary.copy(alpha = 0.14f)),
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
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    uppercase = false,
                )

                KomiText(
                    text = stringResource(Res.string.more_requests_description),
                    role = KomiTextRole.Body,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    uppercase = false,
                )
            }
        }
    }
}

@Composable
private fun StateDevicePrompt(
    state: AuthenticationState,
    authState: AuthLoginState.DevicePrompt,
    onAction: (AuthenticationAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Column(
        modifier = Modifier.fillMaxSize(),
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
                    role = KomiTextRole.Label,
                    color = colors.onSurfaceVariant,
                    uppercase = false,
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KomiText(
                        text = authState.start.userCode,
                        role = KomiTextRole.Mono,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        uppercase = false,
                    )

                    Spacer(Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(shape.corner))
                            .background(colors.primary.copy(alpha = 0.14f))
                            .clickable {
                                onAction(AuthenticationAction.CopyCode(authState.start))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = state.copied,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
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

                state.info?.let { info ->
                    Spacer(Modifier.height(10.dp))

                    KomiText(
                        text = info,
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        textAlign = TextAlign.Center,
                        uppercase = false,
                    )
                }

                if (authState.remainingSeconds > 0) {
                    Spacer(Modifier.height(16.dp))

                    val animatedProgress by animateFloatAsState(
                        targetValue = authState.progressFraction,
                        animationSpec = tween(900),
                        label = "countdown_progress",
                    )
                    val progressColor by animateColorAsState(
                        targetValue = if (authState.isUrgent) colors.error else colors.primary,
                        animationSpec = tween(500),
                        label = "progress_color",
                    )
                    val timerColor by animateColorAsState(
                        targetValue = if (authState.isUrgent) colors.error else colors.onSurfaceVariant,
                        animationSpec = tween(500),
                        label = "timer_color",
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        KomiLinearProgress(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(shape.cornerSmall)),
                            color = progressColor,
                        )

                        Spacer(Modifier.width(12.dp))

                        KomiText(
                            text = authState.formattedTimer,
                            role = KomiTextRole.Mono,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = timerColor,
                            uppercase = false,
                        )
                    }
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
            label = if (state.isPolling) {
                stringResource(Res.string.auth_polling_status)
            } else {
                stringResource(Res.string.auth_check_status)
            },
            variant = KomiButtonVariant.Outline,
            size = KomiButtonSize.Md,
            fullWidth = true,
            enabled = !state.isPolling,
            loading = state.isPolling,
            leadingIcon = if (state.isPolling) null else Icons.Default.Refresh,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        )

        if (state.pollIntervalSec > 0) {
            Spacer(Modifier.height(8.dp))

            KomiText(
                text = stringResource(Res.string.auth_rate_limited, state.pollIntervalSec),
                role = KomiTextRole.Label,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                uppercase = false,
            )
        }

        Spacer(Modifier.weight(2f))
    }
}

@Composable
private fun StatePending() {
    val colors = LocalPersonality.current.colors

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KomiCircularProgress(modifier = Modifier.size(56.dp))

        Spacer(Modifier.height(20.dp))

        KomiText(
            text = stringResource(Res.string.waiting_for_authorization),
            role = KomiTextRole.Title,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            uppercase = false,
        )
    }
}

@Composable
private fun StateLoggedIn() {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val checkmarkVisibility = remember { MutableTransitionState(false).apply { targetState = true } }

        AnimatedVisibility(
            visibleState = checkmarkVisibility,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(shape.cornerSmall))
                    .background(colors.primary.copy(alpha = 0.14f)),
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
            role = KomiTextRole.Title,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
            uppercase = false,
        )

        Spacer(Modifier.height(6.dp))

        KomiText(
            text = stringResource(Res.string.redirecting_message),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            uppercase = false,
        )
    }
}

@Composable
private fun StateError(
    authState: AuthLoginState.Error,
    onAction: (AuthenticationAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        KomiSurface(
            modifier = Modifier.fillMaxWidth(),
            paper = KomiSurfacePaper.Surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.error.copy(alpha = 0.12f))
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
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    uppercase = false,
                )

                Spacer(Modifier.height(6.dp))

                KomiText(
                    text = stringResource(authState.error.hintRes()),
                    role = KomiTextRole.Body,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    uppercase = false,
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

@Composable
private fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    KomiButton(
        onClick = onClick,
        label = text,
        enabled = enabled,
        variant = KomiButtonVariant.Primary,
        size = KomiButtonSize.Lg,
        fullWidth = true,
        leadingIcon = leadingIcon,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    )
}

@Composable
private fun PatSignInSheet(
    input: String,
    error: String?,
    isSubmitting: Boolean,
    onAction: (AuthenticationAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors

    KomiSheet(
        onDismiss = { if (!isSubmitting) onAction(AuthenticationAction.DismissPatSheet) },
        placement = KomiSheetPlacement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.pat_sheet_title),
                role = KomiTextRole.Title,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                uppercase = false,
            )

            KomiText(
                text = stringResource(Res.string.pat_sheet_description),
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                uppercase = false,
            )

            KomiButton(
                onClick = { onAction(AuthenticationAction.OpenPatSettingsPage) },
                label = stringResource(Res.string.pat_open_settings),
                variant = KomiButtonVariant.Outline,
                size = KomiButtonSize.Md,
                fullWidth = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )

            KomiTextField(
                value = input,
                onValueChange = { onAction(AuthenticationAction.OnPatInputChanged(it)) },
                label = stringResource(Res.string.pat_input_label),
                placeholder = stringResource(Res.string.pat_input_placeholder),
                error = error,
                password = true,
                keyboardType = KeyboardType.Password,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KomiButton(
                    onClick = { onAction(AuthenticationAction.DismissPatSheet) },
                    label = stringResource(Res.string.pat_cancel),
                    enabled = !isSubmitting,
                    variant = KomiButtonVariant.Outline,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )

                KomiButton(
                    onClick = { onAction(AuthenticationAction.SubmitPat) },
                    label = stringResource(Res.string.pat_submit),
                    enabled = !isSubmitting && input.isNotBlank(),
                    loading = isSubmitting,
                    variant = KomiButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewError() {
    PersonalityPreview {
        AuthenticationScreen(
            state = AuthenticationState(
                loginState = AuthLoginState.Error(AuthError.Unreachable),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun PreviewLoggedOut() {
    PersonalityPreview(personality = classicPersonality()) {
        AuthenticationScreen(
            state = AuthenticationState(loginState = AuthLoginState.LoggedOut),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun PreviewDevicePrompt() {
    PersonalityPreview {
        AuthenticationScreen(
            state = AuthenticationState(
                loginState = AuthLoginState.DevicePrompt(
                    start = GithubDeviceStartUi(
                        deviceCode = "",
                        userCode = "2102-UHHUF",
                        verificationUri = "",
                        expiresInSec = 900,
                    ),
                    remainingSeconds = 847,
                    progressFraction = 847f / 900f,
                    formattedTimer = "14:07",
                ),
                copied = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun PreviewLoggedIn() {
    PersonalityPreview {
        AuthenticationScreen(
            state = AuthenticationState(loginState = AuthLoginState.LoggedIn),
            onAction = {},
        )
    }
}
