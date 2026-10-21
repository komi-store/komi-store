package zed.rainxch.profile.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.core.presentation.utils.constrainedContentWidth
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.logout_success
import zed.rainxch.githubstore.core.presentation.res.profile_title
import zed.rainxch.profile.presentation.components.LogoutDialog
import zed.rainxch.profile.presentation.components.profileSections
import zed.rainxch.core.presentation.layout.withoutBottom

@Composable
fun ProfileRoot(
    onNavigateToDevProfile: (username: String) -> Unit,
    onNavigateToAuthentication: () -> Unit,
    onNavigateToStarredRepos: () -> Unit,
    onNavigateToFavouriteRepos: () -> Unit,
    onNavigateToRecentlyViewed: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToAnnouncements: () -> Unit,
    onNavigateToTweaks: () -> Unit,
    onNavigateToAbout: () -> Unit,
    hasUnreadAnnouncements: Boolean,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ProfileEvent.OnLogoutSuccessful -> {
                toastState.success(getString(Res.string.logout_success))
            }

            is ProfileEvent.OnLogoutError -> {
                toastState.danger(event.message)
            }
        }
    }

    ProfileScreen(
        state = state,
        hasUnreadAnnouncements = hasUnreadAnnouncements,
        onAction = { action ->
            when (action) {
                ProfileAction.OnLoginClick -> {
                    onNavigateToAuthentication()
                }

                ProfileAction.OnFavouriteReposClick -> {
                    onNavigateToFavouriteRepos()
                }

                ProfileAction.OnStarredReposClick -> {
                    onNavigateToStarredRepos()
                }

                is ProfileAction.OnRepositoriesClick -> {
                    onNavigateToDevProfile(action.username)
                }

                ProfileAction.OnRecentlyViewedClick -> {
                    onNavigateToRecentlyViewed()
                }

                ProfileAction.OnWhatsNewClick -> {
                    onNavigateToWhatsNew()
                }

                ProfileAction.OnAnnouncementsClick -> {
                    onNavigateToAnnouncements()
                }

                ProfileAction.OnTweaksClick -> {
                    onNavigateToTweaks()
                }

                ProfileAction.OnAboutClick -> {
                    onNavigateToAbout()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        toastState = toastState,
    )

    if (state.isLogoutDialogVisible) {
        LogoutDialog(
            onDismissRequest = {
                viewModel.onAction(ProfileAction.OnLogoutDismiss)
            },
            onLogout = {
                viewModel.onAction(ProfileAction.OnLogoutConfirmClick)
            },
        )
    }
}

@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
    toastState: KomiToastState,
    hasUnreadAnnouncements: Boolean = false,
) {
    KomiScaffold(
        topBar = {
            KomiTopBar(title = stringResource(Res.string.profile_title))
        },
        toastState = toastState,
    ) { innerPadding ->
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding.withoutBottom()),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                contentPadding =
                    PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + innerPadding.calculateBottomPadding()),
                modifier =
                    Modifier
                        .constrainedContentWidth()
                        .fillMaxHeight()
                        .arrowKeyScroll(listState, autoFocus = true),
            ) {
                profileSections(
                    state = state,
                    hasUnreadAnnouncements = hasUnreadAnnouncements,
                    onAction = onAction,
                )

                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}


@Preview
@Composable
private fun Preview() {
    PersonalityPreview {
        ProfileScreen(
            state = ProfileState(),
            onAction = {},
            toastState = rememberKomiToastState(),
        )
    }
}
