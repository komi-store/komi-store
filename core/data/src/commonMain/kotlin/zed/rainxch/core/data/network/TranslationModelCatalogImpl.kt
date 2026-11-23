package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import zed.rainxch.core.domain.network.TranslationModelCatalog

class TranslationModelCatalogException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class TranslationModelCatalogImpl(
    private val client: () -> HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationModelCatalog {
    override suspend fun listModels(
        baseUrl: String,
        apiKey: String?,
        allowInsecureHttp: Boolean,
    ): List<String> {
        val root =
            baseUrl
                .trim()
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .removeSuffix("/chat/completions")
        if (root.isEmpty()) {
            throw TranslationModelCatalogException("OpenAI-compatible base URL is empty")
        }
        if (root.startsWith("http://", ignoreCase = true) && !allowInsecureHttp) {
            throw TranslationModelCatalogException(
                "Insecure HTTP endpoint requires the allow-insecure-HTTP setting",
            )
        }

        val response =
            client().get("$root/models") {
                apiKey?.takeIf { it.isNotBlank() }?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw TranslationModelCatalogException(
                "Model list HTTP ${response.status.value}: ${body.take(ERROR_BODY_LIMIT)}",
            )
        }

        val models =
            try {
                val data =
                    (json.parseToJsonElement(body) as? JsonObject)
                        ?.get("data") as? JsonArray
                data
                    ?.mapNotNull { item ->
                        val id = (item as? JsonObject)?.get("id") as? JsonPrimitive
                        id?.takeIf { it.isString }?.content
                    }?.filter { it.isNotBlank() }
                    .orEmpty()
            } catch (e: SerializationException) {
                throw TranslationModelCatalogException("Model list is not valid JSON: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw TranslationModelCatalogException("Model list is not valid JSON: ${e.message}", e)
            }
        if (models.isEmpty()) {
            throw TranslationModelCatalogException("Model list response has no data[].id")
        }
        return models
    }

    private companion object {
        private const val ERROR_BODY_LIMIT = 200
    }
}
