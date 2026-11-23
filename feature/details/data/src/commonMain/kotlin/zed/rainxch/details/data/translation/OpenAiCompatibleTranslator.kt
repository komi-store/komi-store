package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import zed.rainxch.core.domain.model.settings.OpenAiTranslationPrompt
import zed.rainxch.core.domain.model.settings.SupportedTranslationLanguages
import zed.rainxch.details.domain.model.TranslationResult

internal class OpenAiCompatibleTranslator(
    private val httpClient: () -> HttpClient,
    private val json: Json,
    private val endpoint: String,
    private val model: String,
    private val apiKey: String? = null,
    private val userAgent: String? = null,
    private val promptTemplate: String? = null,
) : Translator {
    override val maxChunkSize: Int = 2_000

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        requireConfigured()
        val prompt = buildPrompt(targetLanguage, sourceLanguage, text)
        val response = postCompletion(prompt)
        if (!response.status.isSuccess()) {
            throw RuntimeException(
                "Translation HTTP ${response.status.value}: ${response.bodyAsText().take(200)}",
            )
        }

        val translated =
            if (response.contentType()?.match(ContentType.Application.Json) == true) {
                readCompleteResponse(response.bodyAsText())
            } else {
                readStreamingResponse(response.bodyAsText())
            }

        return TranslationResult(
            translatedText = translated.trim(),
            detectedSourceLanguage = null,
        )
    }

    private fun requireConfigured() {
        if (endpoint.isBlank() || model.isBlank()) {
            throw TranslationProviderNotConfiguredException(
                "OpenAI-compatible endpoint/model not configured",
            )
        }
        if (apiKey != null && apiKey.isBlank()) {
            throw TranslationProviderNotConfiguredException(
                "OpenAI-compatible provider API key not configured",
            )
        }
    }

    private fun buildPrompt(
        targetLanguage: String,
        sourceLanguage: String,
        text: String,
    ): String {
        val targetName =
            SupportedTranslationLanguages.findByCode(targetLanguage)?.displayName
                ?: targetLanguage
        val sourceName =
            SupportedTranslationLanguages
                .findByCode(sourceLanguage)
                ?.displayName
                ?.takeIf { sourceLanguage.isNotBlank() && sourceLanguage != "auto" }
        return OpenAiTranslationPrompt.render(
            template = promptTemplate.orEmpty(),
            targetLanguage = targetName,
            sourceLanguage = sourceName,
            text = text,
        )
    }

    private suspend fun postCompletion(prompt: String): HttpResponse =
        httpClient().post(endpoint) {
            contentType(ContentType.Application.Json)
            userAgent?.let { header("User-Agent", it) }
            apiKey?.let { header("Authorization", "Bearer $it") }
            setBody(
                buildJsonObject {
                    put("model", model)
                    put("temperature", 0)
                    put("stream", true)
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    put("content", prompt)
                                },
                            )
                        },
                    )
                }.toString(),
            )
        }

    private fun readCompleteResponse(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        return root
            .choicesMessage()
            ?.messageContent()
            ?: throw RuntimeException("Translation response missing message content")
    }

    private fun readStreamingResponse(body: String): String {
        val content = StringBuilder()
        for (line in body.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val delta =
                json
                    .parseToJsonElement(payload)
                    .jsonObject
                    .choicesMessage()
                    ?.messageContent()
            if (!delta.isNullOrEmpty()) content.append(delta)
        }
        if (content.isEmpty()) {
            throw RuntimeException("Translation response missing streamed message content")
        }
        return content.toString()
    }

    private fun JsonObject.messageContent(): String? =
        this["content"]
            ?.let { it as? JsonPrimitive }
            ?.takeIf { it.isString }
            ?.content

    private fun JsonObject.choicesMessage(): JsonObject? =
        this["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: this["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("delta")
                ?.jsonObject
}
