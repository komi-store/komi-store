package zed.rainxch.apps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import zed.rainxch.apps.presentation.library.LibraryBackup
import zed.rainxch.apps.presentation.library.LibraryLoader
import zed.rainxch.apps.presentation.link.LinkSheetActions
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.settings.AppRowActions
import zed.rainxch.apps.presentation.settings.AssetMatchSettings
import zed.rainxch.apps.presentation.updates.AppUpdates

class AppsViewModel(
    private val library: LibraryLoader,
    private val updates: AppUpdates,
    private val linkSheet: LinkSheetActions,
    private val rowActions: AppRowActions,
    private val assetSettings: AssetMatchSettings,
    private val backup: LibraryBackup,
) : ViewModel() {
    private val holder = AppsStateHolder(viewModelScope)
    private var hasLoadedInitialData = false

    val state: StateFlow<AppsState> =
        holder.state
            .onStart {
                if (!hasLoadedInitialData) {
                    library.load(holder, onAppsChanged = { updates.reapplyDownloads(holder) })
                    hasLoadedInitialData = true
                }
            }.map { it.withDerived() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AppsState(),
            )

    val events: Flow<AppsEvent> = holder.events

    init {
        updates.observeDownloads(holder)
    }

    fun onAction(action: AppsAction) {
        when (action) {
            is AppsAction.Library -> library.onAction(holder, action)
            is AppsAction.ImportProposal -> library.onAction(holder, action)
            is AppsAction.Update -> updates.onAction(holder, action)
            is AppsAction.DownloadTask -> updates.onAction(holder, action)
            is AppsAction.OnUpdateApp -> updateApp(action.app)
            is AppsAction.LinkNavigation -> linkSheet.onAction(holder, action)
            is AppsAction.LinkInput -> linkSheet.onAction(holder, action)
            is AppsAction.AppRow -> rowActions.onAction(holder, action)
            is AppsAction.AdvancedFilter -> assetSettings.onAction(holder, action)
            is AppsAction.Variant -> assetSettings.onAction(holder, action, onResumeUpdate = { updates.update(holder, it) })
            is AppsAction.Backup -> backup.onAction(holder, action)
            AppsAction.OnNavigateBackClick, AppsAction.OnAddFromStarredClick -> Unit
        }
    }

    // A pinned variant that vanished from the latest release has to be picked again before the update can run.
    private fun updateApp(app: InstalledAppUi) {
        if (app.preferredVariantStale) {
            assetSettings.openVariantPicker(holder, app, resumeUpdateAfterPick = true)
        } else {
            updates.update(holder, app)
        }
    }

    override fun onCleared() {
        updates.release()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
