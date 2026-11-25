package zed.rainxch.search.presentation.handlers

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.no_github_link_in_clipboard
import zed.rainxch.search.presentation.SearchAction
import zed.rainxch.search.presentation.SearchEvent
import zed.rainxch.search.presentation.utils.parseGithubUrls
import zed.rainxch.search.presentation.withoutResults

// GitHub links found in the clipboard: the banner offered on open, and the open-link button.
class ClipboardLinks(
    private val clipboardHelper: ClipboardHelper,
    private val tweaksRepository: TweaksRepository,
    private val logger: KomiStoreLogger,
) {
    internal fun observe(store: SearchStore) {
        store.launch {
            tweaksRepository.getAutoDetectClipboardLinks().collect { enabled ->
                store.update { current ->
                    current.copy(
                        autoDetectClipboardEnabled = enabled,
                        clipboardLinks = if (enabled) current.clipboardLinks else persistentListOf(),
                        isClipboardBannerVisible = enabled && current.isClipboardBannerVisible,
                    )
                }
                if (enabled) offerClipboardLinks(store)
            }
        }
        offerClipboardLinks(store)
    }

    internal fun onAction(
        store: SearchStore,
        action: SearchAction.Links,
    ) {
        when (action) {
            is SearchAction.OpenGithubLink -> store.launch { store.send(SearchEvent.NavigateToRepo(action.owner, action.repo)) }
            SearchAction.OnFabClick -> openClipboardLink(store)
            SearchAction.DismissClipboardBanner -> store.update { it.copy(isClipboardBannerVisible = false) }
        }
    }

    private fun offerClipboardLinks(store: SearchStore) {
        store.launch {
            if (!tweaksRepository.getAutoDetectClipboardLinks().first()) return@launch

            val clipText =
                try {
                    clipboardHelper.getText()
                } catch (e: SystemPackagesException) {
                    logger.debug("Failed to read clipboard: ${e.message}")
                    null
                } ?: return@launch
            val links = parseGithubUrls(clipText)
            if (links.isNotEmpty()) {
                store.update { it.copy(clipboardLinks = links, isClipboardBannerVisible = true) }
            }
        }
    }

    // One link opens straight away; several fill the query so the user can pick.
    private fun openClipboardLink(store: SearchStore) {
        store.launch {
            val clipText =
                try {
                    clipboardHelper.getText()
                } catch (e: SystemPackagesException) {
                    logger.error("Failed to read clipboard: ${e.message}")
                    null
                }
            val links = if (clipText.isNullOrBlank()) persistentListOf() else parseGithubUrls(clipText)
            when {
                links.isEmpty() -> {
                    store.send(SearchEvent.OnMessage(getString(Res.string.no_github_link_in_clipboard)))
                }

                links.size == 1 -> {
                    store.send(SearchEvent.NavigateToRepo(links.first().owner, links.first().repo))
                }

                else -> {
                    store.update { it.withoutResults().copy(query = clipText.orEmpty(), detectedLinks = links) }
                }
            }
        }
    }
}
