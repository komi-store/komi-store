package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import zed.rainxch.core.domain.repository.TweaksRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        try {
            val enabled =
                runCatching {
                    runBlocking {
                        GlobalContext.get().get<TweaksRepository>().getUpdateCheckEnabled().first()
                    }
                }.getOrElse {
                    Logger.w(it) { "BootReceiver: Failed to read update-check flag, defaulting to enabled" }
                    true
                }
            if (enabled) {
                Logger.i { "BootReceiver: Device booted, scheduling update checks" }
                val intervalHours =
                    runCatching {
                        runBlocking {
                            GlobalContext.get().get<TweaksRepository>().getUpdateCheckInterval().first()
                        }
                    }.getOrNull()
                if (intervalHours != null) {
                    UpdateScheduler.schedule(context, intervalHours)
                } else {
                    UpdateScheduler.schedule(context)
                }
            } else {
                Logger.i { "BootReceiver: Device booted, update check disabled — skipping" }
                UpdateScheduler.cancel(context)
            }
        } catch (t: Throwable) {

            Logger.e(t) { "BootReceiver: scheduling failed; dropped" }
        } finally {
            pendingResult.finish()
        }
    }
}
