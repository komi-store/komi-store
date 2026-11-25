package zed.rainxch.apps.presentation.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository

class ExternalImportViewModel(
    externalImportRepository: ExternalImportRepository,
    appsRepository: AppsRepository,
    installedAppsRepository: InstalledAppsRepository,
    logger: KomiStoreLogger,
    tweaksRepository: TweaksRepository,
) : ViewModel() {
    private val holder = ImportStateHolder(viewModelScope)
    private val session = ImportSession()
    private val linker = ImportLinker(appsRepository, externalImportRepository, logger)
    private val scan = ImportScan(externalImportRepository, installedAppsRepository, linker, session, logger)
    private val review = ImportReview(externalImportRepository, installedAppsRepository, linker, session, logger)
    private val summary = ImportSummary(externalImportRepository, installedAppsRepository, session, logger)
    private val search = RepoSearch(externalImportRepository, tweaksRepository, logger)
    private var hasStarted = false

    val state: StateFlow<ExternalImportState> =
        holder.state
            .onStart {
                if (!hasStarted) {
                    hasStarted = true
                    scan.startIfIdle(holder)
                }
            }.map { it.copy(trackedCount = it.autoImported + it.manuallyLinked) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ExternalImportState(),
            )

    val events: Flow<ExternalImportEvent> = holder.events

    fun onAction(action: ExternalImportAction) {
        when (action) {
            is ExternalImportAction.Scan -> {
                scan.onAction(holder, action)
            }

            is ExternalImportAction.Review -> {
                review.onAction(holder, action)
            }

            is ExternalImportAction.Search -> {
                search.onAction(holder, action)
            }

            is ExternalImportAction.Summary -> {
                summary.onAction(holder, action)
            }

            ExternalImportAction.OnExit -> {
                viewModelScope.launch { holder.send(ExternalImportEvent.NavigateBack) }
            }

            ExternalImportAction.OnAddManually -> {
                viewModelScope.launch { holder.send(ExternalImportEvent.NavigateBackAndOpenManualLink) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
