package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.domain.system.SystemInstallSerializer

class DefaultSystemInstallSerializer : SystemInstallSerializer {
    private val pending = MutableStateFlow<String?>(null)

    override suspend fun awaitFreeAndMarkPending(
        packageName: String,
        timeoutMs: Long,
    ) {
        val initiallyHeldBy = pending.value
        if (timeoutMs <= 0L) {
            if (initiallyHeldBy != null) {
                Logger.i { "SystemInstallSerializer: $packageName claimed the gate from $initiallyHeldBy" }
            }
            pending.value = packageName
            return
        }
        if (initiallyHeldBy != null) {
            Logger.i {
                "SystemInstallSerializer: $packageName waiting for $initiallyHeldBy to clear " +
                    "(timeout ${timeoutMs}ms)"
            }
        }
        val acquired =
            withTimeoutOrNull(timeoutMs) {
                while (!pending.compareAndSet(null, packageName)) {
                    pending.first { it == null }
                }
            }
        if (acquired == null) {
            Logger.w {
                "SystemInstallSerializer: timed out waiting for ${pending.value} to clear; force-claiming for $packageName"
            }
            pending.value = packageName
        } else if (initiallyHeldBy != null) {
            Logger.i {
                "SystemInstallSerializer: $packageName acquired gate after waiting for $initiallyHeldBy"
            }
        }
    }

    override fun markCompleted(packageName: String) {
        pending.value = null
    }
}
