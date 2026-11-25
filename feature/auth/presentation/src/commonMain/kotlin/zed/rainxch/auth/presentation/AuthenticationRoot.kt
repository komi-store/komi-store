package zed.rainxch.auth.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.auth.presentation.components.PatSignInSheet
import zed.rainxch.auth.presentation.components.StateDevicePrompt
import zed.rainxch.auth.presentation.components.StateError
import zed.rainxch.auth.presentation.components.StateLoggedIn
import zed.rainxch.auth.presentation.components.StateLoggedOut
import zed.rainxch.auth.presentation.components.StatePending
import zed.rainxch.auth.presentation.mapper.messageRes
import zed.rainxch.auth.presentation.model.AuthError
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.auth.presentation.model.GithubDeviceStartUi
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.constrainedContentWidth
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.app_icon

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
private fun AuthenticationScreen(
    state: AuthenticationState,
    onAction: (AuthenticationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = LocalPersonality.current.shape

    KomiScaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .constrainedContentWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))

                val iconScale by animateFloatAsState(
                    targetValue =
                        when (state.loginState) {
                            is AuthLoginState.LoggedIn -> 0.92f
                            is AuthLoginState.Error -> 0.96f
                            else -> 1f
                        },
                    animationSpec = LocalPersonality.current.motion.fastSpatial(),
                    label = "icon_scale",
                )

                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(96.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }.clip(RoundedCornerShape(shape.corner)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(Modifier.height(24.dp))

                val motion = LocalPersonality.current.motion
                AnimatedContent(
                    targetState = state.loginState,
                    transitionSpec = {
                        val enter =
                            fadeIn(motion.defaultEffects()) +
                                slideInVertically(
                                    animationSpec = motion.defaultSpatial(),
                                    initialOffsetY = { it / 5 },
                                )
                        val exit = fadeOut(motion.fastEffects())
                        enter togetherWith exit
                    },
                    contentKey = { it::class },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    label = "auth_state",
                ) { authState ->
                    when (authState) {
                        is AuthLoginState.LoggedOut -> {
                            StateLoggedOut(
                                isAdvancedAuthVisible = state.isAdvancedAuthVisible,
                                onAction = onAction,
                            )
                        }

                        is AuthLoginState.DevicePrompt -> {
                            StateDevicePrompt(
                                authState = authState,
                                copied = state.copied,
                                info = state.info,
                                isPolling = state.isPolling,
                                pollIntervalSec = state.pollIntervalSec,
                                onAction = onAction,
                            )
                        }

                        is AuthLoginState.Pending -> {
                            StatePending()
                        }

                        is AuthLoginState.LoggedIn -> {
                            StateLoggedIn()
                        }

                        is AuthLoginState.Error -> {
                            StateError(authState = authState, onAction = onAction)
                        }
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

@Preview
@Composable
private fun PreviewError() {
    PersonalityPreview {
        AuthenticationScreen(
            state =
                AuthenticationState(
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
            state =
                AuthenticationState(
                    loginState =
                        AuthLoginState.DevicePrompt(
                            start =
                                GithubDeviceStartUi(
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
