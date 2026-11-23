package zed.rainxch.details.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationProviderProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun baidu_parses_the_translation_result() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """{"from":"en","to":"zh","trans_result":[{"src":"hello","dst":"你好"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val translator =
                BaiduTranslator(
                    httpClient = { HttpClient(engine) },
                    json = json,
                    appId = "app",
                    appSecret = "secret",
                )

            val result = translator.translate("hello", "zh-CN", "en")

            assertEquals("你好", result.translatedText)
            assertEquals("en", result.detectedSourceLanguage)
        }

    @Test
    fun openai_compatible_collects_the_sse_stream() =
        runBlocking {
            var authorization: String? = null
            val engine =
                MockEngine { request ->
                    authorization = request.headers[HttpHeaders.Authorization]
                    respond(
                        content =
                            """
                            data: {"choices":[{"delta":{"role":"assistant","content":null}}]}

                            data: ping

                            data:


                            data: {"choices":[{"delta":{"content":"你"}}]}

                            data: {"choices":[{"delta":{"content":"好"}}]}

                            data: [DONE]

                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }
            val translator =
                OpenAiCompatibleTranslator(
                    httpClient = { HttpClient(engine) },
                    json = json,
                    endpoint = "https://example.test/v1/chat/completions",
                    model = "test-model",
                    apiKey = "test-key",
                )

            val result = translator.translate("hello", "zh-CN", "en")

            assertEquals("你好", result.translatedText)
            assertEquals("Bearer test-key", authorization)
        }

    @Test
    fun openai_compatible_rejects_a_truncated_completion() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = """{"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val translator =
                OpenAiCompatibleTranslator(
                    httpClient = { HttpClient(engine) },
                    json = json,
                    endpoint = "https://example.test/v1/chat/completions",
                    model = "test-model",
                )

            assertFailsWith<TranslationResponseTruncatedException> {
                translator.translate("hello", "zh-CN", "en")
            }
            Unit
        }

    @Test
    fun openai_compatible_accepts_a_non_streaming_json_response() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = """{"choices":[{"message":{"content":"你好"}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val translator =
                OpenAiCompatibleTranslator(
                    httpClient = { HttpClient(engine) },
                    json = json,
                    endpoint = "https://example.test/v1/chat/completions",
                    model = "test-model",
                )

            val result = translator.translate("hello", "zh-CN", "en")

            assertEquals("你好", result.translatedText)
        }
}
