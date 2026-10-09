package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import zed.rainxch.core.domain.system.DownloadOrchestrator

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dispatch =
            when (intent.data?.scheme) {
                URI_SCHEME_DISCARD -> Dispatch.DELETE
                URI_SCHEME_PAUSE -> Dispatch.PAUSE
                else -> {
                    Logger.w { "DownloadCancelReceiver: unknown data URI ${intent.data}, ignoring" }
                    return
                }
            }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (packageName.isBlank()) {
            Logger.w { "DownloadCancelReceiver: missing package name extra" }
            return
        }

        val pending = goAsync()
        val koin = GlobalContext.getOrNull()
        if (koin == null) {
            Logger.w { "DownloadCancelReceiver: Koin not initialized, ignoring $dispatch for $packageName" }
            pending.finish()
            return
        }

        val orchestrator = koin.get<DownloadOrchestrator>()
        val scope = koin.get<CoroutineScope>()
        scope.launch {
            try {
                when (dispatch) {
                    Dispatch.PAUSE -> orchestrator.cancel(packageName)
                    Dispatch.DELETE -> orchestrator.discard(packageName)
                }
            } catch (t: Throwable) {
                Logger.e(t) { "DownloadCancelReceiver: $dispatch failed for $packageName" }
            } finally {
                pending.finish()
            }
        }
    }

    private enum class Dispatch { PAUSE, DELETE }

    companion object {
        const val ACTION_CANCEL = "zed.rainxch.githubstore.action.CANCEL_DOWNLOAD"
        const val ACTION_DISCARD = "zed.rainxch.githubstore.action.DISCARD_DOWNLOAD"
        const val EXTRA_PACKAGE_NAME = "zed.rainxch.githubstore.extra.PACKAGE_NAME"

        const val URI_SCHEME_PAUSE = "githubstore-cancel"
        const val URI_SCHEME_DISCARD = "githubstore-discard"
    }
}
