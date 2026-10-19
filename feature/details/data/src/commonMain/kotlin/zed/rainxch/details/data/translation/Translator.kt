package zed.rainxch.details.data.translation

import zed.rainxch.details.domain.model.TranslationResult

internal interface Translator {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult

    val maxChunkSize: Int
}

internal class TranslationProviderNotConfiguredException(
    message: String,
) : RuntimeException(message)
