package zed.rainxch.tweaks.presentation.handlers

import zed.rainxch.core.domain.diagnostics.DiagnosticSessionController
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.tweaks.presentation.TweaksEvent

class DiagnosticsHandler(
    private val diagnosticSession: DiagnosticSessionController,
    private val shareManager: ShareManager,
    private val logger: KomiStoreLogger,
) {
    internal fun shareLogs(store: TweaksStore) {
        store.launch {
            val bundle = diagnosticSession.buildShareBundle()
            if (bundle == null || !share(bundle)) store.send(TweaksEvent.OnDiagnosticsShareFailed)
        }
    }

    private fun share(bundle: String): Boolean =
        try {
            shareManager.shareFile(fileName = BUNDLE_FILE_NAME, content = bundle, mimeType = "text/plain")
            true
        } catch (e: LocalStoreException) {
            shareFailed(e)
        } catch (e: SystemPackagesException) {
            shareFailed(e)
        }

    private fun shareFailed(e: Exception): Boolean {
        logger.error("TweaksViewModel: failed to share diagnostic logs", e)
        return false
    }

    private companion object {
        const val BUNDLE_FILE_NAME = "komi-store-diagnostics.txt"
    }
}
