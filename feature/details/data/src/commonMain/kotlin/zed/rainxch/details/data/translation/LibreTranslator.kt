package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.details.domain.model.TranslationResult

internal class LibreTranslator(
    private val httpClient: () -> HttpClient,
    private val json: Json,
    private val baseUrl: String,
    private val apiKey: String?,
) : Translator {
    override val maxChunkSize: Int = 2_000

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        if (baseUrl.isBlank()) {
            throw TranslationProviderNotConfiguredException(
                "LibreTranslate instance URL not configured",
            )
        }

        val target =
            mapTargetLanguageCode(targetLanguage)
                ?: error("LibreTranslate target language required (got '$targetLanguage')")
        val response = requestTranslation(text, target, mapSourceLanguageCode(sourceLanguage))
        val body = response.bodyAsText()
        check(response.status.isSuccess()) {
            "LibreTranslate HTTP ${response.status.value}: ${errorMessage(body) ?: body.take(ERROR_BODY_PREVIEW_CHARS)}"
        }
        return parseTranslation(body)
    }

    private suspend fun requestTranslation(
        text: String,
        target: String,
        source: String,
    ): HttpResponse =
        httpClient().submitForm(
            url = baseUrl.trimEnd('/') + "/translate",
            formParameters =
                Parameters.build {
                    append("q", text)
                    append("source", source)
                    append("target", target)
                    append("format", "text")
                    if (!apiKey.isNullOrBlank()) append("api_key", apiKey)
                },
        )

    private fun errorMessage(body: String): String? =
        runCatching {
            json
                .parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()

    private fun parseTranslation(body: String): TranslationResult {
        val root =
            try {
                json.parseToJsonElement(body).jsonObject
            } catch (e: SerializationException) {
                throw IllegalStateException("LibreTranslate returned non-JSON response: ${body.take(ERROR_BODY_PREVIEW_CHARS)}", e)
            }

        val apiError = root["error"]?.jsonPrimitive?.content
        check(apiError == null) { "LibreTranslate error: $apiError" }

        val translation =
            root["translatedText"]?.jsonPrimitive?.content
                ?: error("LibreTranslate response missing translatedText")
        val detected =
            root["detectedLanguage"]
                ?.jsonObject
                ?.get("language")
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }

        return TranslationResult(
            translatedText = translation,
            detectedSourceLanguage = detected,
        )
    }

    private fun mapSourceLanguageCode(code: String): String =
        when (code.lowercase()) {
            "", "auto" -> "auto"
            "zh-cn", "zh-hans" -> "zh"
            "zh-tw", "zh-hant" -> "zt"
            else -> code.lowercase().substringBefore('-')
        }

    private fun mapTargetLanguageCode(code: String): String? =
        when (code.lowercase()) {
            "", "auto" -> null
            "zh-cn", "zh-hans" -> "zh"
            "zh-tw", "zh-hant" -> "zt"
            else -> code.lowercase().substringBefore('-')
        }
}
