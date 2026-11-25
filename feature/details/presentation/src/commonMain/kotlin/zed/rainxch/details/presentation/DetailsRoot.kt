package zed.rainxch.details.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.error.RefreshError
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.utils.KomiSeededTheme
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.rememberIconSeedColor
import zed.rainxch.details.presentation.components.ApkInspectSheet
import zed.rainxch.details.presentation.components.PlatformHandoffSheet
import zed.rainxch.details.presentation.model.RepositoryUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_refresh_snackbar_archived
import zed.rainxch.githubstore.core.presentation.res.details_refresh_snackbar_budget_exhausted
import zed.rainxch.githubstore.core.presentation.res.details_refresh_snackbar_cooldown
import zed.rainxch.githubstore.core.presentation.res.details_refresh_snackbar_generic
import zed.rainxch.githubstore.core.presentation.res.details_refresh_snackbar_not_found
import zed.rainxch.githubstore.core.presentation.res.details_refresh_snackbar_upstream

@Composable
fun DetailsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    onOpenRepositoryInApp: (repoId: Long) -> Unit,
    onNavigateToAbout: (repoId: Long, owner: String, repo: String, sourceHost: String?, translateTo: String?) -> Unit,
    onNavigateToWhatsNew: (repoId: Long, owner: String, repo: String, sourceHost: String?) -> Unit,
    onNavigateToIssues: (owner: String, repo: String) -> Unit,
    onNavigateToSecurity: (owner: String, repo: String) -> Unit,
    onNavigateToReviews: (
        owner: String,
        repo: String,
        sourceHost: String?,
        openComposer: Boolean,
        composerRating: Int,
        version: String?,
        appLabel: String?,
    ) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DetailsEvent.OnOpenRepositoryInApp -> onOpenRepositoryInApp(event.repositoryId)
            is DetailsEvent.OnMessage -> coroutineScope.launch { toastState.show(event.message) }
            is DetailsEvent.OnRefreshError -> coroutineScope.launch { toastState.show(refreshErrorText(event)) }
        }
    }

    val repository = state.repository
    KomiSeededTheme(seed = rememberDetailsSeed(state)) {
        DetailsScreen(
            state = state,
            toastState = toastState,
            onAction = { action ->
                if (action is DetailsAction.Navigation) {
                    navigate(action, onNavigateBack, onNavigateToDeveloperProfile) { text ->
                        coroutineScope.launch { toastState.show(getString(text)) }
                    }
                } else {
                    viewModel.onAction(action)
                }
            },
            modifier = modifier,
            onTranslateLanguage =
                repository?.let { repo ->
                    { code -> onNavigateToAbout(repo.id, repo.ownerLogin, repo.name, repo.sourceHost, code) }
                },
            onReadMoreWhatsNew = repository?.let { repo -> { onNavigateToWhatsNew(repo.id, repo.ownerLogin, repo.name, repo.sourceHost) } },
            onOpenIssues = repository?.githubOnly()?.let { repo -> { onNavigateToIssues(repo.ownerLogin, repo.name) } },
            onOpenSecurity = repository?.githubOnly()?.let { repo -> { onNavigateToSecurity(repo.ownerLogin, repo.name) } },
            onOpenReviews =
                repository?.let { repo ->
                    { composerRating ->
                        onNavigateToReviews(
                            repo.ownerLogin,
                            repo.name,
                            repo.sourceHost,
                            composerRating != null,
                            composerRating ?: 0,
                            state.installedApp?.installedVersion ?: state.selectedRelease?.tagName,
                            state.selectedAppLabel,
                        )
                    }
                },
        )

        DetailsOverlays(state = state, onAction = viewModel::onAction)
    }
}

// Classic takes the page's colors from the app's icon, sheets and dialogs included.
@Composable
private fun rememberDetailsSeed(state: DetailsState): Color? =
    if (LocalPersonality.current is MangaPersonality) {
        null
    } else {
        rememberIconSeedColor(
            packageName = state.installedApp?.takeIf { !it.isPendingInstall }?.packageName,
            imageUrl = state.userProfile?.avatarUrl ?: state.repository?.ownerAvatarUrl,
        )
    }

private fun navigate(
    action: DetailsAction.Navigation,
    onNavigateBack: () -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    showMessage: (StringResource) -> Unit,
) {
    when (action) {
        DetailsAction.OnNavigateBackClick -> onNavigateBack()
        is DetailsAction.OpenDeveloperProfile -> onNavigateToDeveloperProfile(action.username)
        is DetailsAction.OnMessage -> showMessage(action.messageText)
    }
}

// Issues and security pages exist for GitHub repositories only.
private fun RepositoryUi.githubOnly(): RepositoryUi? = takeIf { it.sourceHost == null }

@Composable
private fun DetailsOverlays(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    state.handoff?.let { handoff -> PlatformHandoffSheet(handoff = handoff, onAction = onAction) }
    DetailsDialogs(state = state, onAction = onAction)
    if (state.isApkInspectSheetVisible) {
        ApkInspectSheet(
            inspection = state.apkInspection,
            isLoading = state.isApkInspectLoading,
            onDismiss = { onAction(DetailsAction.OnDismissApkInspect) },
        )
    }
}

private suspend fun refreshErrorText(event: DetailsEvent.OnRefreshError): String {
    val seconds = (event.retryAfterSeconds?.toInt() ?: 0).coerceAtLeast(1)
    return when (event.kind) {
        RefreshError.COOLDOWN -> getString(Res.string.details_refresh_snackbar_cooldown, seconds)
        RefreshError.BUDGET_EXHAUSTED -> getString(Res.string.details_refresh_snackbar_budget_exhausted, seconds)
        RefreshError.ARCHIVED -> getString(Res.string.details_refresh_snackbar_archived)
        RefreshError.NOT_FOUND -> getString(Res.string.details_refresh_snackbar_not_found)
        RefreshError.UPSTREAM -> getString(Res.string.details_refresh_snackbar_upstream)
        RefreshError.GENERIC -> getString(Res.string.details_refresh_snackbar_generic)
    }
}
