package zed.rainxch.search.presentation.handlers

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.repository.SeenRepoEntry
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.HiddenReposRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.search.presentation.SearchAction
import zed.rainxch.search.presentation.SearchEvent

// A result card's own actions: share, hide and the seen toggle, with the hidden and seen sets they feed back.
class RepoCardActions(
    private val shareManager: ShareManager,
    private val platform: Platform,
    private val hiddenReposRepository: HiddenReposRepository,
    private val seenReposRepository: SeenReposRepository,
    private val logger: KomiStoreLogger,
) {
    internal fun observe(store: SearchStore) {
        store.observe(seenReposRepository.getAllSeenRepoIds()) { ids ->
            copy(
                seenRepoIds = ids.toImmutableSet(),
                repositories = repositories.map { it.copy(isSeen = it.repository.id in ids) }.toImmutableList(),
            )
        }
        store.observe(hiddenReposRepository.getAllHiddenRepoIds()) { copy(hiddenRepoIds = it.toImmutableSet()) }
    }

    internal fun onAction(
        store: SearchStore,
        action: SearchAction.RepoCard,
    ) {
        when (action) {
            is SearchAction.OnShareClick -> {
                share(store, action.repo)
            }

            is SearchAction.OnHideRepository -> {
                hide(store, action.repo)
            }

            is SearchAction.OnUndoHideRepository -> {
                write(store, "Unhide repository failed for ${action.repoId}") { hiddenReposRepository.unhide(action.repoId) }
            }

            is SearchAction.OnMarkAsSeen -> {
                markAsSeen(store, action.repo)
            }

            is SearchAction.OnMarkAsUnseen -> {
                write(store, "Mark as unseen failed for ${action.repoId}") { seenReposRepository.removeFromHistory(action.repoId) }
            }
        }
    }

    private fun share(
        store: SearchStore,
        repo: GithubRepoSummaryUi,
    ) {
        store.launch {
            val shared = runCatching { shareManager.shareText("https://github-store.org/app?repo=${repo.fullName}") }
            if (shared.isFailure) {
                logger.error("Failed to share link: ${shared.exceptionOrNull()?.message}")
                store.send(SearchEvent.OnMessage(getString(Res.string.failed_to_share_link)))
            } else if (platform != Platform.ANDROID) {
                store.send(SearchEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
            }
        }
    }

    private fun hide(
        store: SearchStore,
        repo: GithubRepoSummaryUi,
    ) = write(store, "Hide repository failed for ${repo.id}") {
        hiddenReposRepository.hide(
            repoId = repo.id,
            repoName = repo.name,
            repoOwner = repo.owner.login,
            repoOwnerAvatarUrl = repo.owner.avatarUrl,
        )
    }

    private fun markAsSeen(
        store: SearchStore,
        repo: GithubRepoSummaryUi,
    ) = write(store, "Mark as seen failed for ${repo.id}") {
        seenReposRepository.markAsSeen(
            SeenRepoEntry(
                repoId = repo.id,
                repoName = repo.name,
                repoOwner = repo.owner.login,
                repoOwnerAvatarUrl = repo.owner.avatarUrl,
                repoDescription = repo.description,
                primaryLanguage = repo.language,
                repoUrl = repo.htmlUrl,
            ),
        )
    }

    private fun write(
        store: SearchStore,
        failure: String,
        block: suspend () -> Unit,
    ) {
        store.launch {
            try {
                block()
            } catch (e: LocalStoreException) {
                logger.warn("$failure: ${e.message}")
            }
        }
    }
}
