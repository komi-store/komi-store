package zed.rainxch.core.data.services

import kotlinx.coroutines.CancellationException
import zed.rainxch.core.domain.model.error.SystemPackagesException

// System services rethrow a failed binder call as a bare RuntimeException (RemoteException.rethrowFromSystemServer).
internal inline fun <T> systemServiceCall(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        throw SystemPackagesException(e)
    }
