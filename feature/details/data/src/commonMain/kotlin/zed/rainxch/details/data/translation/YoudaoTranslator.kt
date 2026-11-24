package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.details.domain.model.TranslationResult
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
internal class YoudaoTranslator(
    private val httpClient: () -> HttpClient,
    private val json: Json,
    private val appKey: String,
    private val appSecret: String,
) : Translator {
    override val maxChunkSize: Int = 4500

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        if (appKey.isBlank() || appSecret.isBlank()) {
            throw TranslationProviderNotConfiguredException(
                "Youdao appKey/appSecret not configured",
            )
        }

        val salt = Uuid.random().toString()
        val curtime = Clock.System.now().epochSeconds.toString()
        val signInput = buildSignInput(text)
        val sign = sha256Hex(appKey + signInput + salt + curtime + appSecret)

        val from = mapLanguageCode(sourceLanguage)
        val to = mapLanguageCode(targetLanguage)

        val response =
            httpClient().submitForm(
                url = "https://openapi.youdao.com/api",
                formParameters =
                    Parameters.build {
                        append("q", text)
                        append("from", from)
                        append("to", to)
                        append("appKey", appKey)
                        append("salt", salt)
                        append("sign", sign)
                        append("signType", "v3")
                        append("curtime", curtime)
                    },
            )

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val errorCode = root["errorCode"]?.jsonPrimitive?.content
        check(errorCode == "0") { "Youdao translate error: $errorCode" }

        val translation =
            root["translation"]
                ?.jsonArray
                ?.joinToString("\n") { it.jsonPrimitive.content }
                .orEmpty()

        val detected =
            root["l"]
                ?.jsonPrimitive
                ?.content
                ?.substringBefore('2')
                ?.takeIf { it.isNotBlank() }
                ?.let { reverseMapLanguageCode(it) }

        return TranslationResult(
            translatedText = translation,
            detectedSourceLanguage = detected,
        )
    }

    private fun buildSignInput(q: String): String =
        if (q.length <= SIGN_INPUT_MAX_LENGTH) {
            q
        } else {
            q.take(SIGN_INPUT_EDGE_LENGTH) + q.length.toString() + q.takeLast(SIGN_INPUT_EDGE_LENGTH)
        }

    private fun sha256Hex(input: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(input.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun mapLanguageCode(code: String): String =
        when (code.lowercase()) {
            "auto", "" -> "auto"
            "zh", "zh-cn", "zh-hans" -> "zh-CHS"
            "zh-tw", "zh-hant" -> "zh-CHT"
            else -> code
        }

    private fun reverseMapLanguageCode(code: String): String =
        when (code) {
            "zh-CHS" -> "zh"
            "zh-CHT" -> "zh-TW"
            else -> code
        }

    private companion object {
        const val SIGN_INPUT_MAX_LENGTH = 20
        const val SIGN_INPUT_EDGE_LENGTH = 10
    }
}
