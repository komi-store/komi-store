package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

        val host =
            if (authKey.endsWith(":fx")) {
                "https://api-free.deepl.com"
            } else {
                "https://api.deepl.com"
            }
        val endpoint = "$host/v2/translate"
        val target = mapLanguageCode(targetLanguage)
        val source = mapSourceLanguageCode(sourceLanguage)

        val response =
            httpClient().submitForm(
                url = endpoint,
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

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message =
                runCatching {
                    json
                        .parseToJsonElement(body)
                        .jsonObject["message"]
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull()
            throw RuntimeException(
                "DeepL HTTP ${response.status.value}: ${message ?: body.take(200)}",
            )
        }

        val root =
            try {
                json.parseToJsonElement(body).jsonObject
            } catch (e: SerializationException) {
                throw RuntimeException(
                    "DeepL returned non-JSON response: ${body.take(200)}",
                    e,
                )
            }

        val translations = root["translations"]?.jsonArray
        val errorMessage = root["message"]?.jsonPrimitive?.content
        if (translations.isNullOrEmpty()) {
            throw RuntimeException(
                "DeepL ${errorMessage?.let { "error: $it" } ?: "response missing translations"}",
            )
        }

        val first =
            translations.firstOrNull()?.jsonObject
                ?: throw RuntimeException("DeepL response missing translations")
        val translation =
            first["text"]?.jsonPrimitive?.content
                ?: throw RuntimeException("DeepL response missing translation text")
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
