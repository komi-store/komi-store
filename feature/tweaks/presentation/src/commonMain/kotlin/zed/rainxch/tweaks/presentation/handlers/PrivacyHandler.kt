package zed.rainxch.tweaks.presentation.handlers

import kotlinx.coroutines.Job
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.CacheRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_clear_downloads_failed
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksEvent

class PrivacyHandler(
    private val tweaksRepository: TweaksRepository,
    private val seenReposRepository: SeenReposRepository,
    private val cacheRepository: CacheRepository,
    private val logger: KomiStoreLogger,
) {
    private var cacheSizeJob: Job? = null

    internal fun observe(store: TweaksStore) {
        store.observe(tweaksRepository.getAutoDetectClipboardLinks()) { copy(autoDetectClipboardLinks = it) }
        store.observe(tweaksRepository.getHideSeenEnabled()) { copy(isHideSeenEnabled = it) }
    }

    internal fun refreshCacheSize(store: TweaksStore) {
        if (cacheSizeJob?.isActive == true) return
        cacheSizeJob = store.observe(cacheRepository.observeCacheSize()) { copy(cacheSize = formatCacheSize(it)) }
    }

    internal fun onAction(
        store: TweaksStore,
        action: TweaksAction.Privacy,
    ) {
        when (action) {
            is TweaksAction.OnAutoDetectClipboardToggled -> store.launch { tweaksRepository.setAutoDetectClipboardLinks(action.enabled) }
            is TweaksAction.OnHideSeenToggled -> store.launch { tweaksRepository.setHideSeenEnabled(action.enabled) }
            TweaksAction.OnClearSeenRepos -> clearSeenHistory(store)
            TweaksAction.OnClearSeenHistoryRequest -> store.update { it.copy(isClearSeenHistoryDialogVisible = true) }
            TweaksAction.OnClearSeenHistoryDismiss -> store.update { it.copy(isClearSeenHistoryDialogVisible = false) }
            TweaksAction.OnClearSeenHistoryConfirm -> confirmClearSeenHistory(store)
            TweaksAction.OnRefreshCacheSize -> refreshCacheSize(store)
            TweaksAction.OnClearCacheClick -> store.update { it.copy(isClearDownloadsDialogVisible = true) }
            TweaksAction.OnClearDownloadsConfirm -> clearDownloads(store)
            TweaksAction.OnClearDownloadsDismiss -> store.update { it.copy(isClearDownloadsDialogVisible = false) }
        }
    }

    private fun confirmClearSeenHistory(store: TweaksStore) {
        store.update { it.copy(isClearSeenHistoryDialogVisible = false) }
        clearSeenHistory(store)
    }

    private fun clearSeenHistory(store: TweaksStore) {
        store.launch {
            seenReposRepository.clearAll()
            store.send(TweaksEvent.OnSeenHistoryCleared)
        }
    }

    private fun clearDownloads(store: TweaksStore) {
        store.update { it.copy(isClearDownloadsDialogVisible = false) }
        store.launch {
            try {
                cacheRepository.clearCache()
                cacheSizeJob?.cancel()
                cacheSizeJob = null
                refreshCacheSize(store)
                store.send(TweaksEvent.OnCacheCleared)
            } catch (e: LocalStoreException) {
                logger.error("TweaksViewModel: failed to clear downloads", e)
                store.send(TweaksEvent.OnCacheClearError(getString(Res.string.tweaks_clear_downloads_failed)))
            }
        }
    }

    private fun formatCacheSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= BYTES_PER_UNIT && unitIndex < SIZE_UNITS.lastIndex) {
            size /= BYTES_PER_UNIT
            unitIndex++
        }
        val amount = if (size == size.toLong().toDouble()) size.toLong().toString() else "%.1f".format(size)
        return "$amount ${SIZE_UNITS[unitIndex]}"
    }

    private companion object {
        const val BYTES_PER_UNIT = 1024.0
        val SIZE_UNITS = listOf("B", "KB", "MB", "GB")
    }
}
