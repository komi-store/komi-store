package zed.rainxch.core.data.secure

import co.touchlab.kermit.Logger
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import zed.rainxch.core.domain.model.error.LocalStoreException
import java.io.IOException
import java.security.GeneralSecurityException

@PublishedApi
internal val ksafeSafeLogger = Logger.withTag("KSafeSafe")

suspend inline fun <reified T> KSafe.safeGet(
    key: String,
    defaultValue: T,
): T = guardStorage({ "safeGet($key) failed; returning default" }, { defaultValue }) { get(key, defaultValue) }

inline fun <reified T> KSafe.safeGetFlow(
    key: String,
    defaultValue: T,
): Flow<T> {
    val upstream =
        guardStorage({ "safeGetFlow($key) build failed; emitting default" }, { null }) { getFlow(key, defaultValue) }
            ?: return flow { emit(defaultValue) }
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
    guardStorage({ "safePut($key) failed; value not persisted" }, { false }) {
        put(key, value)
        true
    }

suspend fun KSafe.safeDelete(key: String): Boolean =
    guardStorage({ "safeDelete($key) failed" }, { false }) {
        delete(key)
        true
    }

// For a write the caller has to know about: the failures safePut swallows reach it as LocalStoreException.
suspend inline fun <reified T> KSafe.putOrRaise(
    key: String,
    value: T,
) {
    val failure = storageFailure { put(key, value) } ?: return
    throw LocalStoreException(failure)
}

@PublishedApi
internal inline fun storageFailure(block: () -> Unit): Exception? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        e
    } catch (e: GeneralSecurityException) {
        e
    } catch (e: RuntimeException) {
        e
    }

// Beyond its DataStore and cipher failures, KSafe 3.3.0 on desktop throws a bare RuntimeException when the OS keychain refuses a key.
@PublishedApi
internal inline fun <T> guardStorage(
    message: () -> String,
    fallback: () -> T,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ksafeSafeLogger.w(e, message = message)
        fallback()
    } catch (e: GeneralSecurityException) {
        ksafeSafeLogger.w(e, message = message)
        fallback()
    } catch (e: RuntimeException) {
        ksafeSafeLogger.w(e, message = message)
        fallback()
    }
