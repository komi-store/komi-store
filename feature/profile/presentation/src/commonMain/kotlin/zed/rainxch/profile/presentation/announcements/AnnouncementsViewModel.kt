package zed.rainxch.profile.presentation.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.announcement.AnnouncementsFeedSnapshot
import zed.rainxch.core.domain.repository.AnnouncementsRepository

// The repository returns refresh failures as a Result and its tweaks writes drop their own storage failures, so nothing here throws.
class AnnouncementsViewModel(
    private val repository: AnnouncementsRepository,
    private val browserHelper: BrowserHelper,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private val feed: StateFlow<AnnouncementsFeedSnapshot?> =
        repository
            .observeFeed()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = null,
            )

    private val isMuteSheetVisible = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val expandedIds = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<AnnouncementsState> =
        combine(
            feed.filterNotNull(),
            isMuteSheetVisible,
            isRefreshing,
            expandedIds,
        ) { snapshot, muteSheetVisible, refreshing, expanded ->
            AnnouncementsState(
                items = snapshot.visibleItems.map { it.toAnnouncementUi() }.toImmutableList(),
                acknowledgedIds = snapshot.acknowledgedIds.toImmutableSet(),
                mutedCategories = snapshot.mutedCategories.toImmutableSet(),
                expandedIds = expanded.toImmutableSet(),
                refreshFailed = snapshot.lastRefreshFailed,
                isRefreshing = refreshing,
                isMuteSheetVisible = muteSheetVisible,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AnnouncementsState(),
        )

    val unreadCount: StateFlow<Int> =
        feed
            .map { it?.unreadCount ?: 0 }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = 0,
            )

    init {
        viewModelScope.launch {
            repository.refresh()
        }
    }

    fun onAction(action: AnnouncementsAction) {
        when (action) {
            AnnouncementsAction.OnRefresh -> refresh()
            AnnouncementsAction.OnOpenMuteSheet -> isMuteSheetVisible.value = true
            AnnouncementsAction.OnDismissMuteSheet -> isMuteSheetVisible.value = false
            AnnouncementsAction.OnEnterScreen -> markRoutineItemsSeen()
            is AnnouncementsAction.OnCtaClick -> openCta(action.announcement)
            is AnnouncementsAction.OnDismissClick -> dismiss(action.announcement)
            is AnnouncementsAction.OnAcknowledgeClick -> acknowledge(action.announcement)
            is AnnouncementsAction.OnToggleExpand -> toggleExpand(action.announcementId)
            is AnnouncementsAction.OnToggleMute -> setMuted(action.category, action.muted)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                repository.refresh()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private fun toggleExpand(id: String) {
        expandedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    private fun markRoutineItemsSeen() {
        val snapshot = feed.value ?: return
        viewModelScope.launch {
            snapshot.visibleItems.forEach { item ->
                if (!item.requiresAcknowledgment && item.id !in snapshot.acknowledgedIds) {
                    repository.acknowledge(item.id)
                }
            }
        }
    }

    private fun dismiss(announcement: AnnouncementUi) {
        viewModelScope.launch {
            repository.dismiss(announcement.id)
            if (!announcement.requiresAcknowledgment) {
                repository.acknowledge(announcement.id)
            }
        }
    }

    private fun acknowledge(announcement: AnnouncementUi) {
        viewModelScope.launch {
            repository.acknowledge(announcement.id)
        }
    }

    private fun openCta(announcement: AnnouncementUi) {
        val url = announcement.ctaUrl ?: return
        viewModelScope.launch {
            repository.acknowledge(announcement.id)
        }
        browserHelper.openUrl(url) { error ->
            logger.error("Failed to open CTA url for ${announcement.id}: $error")
        }
    }

    private fun setMuted(
        category: AnnouncementCategory,
        muted: Boolean,
    ) {
        viewModelScope.launch {
            repository.setMuted(category, muted)
        }
    }
}
