package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.presentation.utils.toUserMessage
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.utils.UntypedFailureException
import zed.rainxch.details.presentation.utils.untypedFailures
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.added_to_favourites
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.githubstore.core.presentation.res.removed_from_favourites
import zed.rainxch.githubstore.core.presentation.res.star_added
import zed.rainxch.githubstore.core.presentation.res.star_removed
import zed.rainxch.githubstore.core.presentation.res.star_sign_in_required
import kotlin.time.Clock

// The repository itself: favourite, star, share and open it, and the owner badge for the signed-in user.
internal class DetailsRepoHandler(
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val userSessionRepository: UserSessionRepository,
    private val shareManager: ShareManager,
    private val browserHelper: BrowserHelper,
    private val logger: KomiStoreLogger,
) {
    internal fun onAction(
        session: DetailsSession,
        action: DetailsAction.Repo,
    ) {
        when (action) {
            DetailsAction.OnToggleFavorite -> {
                toggleFavourite(session)
            }

            DetailsAction.OnToggleStar -> {
                toggleStar(session)
            }

            DetailsAction.OnShareClick -> {
                share(session)
            }

            DetailsAction.OpenRepoInBrowser -> {
                session.current.repository
                    ?.htmlUrl
                    ?.let { browserHelper.openUrl(url = it) }
            }

            DetailsAction.OpenAuthorInBrowser -> {
                session.current.userProfile
                    ?.htmlUrl
                    ?.let { browserHelper.openUrl(url = it) }
            }

            is DetailsAction.OnShareAssetLink -> {
                shareAssetLink(action.assetUrl)
            }

            is DetailsAction.OnDownloadForTransfer -> {
                browserHelper.openUrl(action.assetUrl) { err -> logger.warn("Open transfer download failed: $err") }
            }
        }
    }

    internal fun observeCurrentUserForBadge(session: DetailsSession) {
        session.launch {
            combine(
                userSessionRepository.getUser(),
                session.state.map { it.repository?.owner?.login }.distinctUntilChanged(),
            ) { user, ownerLogin ->
                val login = user?.username
                login != null && ownerLogin != null && ownerLogin.equals(login, ignoreCase = true)
            }.collect { isOwner -> session.update { it.copy(isCurrentUserOwner = isOwner) } }
        }
    }

    private fun toggleFavourite(session: DetailsSession) {
        session.launch {
            val repo = session.current.repository ?: return@launch
            val selectedRelease = session.current.selectedRelease
            val now = Clock.System.now().toEpochMilliseconds()
            val favourite =
                FavoriteRepo(
                    repoId = repo.id,
                    repoName = repo.name,
                    repoOwner = repo.owner.login,
                    repoOwnerAvatarUrl = repo.owner.avatarUrl,
                    repoDescription = repo.description,
                    primaryLanguage = repo.language,
                    repoUrl = repo.htmlUrl,
                    latestVersion = selectedRelease?.tagName,
                    latestReleaseUrl = selectedRelease?.htmlUrl,
                    addedAt = now,
                    lastSyncedAt = now,
                )
            try {
                val isFavourite =
                    untypedFailures {
                        favouritesRepository.toggleFavorite(favourite)
                        favouritesRepository.isFavoriteSync(repo.id)
                    }
                session.update { it.copy(isFavourite = isFavourite) }
                val message = if (isFavourite) Res.string.added_to_favourites else Res.string.removed_from_favourites
                session.message(getString(message))
            } catch (e: UntypedFailureException) {
                logger.error("Failed to toggle favorite: ${e.message}")
            }
        }
    }

    private fun toggleStar(session: DetailsSession) {
        session.launch {
            val repo = session.current.repository ?: return@launch
            if (!userSessionRepository.isCurrentlyUserLoggedIn()) {
                session.message(getString(Res.string.star_sign_in_required))
                return@launch
            }
            val target = !session.current.isStarred
            session.update { it.copy(isStarred = target) }
            starredRepository
                .setStarred(repo.owner.login, repo.name, target)
                .onSuccess {
                    session.message(getString(if (target) Res.string.star_added else Res.string.star_removed))
                    starredRepository.syncStarredRepos(forceRefresh = true)
                }.onFailure { e ->
                    session.update { it.copy(isStarred = !target) }
                    session.message(e.toUserMessage(Res.string.failed_to_load))
                }
        }
    }

    // Android shares through the system sheet; elsewhere the link is copied, so the user is told.
    private fun share(session: DetailsSession) {
        val repo = session.current.repository ?: return
        session.launch {
            val shared = runCatching { shareManager.shareText("https://github-store.org/app?repo=${repo.fullName}") }
            if (shared.isFailure) {
                logger.error("Failed to share link: ${shared.exceptionOrNull()?.message}")
                session.message(getString(Res.string.failed_to_share_link))
            } else if (session.platform != Platform.ANDROID) {
                session.message(getString(Res.string.link_copied_to_clipboard))
            }
        }
    }

    private fun shareAssetLink(assetUrl: String) {
        runCatching { shareManager.shareText(assetUrl) }
            .onFailure { logger.warn("Share asset link failed: ${it.message}") }
    }
}
