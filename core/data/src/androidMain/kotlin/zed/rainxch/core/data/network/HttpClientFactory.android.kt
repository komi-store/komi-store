package zed.rainxch.core.data.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.Credentials
import zed.rainxch.core.domain.model.settings.ProxyConfig
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy

actual fun createPlatformHttpClient(proxyConfig: ProxyConfig): HttpClient {
    Authenticator.setDefault(null)

    return HttpClient(OkHttp) {
        engine {
            when (proxyConfig) {
                is ProxyConfig.None -> {
                    proxy = Proxy.NO_PROXY
                }

                is ProxyConfig.System -> {
                    proxy = resolveAndroidSystemProxy()
                }

                is ProxyConfig.Http -> {
                    proxy =
                        Proxy(
                            Proxy.Type.HTTP,
                            InetSocketAddress.createUnresolved(proxyConfig.host, proxyConfig.port),
                        )
                    if (proxyConfig.username != null) {
                        config {
                            proxyAuthenticator { _, response ->
                                response.request
                                    .newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        Credentials.basic(
                                            proxyConfig.username!!,
                                            proxyConfig.password.orEmpty(),
                                        ),
                                    ).build()
                            }
                        }
                    }
                }

                is ProxyConfig.Socks -> {
                    proxy =
                        Proxy(
                            Proxy.Type.SOCKS,
                            InetSocketAddress.createUnresolved(proxyConfig.host, proxyConfig.port),
                        )

                    if (proxyConfig.username != null) {
                        Authenticator.setDefault(
                            object : Authenticator() {
                                override fun getPasswordAuthentication(): PasswordAuthentication? {
                                    if (requestingHost == proxyConfig.host &&
                                        requestingPort == proxyConfig.port
                                    ) {
                                        return PasswordAuthentication(
                                            proxyConfig.username,
                                            proxyConfig.password.orEmpty().toCharArray(),
                                        )
                                    }
                                    return null
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun resolveAndroidSystemProxy(): Proxy {
    val httpsHost = System.getProperty("https.proxyHost")?.takeIf { it.isNotBlank() }
    val httpsPort = System.getProperty("https.proxyPort")?.toIntOrNull()?.takeIf { it in 1..65535 }
    if (httpsHost != null && httpsPort != null) {
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(httpsHost, httpsPort))
    }

    val httpHost = System.getProperty("http.proxyHost")?.takeIf { it.isNotBlank() }
    val httpPort = System.getProperty("http.proxyPort")?.toIntOrNull()?.takeIf { it in 1..65535 }
    if (httpHost != null && httpPort != null) {
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(httpHost, httpPort))
    }

    return Proxy.NO_PROXY
}
