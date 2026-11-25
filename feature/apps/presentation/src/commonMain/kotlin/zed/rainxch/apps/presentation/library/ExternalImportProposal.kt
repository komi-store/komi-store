package zed.rainxch.apps.presentation.library

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import zed.rainxch.apps.presentation.AppsEvent
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.TweaksRepository

// The banner offering to import apps installed elsewhere; it comes back only when more candidates turn up than were dismissed.
internal class ExternalImportProposal(
    private val externalImportRepository: ExternalImportRepository,
    private val tweaksRepository: TweaksRepository,
) {
    @Volatile
    private var localDismissedAtCount: Int = 0

    fun observe(holder: AppsStateHolder) {
        holder.scope.launch {
            externalImportRepository
                .pendingCandidateCountFlow()
                .combine(tweaksRepository.getExternalImportBannerDismissedAtCount()) { count, dismissedAt ->
                    count to dismissedAt
                }.collect { (count, dismissedAt) ->
                    val effectiveDismissedAt = maxOf(dismissedAt, localDismissedAtCount)
                    val shouldShow = count >= BANNER_THRESHOLD && count > effectiveDismissedAt
                    holder.update {
                        it.copy(
                            pendingExternalImportCount = count,
                            showImportProposalBanner = shouldShow && !it.isExternalImportInFlight,
                        )
                    }
                }
        }
    }

    fun review(holder: AppsStateHolder) {
        val current = dismiss(holder)
        holder.scope.launch {
            tweaksRepository.setExternalImportBannerDismissedAtCount(current)
            holder.send(AppsEvent.NavigateToExternalImport)
        }
    }

    fun dismissBanner(holder: AppsStateHolder) {
        val current = dismiss(holder)
        holder.scope.launch {
            tweaksRepository.setExternalImportBannerDismissedAtCount(current)
        }
    }

    fun rescan(holder: AppsStateHolder) {
        localDismissedAtCount = 0
        holder.update { it.copy(showImportProposalBanner = false) }
        holder.scope.launch {
            tweaksRepository.setExternalImportBannerDismissedAtCount(0)
            holder.send(AppsEvent.NavigateToExternalImport)
        }
    }

    private fun dismiss(holder: AppsStateHolder): Int {
        val current = holder.current.pendingExternalImportCount
        localDismissedAtCount = maxOf(localDismissedAtCount, current)
        holder.update { it.copy(showImportProposalBanner = false) }
        return current
    }

    private companion object {
        const val BANNER_THRESHOLD = 1
    }
}
