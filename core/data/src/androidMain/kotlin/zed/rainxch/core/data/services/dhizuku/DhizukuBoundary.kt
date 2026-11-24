package zed.rainxch.core.data.services.dhizuku

import kotlinx.coroutines.CancellationException
import zed.rainxch.core.domain.model.error.SystemPackagesException

// Dhizuku 2.6.0 rethrows a dead binder's RemoteException as a bare RuntimeException.
internal inline fun <T> dhizukuCall(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        throw SystemPackagesException(e)
    }
