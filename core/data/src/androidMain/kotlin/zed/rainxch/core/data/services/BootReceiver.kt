package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.error.InstanceCreationException
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.TweaksRepository
import kotlin.coroutines.cancellation.CancellationException

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        try {
            val enabled = readTweakOrNull { getUpdateCheckEnabled().first() } ?: true
            if (enabled) {
                Logger.i { "BootReceiver: Device booted, scheduling update checks" }
                val intervalHours = readTweakOrNull { getUpdateCheckInterval().first() }
                if (intervalHours != null) {
                    UpdateScheduler.schedule(context, intervalHours)
                } else {
                    UpdateScheduler.schedule(context)
                }
            } else {
                Logger.i { "BootReceiver: Device booted, update check disabled — skipping" }
                UpdateScheduler.cancel(context)
            }
        } catch (e: IllegalStateException) {
            Logger.e(e) { "BootReceiver: scheduling failed; dropped" }
        } finally {
            pendingResult.finish()
        }
    }

    // A setting that can't be read, a repository that can't be built or a Koin graph that isn't started falls back to the default.
    private fun <T : Any> readTweakOrNull(read: suspend TweaksRepository.() -> T): T? =
        try {
            runBlocking { GlobalContext.get().get<TweaksRepository>().read() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalStoreException) {
            logDefaulted(e)
            null
        } catch (e: InstanceCreationException) {
            logDefaulted(e)
            null
        } catch (e: IllegalStateException) {
            logDefaulted(e)
            null
        }

    private fun logDefaulted(e: Exception) {
        Logger.w(e) { "BootReceiver: Failed to read a setting, using its default" }
    }
}
