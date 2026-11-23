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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import zed.rainxch.core.domain.model.settings.OpenAiTranslationPrompt
import zed.rainxch.core.domain.model.settings.SupportedTranslationLanguages
import zed.rainxch.details.domain.model.TranslationResult

internal class TranslationResponseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

@Suppress("SwallowedException")
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

        val body = response.bodyAsText()
        val translated =
            if (body.lineSequence().any { it.startsWith("data:") }) {
                readStreamingResponse(body)
            } else {
                readCompleteResponse(body)
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

    @Suppress("ThrowsCount")
    private fun readCompleteResponse(body: String): String {
        val root =
            try {
                json.parseToJsonElement(body) as? JsonObject
            } catch (e: SerializationException) {
                throw TranslationResponseException("Translation response is not valid JSON: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw TranslationResponseException("Translation response is not valid JSON: ${e.message}", e)
            } ?: throw TranslationResponseException("Translation response is not a JSON object")
        return root
            .choicesMessage()
            ?.messageContent()
            ?: throw TranslationResponseException("Translation response missing message content")
    }

    private fun readStreamingResponse(body: String): String {
        val content = StringBuilder()
        for (line in body.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val delta = parseStreamFrame(payload)
            if (!delta.isNullOrEmpty()) content.append(delta)
        }
        if (content.isEmpty()) {
            throw TranslationResponseException("Translation response missing streamed message content")
        }
        return content.toString()
    }

    @Suppress("ReturnCount")
    private fun parseStreamFrame(payload: String): String? {
        if (payload.isEmpty()) return null
        val root =
            try {
                json.parseToJsonElement(payload) as? JsonObject
            } catch (e: SerializationException) {
                return null
            } catch (e: IllegalArgumentException) {
                return null
            } ?: return null
        return root.choicesMessage()?.messageContent()
    }

    private fun JsonObject.messageContent(): String? = this["content"].textContent()

    private fun kotlinx.serialization.json.JsonElement?.textContent(): String? =
        when (this) {
            is JsonPrimitive -> {
                takeIf { it.isString }?.content
            }

            is kotlinx.serialization.json.JsonArray -> {
                mapNotNull { (it as? JsonObject)?.get("text").textContent() }
                    .joinToString("")
                    .takeIf { it.isNotEmpty() }
            }

            else -> {
                null
            }
        }

    private fun JsonObject.choicesMessage(): JsonObject? {
        val choice = (this["choices"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject
        return (choice?.get("message") as? JsonObject) ?: (choice?.get("delta") as? JsonObject)
    }
}
