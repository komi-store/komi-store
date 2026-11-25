package zed.rainxch.details.data.translation

import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.details.domain.model.TranslationResult

internal interface Translator {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult

    val maxChunkSize: Int
}

internal const val ERROR_BODY_PREVIEW_CHARS = 200

// The provider answered with an error status, an error body or no translation.
internal fun translationFailed(
    message: String,
    cause: Throwable? = null,
): Nothing = throw NetworkException(message, cause)

// kotlinx.serialization raises IllegalArgumentException for a body that isn't JSON and for an element of another kind.
internal inline fun <T> decodingAnswer(
    provider: String,
    body: String,
    decode: () -> T,
): T =
    try {
        decode()
    } catch (e: IllegalArgumentException) {
        translationFailed("$provider returned an unreadable response: ${body.take(ERROR_BODY_PREVIEW_CHARS)}", e)
    }
