package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.details.domain.model.TranslationResult
import java.security.MessageDigest
import kotlin.random.Random

internal class BaiduTranslator(
    private val httpClient: () -> HttpClient,
    private val json: Json,
    private val appId: String,
    private val appSecret: String,
) : Translator {
    override val maxChunkSize: Int = 1_500

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw TranslationProviderNotConfiguredException(
                "Baidu appId/appSecret not configured",
            )
        }

        val salt = Random.nextLong().toString()
        val sign = baiduSign(appId, text, salt, appSecret)
        val response =
            httpClient().submitForm(
                url = BAIDU_TRANSLATE_URL,
                formParameters =
                    Parameters.build {
                        append("q", text)
                        append("from", mapSourceLanguage(sourceLanguage))
                        append("to", mapTargetLanguage(targetLanguage))
                        append("appid", appId)
                        append("salt", salt)
                        append("sign", sign)
                    },
            )

        val body = response.bodyAsText()
        val root =
            if (body.trimStart().startsWith("{")) {
                json.parseToJsonElement(body).jsonObject
            } else {
                throw RuntimeException(
                    "Baidu translate HTTP ${response.status.value}: ${body.take(200)}",
                )
            }

        val errorCode = root["error_code"]?.jsonPrimitive?.content
        if (errorCode != null && errorCode != "52000") {
            val message = root["error_msg"]?.jsonPrimitive?.content.orEmpty()
            throw RuntimeException("Baidu translate error $errorCode: $message")
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Baidu translate HTTP ${response.status.value}")
        }

        val translation =
            root["trans_result"]
                ?.jsonArray
                ?.joinToString("\n") {
                    it.jsonObject["dst"]
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                }.orEmpty()
        if (translation.isBlank()) {
            throw RuntimeException("Baidu translate response missing trans_result")
        }

        val detected =
            root["from"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() && it != "auto" }
                ?.let(::reverseLanguage)

        // The free tier allows one request per second; keep the next queued translation outside
        // that window instead of failing the second card with 54003.
        delay(BAIDU_MIN_INTERVAL_MS)

        return TranslationResult(
            translatedText = translation,
            detectedSourceLanguage = detected,
        )
    }

    private fun mapSourceLanguage(code: String): String =
        when (code.lowercase()) {
            "", "auto" -> "auto"
            "zh", "zh-cn", "zh-hans" -> "zh"
            "zh-tw", "zh-hant" -> "cht"
            else -> code.substringBefore('-').lowercase()
        }

    private fun mapTargetLanguage(code: String): String =
        when (code.lowercase()) {
            "zh", "zh-cn", "zh-hans" -> "zh"
            "zh-tw", "zh-hant" -> "cht"
            else -> code.substringBefore('-').lowercase()
        }

    private fun reverseLanguage(code: String): String =
        when (code) {
            "zh" -> "zh-CN"
            "cht" -> "zh-TW"
            else -> code
        }

    companion object {
        private const val BAIDU_TRANSLATE_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate"
        private const val BAIDU_MIN_INTERVAL_MS = 1_100L
    }
}

internal fun baiduSign(
    appId: String,
    text: String,
    salt: String,
    appSecret: String,
): String {
    val digest = MessageDigest.getInstance("MD5").digest((appId + text + salt + appSecret).encodeToByteArray())
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            if (value < 0x10) append('0')
            append(value.toString(16))
        }
    }
}
