package zed.rainxch.profile.presentation.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.WhatsNewLoader
import zed.rainxch.core.domain.system.AppVersionInfo

// The loader returns null for an entry it can't read and the tweaks reads and writes drop their own storage failures.
class WhatsNewViewModel(
    private val tweaksRepository: TweaksRepository,
    private val appVersionInfo: AppVersionInfo,
    private val whatsNewLoader: WhatsNewLoader,
) : ViewModel() {
    private val _pendingEntry = MutableStateFlow<WhatsNewEntryUi?>(null)
    val pendingEntry: StateFlow<WhatsNewEntryUi?> = _pendingEntry.asStateFlow()

    private val _historyEntries = MutableStateFlow<ImmutableList<WhatsNewEntryUi>>(persistentListOf())
    val historyEntries: StateFlow<ImmutableList<WhatsNewEntryUi>> = _historyEntries.asStateFlow()

    private val _hasHistory = MutableStateFlow(false)
    val hasHistory: StateFlow<Boolean> = _hasHistory.asStateFlow()

    @Volatile
    private var lastLanguageTag: String? = null

    init {
        viewModelScope.launch {
            tweaksRepository
                .getAppLanguage()
                .distinctUntilChanged()
                .collect { tag ->
                    lastLanguageTag = tag
                    reloadHistory(tag)
                    evaluate(tag)
                }
        }
    }

    private suspend fun reloadHistory(languageTag: String?) {
        val entries = whatsNewLoader.loadAll(languageTag)
        _historyEntries.value = entries.map { it.toWhatsNewEntryUi() }.toImmutableList()
        _hasHistory.value = entries.size > 1
    }

    private suspend fun evaluate(languageTag: String? = lastLanguageTag) {
        val current = appVersionInfo.versionCode
        val lastSeen = tweaksRepository.getLastSeenWhatsNewVersionCode().first() ?: Int.MIN_VALUE

        if (lastSeen >= current) return

        val entry = whatsNewLoader.forVersionCode(current, languageTag)
        if (entry == null || !entry.showAsSheet) {
            tweaksRepository.setLastSeenWhatsNewVersionCode(current)
            return
        }

        _pendingEntry.value = entry.toWhatsNewEntryUi()
    }

    fun markSeen() {
        val entry = _pendingEntry.value ?: return
        _pendingEntry.value = null
        viewModelScope.launch {
            tweaksRepository.setLastSeenWhatsNewVersionCode(entry.versionCode)
        }
    }
}
