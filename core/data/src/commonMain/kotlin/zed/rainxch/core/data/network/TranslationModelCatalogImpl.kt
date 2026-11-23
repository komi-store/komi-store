package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.core.domain.network.TranslationModelCatalog

class TranslationModelCatalogException(
    message: String,
) : RuntimeException(message)

class TranslationModelCatalogImpl(
    private val client: () -> HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationModelCatalog {
    override suspend fun listModels(
        baseUrl: String,
        apiKey: String?,
    ): List<String> {
        val root = baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        require(root.isNotEmpty()) { "OpenAI-compatible base URL is empty" }

        val response =
            client().get("$root/models") {
                apiKey?.takeIf { it.isNotBlank() }?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw TranslationModelCatalogException("Model list HTTP ${response.status.value}: ${body.take(ERROR_BODY_LIMIT)}")
        }

        val models =
            json
                .parseToJsonElement(body)
                .jsonObject["data"]
                ?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?.filter { it.isNotBlank() }
                .orEmpty()
        if (models.isEmpty()) {
            throw TranslationModelCatalogException("Model list response has no data[].id")
        }
        return models
    }

    private companion object {
        private const val ERROR_BODY_LIMIT = 200
    }
}
