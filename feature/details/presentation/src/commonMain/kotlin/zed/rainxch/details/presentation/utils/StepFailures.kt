package zed.rainxch.details.presentation.utils

import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

// What an install, download, file or store step raises; cancellation and anything else, a bug, propagate.
internal inline fun <T> recoverStepFailure(
    recover: (Exception) -> T,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        recover(e)
    } catch (e: UntypedFailureException) {
        recover(e)
    } catch (e: LocalStoreException) {
        recover(e)
    } catch (e: SystemPackagesException) {
        recover(e)
    } catch (e: IllegalStateException) {
        recover(e)
    } catch (e: IllegalArgumentException) {
        recover(e)
    } catch (e: UnsupportedOperationException) {
        recover(e)
    } catch (e: SecurityException) {
        recover(e)
    }
