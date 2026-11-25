package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.core.data.network.networkFailures
import zed.rainxch.details.domain.model.TranslationProviderNotConfiguredException
import zed.rainxch.details.domain.model.TranslationResult

internal class DeeplTranslator(
    private val httpClient: () -> HttpClient,
    private val json: Json,
    private val authKey: String,
) : Translator {
    override val maxChunkSize: Int = 30_000

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        if (authKey.isBlank()) {
            throw TranslationProviderNotConfiguredException(
                "DeepL auth key not configured",
            )
        }

        val (status, body) =
            networkFailures {
                val response = requestTranslation(text, mapLanguageCode(targetLanguage), mapSourceLanguageCode(sourceLanguage))
                response.status to response.bodyAsText()
            }
        if (!status.isSuccess()) {
            translationFailed("DeepL HTTP ${status.value}: ${errorMessage(body) ?: body.take(ERROR_BODY_PREVIEW_CHARS)}")
        }
        return decodingAnswer("DeepL", body) { parseTranslation(body) }
    }

    private suspend fun requestTranslation(
        text: String,
        target: String,
        source: String?,
    ): HttpResponse {
        val host =
            if (authKey.endsWith(":fx")) {
                "https://api-free.deepl.com"
            } else {
                "https://api.deepl.com"
            }
        return httpClient().submitForm(
            url = "$host/v2/translate",
            formParameters =
                Parameters.build {
                    append("text", text)
                    append("target_lang", target)
                    if (source != null) append("source_lang", source)
                    append("preserve_formatting", "1")
                },
        ) {
            header(HttpHeaders.Authorization, "DeepL-Auth-Key $authKey")
        }
    }

    private fun errorMessage(body: String): String? =
        runCatching {
            json
                .parseToJsonElement(body)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()

    private fun parseTranslation(body: String): TranslationResult {
        val root = json.parseToJsonElement(body).jsonObject

        val translations = root["translations"]?.jsonArray
        val errorMessage = root["message"]?.jsonPrimitive?.content
        if (translations.isNullOrEmpty()) {
            translationFailed("DeepL ${errorMessage?.let { "error: $it" } ?: "response missing translations"}")
        }

        val first = translations.firstOrNull()?.jsonObject ?: translationFailed("DeepL response missing translations")
        val translation = first["text"]?.jsonPrimitive?.content ?: translationFailed("DeepL response missing translation text")
        val detected =
            first["detected_source_language"]
                ?.jsonPrimitive
                ?.content
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }

        return TranslationResult(
            translatedText = translation,
            detectedSourceLanguage = detected,
        )
    }

    private fun mapLanguageCode(code: String): String =
        when (code.lowercase()) {
            "en" -> "EN-US"
            "pt" -> "PT-PT"
            "pt-br" -> "PT-BR"
            "zh", "zh-cn", "zh-hans" -> "ZH-HANS"
            "zh-tw", "zh-hant" -> "ZH-HANT"
            else -> code.uppercase()
        }

    private fun mapSourceLanguageCode(code: String): String? =
        when (code.lowercase()) {
            "", "auto" -> null
            "zh", "zh-cn", "zh-hans" -> "ZH"
            "zh-tw", "zh-hant" -> "ZH"
            "pt-br", "pt" -> "PT"
            else -> code.uppercase().substringBefore('-')
        }
}
