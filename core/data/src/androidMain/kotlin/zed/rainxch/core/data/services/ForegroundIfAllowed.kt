package zed.rainxch.core.data.services

import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException

// Android 12+ refuses to start a foreground service while the app is in the background,
// which is when periodic work runs. The work doesn't need one, so it carries on without
// the notification instead of failing before it starts.
internal suspend fun CoroutineWorker.setForegroundIfAllowed(info: ForegroundInfo) {
    try {
        setForeground(info)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.i { "${this::class.simpleName}: running without foreground notification (${e.message})" }
    }
}
