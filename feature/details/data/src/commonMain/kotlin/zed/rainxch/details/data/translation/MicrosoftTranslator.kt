package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import zed.rainxch.details.domain.model.TranslationResult

internal class MicrosoftTranslator(
    private val httpClient: () -> HttpClient,
    private val json: Json,
    private val subscriptionKey: String,
    private val subscriptionRegion: String,
) : Translator {
    override val maxChunkSize: Int = 45_000

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        if (subscriptionKey.isBlank()) {
            throw TranslationProviderNotConfiguredException(
                "Microsoft Translator subscription key not configured",
            )
        }

        val target = mapLanguageCode(targetLanguage)
        val source = mapSourceLanguageCode(sourceLanguage)
        val sourceQuery = source?.let { "&from=$it" }.orEmpty()
        val url =
            "https://api.cognitive.microsofttranslator.com/translate" +
                "?api-version=3.0&to=$target$sourceQuery"

        val requestBody: JsonArray =
            buildJsonArray {
                addJsonObject {
                    put("Text", text)
                }
            }

        val response =
            httpClient().post(url) {
                header("Ocp-Apim-Subscription-Key", subscriptionKey)
                if (subscriptionRegion.isNotBlank() && !subscriptionRegion.equals("global", ignoreCase = true)) {
                    header("Ocp-Apim-Subscription-Region", subscriptionRegion.lowercase())
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val errorMsg =
                runCatching {
                    json
                        .parseToJsonElement(body)
                        .jsonObject["error"]
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull()
            throw RuntimeException(
                "Microsoft Translator HTTP ${response.status.value}: ${errorMsg ?: body.take(200)}",
            )
        }

        val parsed =
            try {
                json.parseToJsonElement(body)
            } catch (e: SerializationException) {
                throw RuntimeException(
                    "Microsoft Translator returned non-JSON response: ${body.take(200)}",
                    e,
                )
            }

        if (parsed is JsonObject) {
            val errorMsg =
                parsed["error"]
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.content
            throw RuntimeException("Microsoft Translator error: ${errorMsg ?: body}")
        }

        val first =
            parsed.jsonArray.firstOrNull()?.jsonObject
                ?: throw RuntimeException("Microsoft Translator response empty")

        val translation =
            first["translations"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                ?: throw RuntimeException("Microsoft Translator response missing translation text")

        val detected =
            first["detectedLanguage"]
                ?.jsonObject
                ?.get("language")
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
            "zh", "zh-cn", "zh-hans" -> "zh-Hans"
            "zh-tw", "zh-hant" -> "zh-Hant"
            "pt", "pt-br" -> "pt-br"
            "pt-pt" -> "pt-pt"
            "no" -> "nb"
            "sr-cyrl" -> "sr-Cyrl"
            "sr-latn" -> "sr-Latn"
            else -> code.lowercase()
        }

    private fun mapSourceLanguageCode(code: String): String? =
        when (code.lowercase()) {
            "", "auto" -> null
            else -> mapLanguageCode(code)
        }
}
