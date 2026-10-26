package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadProgressNotifier

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val dispatch = dispatchFor(intent.data?.scheme)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        when {
            dispatch == null -> {
                Logger.w { "DownloadCancelReceiver: unknown data URI ${intent.data}, ignoring" }
            }

            packageName.isBlank() -> {
                Logger.w { "DownloadCancelReceiver: missing package name extra" }
            }

            else -> {
                handle(dispatch, packageName)
            }
        }
    }

    private fun dispatchFor(scheme: String?): Dispatch? =
        when (scheme) {
            URI_SCHEME_DISCARD -> Dispatch.DELETE
            URI_SCHEME_PAUSE -> Dispatch.PAUSE
            URI_SCHEME_RESUME -> Dispatch.RESUME
            else -> null
        }

    private fun handle(
        dispatch: Dispatch,
        packageName: String,
    ) {
        val pending = goAsync()
        val koin = GlobalContext.getOrNull()
        if (koin == null) {
            Logger.w { "DownloadCancelReceiver: Koin not initialized, ignoring $dispatch for $packageName" }
            pending.finish()
            return
        }

        val orchestrator = koin.get<DownloadOrchestrator>()
        val scope = koin.get<CoroutineScope>()
        // The app scope has no handler, so a failed dispatch is logged here instead of crashing the app.
        val logFailure =
            CoroutineExceptionHandler { _, e ->
                Logger.e(e) { "DownloadCancelReceiver: $dispatch failed for $packageName" }
            }
        scope.launch(logFailure) {
            try {
                send(koin, orchestrator, dispatch, packageName)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun send(
        koin: Koin,
        orchestrator: DownloadOrchestrator,
        dispatch: Dispatch,
        packageName: String,
    ) {
        if (orchestrator.observe(packageName).first() == null) {
            koin.get<DownloadProgressNotifier>().clearProgress(packageName)
            return
        }
        when (dispatch) {
            Dispatch.PAUSE -> orchestrator.cancel(packageName)
            Dispatch.RESUME -> orchestrator.resume(packageName)
            Dispatch.DELETE -> orchestrator.discard(packageName)
        }
    }

    private enum class Dispatch { PAUSE, RESUME, DELETE }

    companion object {
        const val ACTION_CANCEL = "zed.rainxch.githubstore.action.CANCEL_DOWNLOAD"
        const val ACTION_DISCARD = "zed.rainxch.githubstore.action.DISCARD_DOWNLOAD"
        const val ACTION_RESUME = "zed.rainxch.githubstore.action.RESUME_DOWNLOAD"
        const val EXTRA_PACKAGE_NAME = "zed.rainxch.githubstore.extra.PACKAGE_NAME"

        const val URI_SCHEME_PAUSE = "githubstore-cancel"
        const val URI_SCHEME_DISCARD = "githubstore-discard"
        const val URI_SCHEME_RESUME = "githubstore-resume"
    }
}
