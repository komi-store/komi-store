package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.details.domain.model.TranslationResult
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

        return requestMutex.withLock {
            lastRequestAt?.let { mark ->
                val elapsed = mark.elapsedNow().inWholeMilliseconds
                if (elapsed < BAIDU_MIN_INTERVAL_MS) {
                    delay(BAIDU_MIN_INTERVAL_MS - elapsed)
                }
            }
            lastRequestAt = TimeSource.Monotonic.markNow()

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

            TranslationResult(
                translatedText = translation,
                detectedSourceLanguage = detected,
            )
        }
    }

    private fun mapSourceLanguage(code: String): String = baiduLanguageCode(code, allowAuto = true)

    private fun mapTargetLanguage(code: String): String = baiduLanguageCode(code, allowAuto = false)

    private fun reverseLanguage(code: String): String = baiduLanguageFromCode(code)

    companion object {
        private const val BAIDU_TRANSLATE_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate"
        private const val BAIDU_MIN_INTERVAL_MS = 1_100L
        private val requestMutex = Mutex()
        private var lastRequestAt: TimeMark? = null
    }
}

private val BAIDU_LANGUAGE_CODES =
    mapOf(
        "ar" to "ara",
        "bn" to "ben",
        "zh" to "zh",
        "zh-cn" to "zh",
        "zh-hans" to "zh",
        "zh-tw" to "cht",
        "zh-hant" to "cht",
        "cs" to "cs",
        "da" to "dan",
        "nl" to "nl",
        "en" to "en",
        "fi" to "fin",
        "fr" to "fra",
        "de" to "de",
        "el" to "el",
        "he" to "he",
        "hi" to "hi",
        "hu" to "hu",
        "id" to "id",
        "it" to "it",
        "ja" to "jp",
        "ko" to "kor",
        "ms" to "may",
        "no" to "nor",
        "pl" to "pl",
        "pt" to "pt",
        "pt-br" to "pt",
        "ro" to "rom",
        "ru" to "ru",
        "es" to "spa",
        "sv" to "swe",
        "th" to "th",
        "tr" to "tr",
        "uk" to "uk",
        "uz" to "uz",
        "vi" to "vie",
    )

internal fun baiduLanguageCode(
    code: String,
    allowAuto: Boolean,
): String {
    val normalized = code.trim().lowercase()
    if (normalized.isEmpty() || normalized == "auto") {
        if (allowAuto) return "auto"
        throw TranslationUnsupportedLanguageException("Baidu target language cannot be auto")
    }
    return BAIDU_LANGUAGE_CODES[normalized]
        ?: throw TranslationUnsupportedLanguageException("Baidu does not support language $code")
}

internal fun baiduLanguageFromCode(code: String): String {
    val normalized = code.trim().lowercase()
    return when (normalized) {
        "zh" -> "zh-CN"
        "cht" -> "zh-TW"
        else -> BAIDU_APP_CODES[normalized] ?: normalized
    }
}

private val BAIDU_APP_CODES =
    BAIDU_LANGUAGE_CODES.entries
        .filter { (appCode, _) ->
            appCode != "zh" &&
                appCode != "zh-cn" &&
                appCode != "zh-hans" &&
                appCode != "zh-tw" &&
                appCode != "zh-hant"
        }.associate { (appCode, baiduCode) -> baiduCode to appCode }

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
