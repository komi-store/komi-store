package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.details.domain.model.TranslationResult

internal class GoogleTranslator(
    private val httpClient: () -> HttpClient,
    private val json: Json,
) : Translator {
    override val maxChunkSize: Int = 4500

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        val body =
            httpClient()
                .submitForm(
                    url = "https://translate.googleapis.com/translate_a/single",
                    formParameters =
                        Parameters.build {
                            append("client", "gtx")
                            append("sl", sourceLanguage)
                            append("tl", targetLanguage)
                            append("dt", "t")
                            append("q", text)
                        },
                ).bodyAsText()

        val root = json.parseToJsonElement(body).jsonArray
        val segments = root[0].jsonArray
        val translated =
            segments.joinToString("") { segment ->
                segment.jsonArray[0].jsonPrimitive.content
            }
        val detected =
            runCatching { root[2].jsonPrimitive.content }.getOrNull()

        return TranslationResult(
            translatedText = translated,
            detectedSourceLanguage = detected,
        )
    }
}
