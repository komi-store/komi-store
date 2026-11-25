package zed.rainxch.githubstore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.githubstore.app.components.RateLimitDialog
import zed.rainxch.githubstore.app.components.SessionExpiredDialog
import zed.rainxch.githubstore.app.navigation.GithubStoreGraph
import zed.rainxch.githubstore.app.whatsnew.WhatsNewSheet
import zed.rainxch.profile.presentation.whatsnew.WhatsNewViewModel

// The dialogs and the what's-new sheet shown over whichever screen is open.
@Composable
internal fun AppOverlays(
    state: MainState,
    currentScreen: GithubStoreGraph?,
    onAction: (MainAction) -> Unit,
    onNavigate: (GithubStoreGraph) -> Unit,
    whatsNewViewModel: WhatsNewViewModel = koinViewModel(),
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val onAuthScreen = currentScreen is GithubStoreGraph.AuthenticationScreen
    LaunchedEffect(onAuthScreen, state.showRateLimitDialog) {
        if (onAuthScreen && state.showRateLimitDialog) {
            currentOnAction(MainAction.DismissRateLimitDialog)
        }
    }

    val rateLimit = state.rateLimit
    if (state.showRateLimitDialog && rateLimit != null && !onAuthScreen) {
        RateLimitDialog(
            rateLimit = rateLimit,
            isAuthenticated = state.isLoggedIn,
            onDismiss = { onAction(MainAction.DismissRateLimitDialog) },
            onSignIn = {
                onAction(MainAction.DismissRateLimitDialog)
                onNavigate(GithubStoreGraph.AuthenticationScreen)
            },
        )
    }

    if (state.showSessionExpiredDialog) {
        SessionExpiredDialog(
            onDismiss = { onAction(MainAction.DismissSessionExpiredDialog) },
            onSignIn = {
                onAction(MainAction.DismissSessionExpiredDialog)
                onNavigate(GithubStoreGraph.AuthenticationScreen)
            },
        )
    }

    val pendingEntry by whatsNewViewModel.pendingEntry.collectAsStateWithLifecycle()
    pendingEntry?.let { entryToShow ->
        val onHomeScreen = currentScreen is GithubStoreGraph.ExploreScreen
        if (onHomeScreen && !state.showRateLimitDialog) {
            val hasHistory by whatsNewViewModel.hasHistory.collectAsStateWithLifecycle()
            WhatsNewSheet(
                entry = entryToShow,
                showHistoryAction = hasHistory,
                onDismiss = { whatsNewViewModel.markSeen() },
                onViewHistory = {
                    whatsNewViewModel.markSeen()
                    onNavigate(GithubStoreGraph.WhatsNewHistoryScreen)
                },
            )
        }
    }
}
