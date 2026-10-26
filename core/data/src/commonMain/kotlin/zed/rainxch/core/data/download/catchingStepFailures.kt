package zed.rainxch.core.data.download

import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

// The failures a download, install or database step raises; cancellation and anything else, a bug, propagate.
internal inline fun <T> catchingStepFailures(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: AssetSourceGoneException) {
        Result.failure(e)
    } catch (e: LocalStoreException) {
        Result.failure(e)
    } catch (e: SystemPackagesException) {
        Result.failure(e)
    } catch (e: IllegalStateException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } catch (e: UnsupportedOperationException) {
        Result.failure(e)
    } catch (e: SecurityException) {
        Result.failure(e)
    }
