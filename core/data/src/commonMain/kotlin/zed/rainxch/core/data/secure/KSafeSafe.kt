package zed.rainxch.core.data.secure

import co.touchlab.kermit.Logger
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

@PublishedApi
internal val ksafeSafeLogger = Logger.withTag("KSafeSafe")

suspend inline fun <reified T> KSafe.safeGet(
    key: String,
    defaultValue: T,
): T =
    try {
        get(key, defaultValue)
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        ksafeSafeLogger.w(t) { "safeGet($key) failed; returning default" }
        defaultValue
    }

inline fun <reified T> KSafe.safeGetFlow(
    key: String,
    defaultValue: T,
): Flow<T> {
    val upstream =
        try {
            getFlow(key, defaultValue)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            ksafeSafeLogger.w(t) { "safeGetFlow($key) build failed; emitting default" }
            return flow { emit(defaultValue) }
        }
    return upstream.catch { e ->
        if (e is CancellationException) throw e
        ksafeSafeLogger.w(e) { "safeGetFlow($key) downstream error; emitting default" }
        emit(defaultValue)
    }
}

suspend inline fun <reified T> KSafe.safePut(
    key: String,
    value: T,
): Boolean =
    try {
        put(key, value)
        true
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        ksafeSafeLogger.w(t) { "safePut($key) failed; value not persisted" }
        false
    }

suspend fun KSafe.safeDelete(key: String): Boolean =
    try {
        delete(key)
        true
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        ksafeSafeLogger.w(t) { "safeDelete($key) failed" }
        false
    }
