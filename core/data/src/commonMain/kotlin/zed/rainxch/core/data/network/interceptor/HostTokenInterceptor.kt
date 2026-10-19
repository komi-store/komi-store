@file:Suppress("MatchingDeclarationName")

package zed.rainxch.core.data.network.interceptor

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.authority
import zed.rainxch.core.domain.model.account.HostNames
import zed.rainxch.core.domain.repository.HostTokenRepository
import kotlin.coroutines.cancellation.CancellationException

class HostTokenInterceptorConfig {
    var repository: HostTokenRepository? = null
}

val HostTokenInterceptor =
    createClientPlugin("HostTokenInterceptor", ::HostTokenInterceptorConfig) {
        val repository =
            requireNotNull(pluginConfig.repository) {
                "HostTokenRepository must be provided"
            }
        onRequest { request, _ ->
            if (request.url.protocol != URLProtocol.HTTPS) return@onRequest
            if (!request.headers[HttpHeaders.Authorization].isNullOrBlank()) return@onRequest
            val host = HostNames.apiHostToTokenHost(request.url.authority)
            if (host.isBlank()) return@onRequest
            val token =
                try {
                    repository.get(host)?.token
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            if (!token.isNullOrBlank()) {
                request.header(HttpHeaders.Authorization, "token $token")
            }
        }
    }
