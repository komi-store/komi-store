package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
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
                ?: throw RuntimeException("LibreTranslate target language required (got '$targetLanguage')")
        val source = mapSourceLanguageCode(sourceLanguage)
        val endpoint = baseUrl.trimEnd('/') + "/translate"

        val response =
            httpClient().submitForm(
                url = endpoint,
                formParameters =
                    Parameters.build {
                        append("q", text)
                        append("source", source)
                        append("target", target)
                        append("format", "text")
                        if (!apiKey.isNullOrBlank()) append("api_key", apiKey)
                    },
            )

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message =
                runCatching {
                    json
                        .parseToJsonElement(body)
                        .jsonObject["error"]
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull()
            throw RuntimeException(
                "LibreTranslate HTTP ${response.status.value}: ${message ?: body.take(200)}",
            )
        }

        val root =
            try {
                json.parseToJsonElement(body).jsonObject
            } catch (e: SerializationException) {
                throw RuntimeException(
                    "LibreTranslate returned non-JSON response: ${body.take(200)}",
                    e,
                )
            }

        root["error"]?.jsonPrimitive?.content?.let { msg ->
            throw RuntimeException("LibreTranslate error: $msg")
        }

        val translation =
            root["translatedText"]?.jsonPrimitive?.content
                ?: throw RuntimeException("LibreTranslate response missing translatedText")
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
