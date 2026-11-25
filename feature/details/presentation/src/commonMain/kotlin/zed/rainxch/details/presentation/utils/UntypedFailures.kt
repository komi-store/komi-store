package zed.rainxch.details.presentation.utils

import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RefreshException
import kotlin.coroutines.cancellation.CancellationException

// Wraps calls into layers that still throw bare Exception, RuntimeException or Room's SQLiteException; see feature/details/CLAUDE.md.
internal inline fun <T> untypedFailures(block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        throw passThroughOrWrap(e)
    }

internal fun passThroughOrWrap(error: Exception): Exception =
    when (error) {
        is CancellationException,
        is RateLimitException,
        is RefreshException,
        is UntypedFailureException,
        -> error

        else -> UntypedFailureException(error)
    }
