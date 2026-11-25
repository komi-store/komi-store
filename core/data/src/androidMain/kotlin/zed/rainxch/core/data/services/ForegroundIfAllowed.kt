package zed.rainxch.core.data.services

import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException

// Android 12+ refuses a foreground service to background work, so the work carries on without the notification.
internal suspend fun CoroutineWorker.setForegroundIfAllowed(info: ForegroundInfo) {
    try {
        setForeground(info)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        logRunningWithoutForeground(e)
    } catch (e: SecurityException) {
        logRunningWithoutForeground(e)
    }
}

private fun CoroutineWorker.logRunningWithoutForeground(e: RuntimeException) {
    Logger.i { "${this::class.simpleName}: running without foreground notification (${e.message})" }
}
