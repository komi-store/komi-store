package zed.rainxch.details.data.repository

import androidx.sqlite.SQLiteException
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException

// executeRequest hands Ktor failures back in its Result; only the rate-limit interceptor throws past it.
internal inline fun <T> catchingRateLimit(request: () -> Result<T>): Result<T> =
    try {
        request()
    } catch (e: RateLimitException) {
        Result.failure(e)
    }

// CacheManager.put lets Room's SQLiteException through; a read whose write fails ends like a failed fetch.
internal suspend inline fun <reified T> CacheManager.putOrFailure(
    key: String,
    value: T,
    ttlMillis: Long,
): Result<T> =
    try {
        put(key, value, ttlMillis)
        Result.success(value)
    } catch (e: SQLiteException) {
        Result.failure(LocalStoreException(e))
    }

internal suspend inline fun <reified T> CacheManager.staleOrThrow(
    key: String,
    cause: Throwable,
    onStale: () -> Unit = {},
): T = getStale<T>(key)?.also { onStale() } ?: throw cause

internal suspend inline fun <reified T> CacheManager.freshOrStale(
    key: String,
    ttlMillis: Long,
    onStale: () -> Unit = {},
    fetch: () -> Result<T>,
): T =
    catchingRateLimit(fetch)
        .fold(
            onSuccess = { fresh -> putOrFailure(key, fresh, ttlMillis) },
            onFailure = { cause -> Result.failure<T>(cause) },
        ).getOrElse { cause -> staleOrThrow<T>(key, cause, onStale) }
