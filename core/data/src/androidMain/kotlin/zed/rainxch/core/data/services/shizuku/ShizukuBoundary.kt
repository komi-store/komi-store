package zed.rainxch.core.data.services.shizuku

import kotlinx.coroutines.CancellationException
import zed.rainxch.core.domain.model.error.SystemPackagesException

// Shizuku 13.1.5 rethrows a dead binder's RemoteException as a bare RuntimeException (Shizuku.rethrowAsRuntimeException).
internal inline fun <T> shizukuCall(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        throw SystemPackagesException(e)
    }
