package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationModelCatalogImplTest {
    @Test
    fun lists_models_from_the_openai_endpoint() =
        runBlocking {
            var requestedUrl = ""
            var authorization: String? = null
            val engine =
                MockEngine { request ->
                    requestedUrl = request.url.toString()
                    authorization = request.headers[HttpHeaders.Authorization]
                    respond(
                        content = """{"data":[{"id":"glm-4-flash"},{"id":"glm-4-plus"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val catalog = TranslationModelCatalogImpl(client = { HttpClient(engine) })

            val models = catalog.listModels("https://open.bigmodel.cn/api/paas/v4", "test-key")

            assertEquals(listOf("glm-4-flash", "glm-4-plus"), models)
            assertEquals("https://open.bigmodel.cn/api/paas/v4/models", requestedUrl)
            assertEquals("Bearer test-key", authorization)
        }

    @Test
    fun strips_a_chat_completions_path_and_query() =
        runBlocking {
            var requestedUrl = ""
            val engine =
                MockEngine { request ->
                    requestedUrl = request.url.toString()
                    respond(
                        content = """{"data":[{"id":"m1"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val catalog = TranslationModelCatalogImpl(client = { HttpClient(engine) })

            catalog.listModels("https://example.test/v1/chat/completions?api-version=1", null)

            assertEquals("https://example.test/v1/models", requestedUrl)
        }

    @Test
    fun reports_a_non_success_status_as_a_catalog_error() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = "unauthorized",
                        status = HttpStatusCode.Unauthorized,
                    )
                }
            val catalog = TranslationModelCatalogImpl(client = { HttpClient(engine) })

            assertFailsWith<TranslationModelCatalogException> {
                catalog.listModels("https://example.test/v1", "bad-key")
            }
            Unit
        }

    @Test
    fun reports_an_empty_model_list_as_a_catalog_error() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = """{"data":[]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val catalog = TranslationModelCatalogImpl(client = { HttpClient(engine) })

            assertFailsWith<TranslationModelCatalogException> {
                catalog.listModels("https://example.test/v1", null)
            }
            Unit
        }
}
