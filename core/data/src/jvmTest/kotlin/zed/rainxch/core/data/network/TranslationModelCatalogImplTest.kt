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
}
