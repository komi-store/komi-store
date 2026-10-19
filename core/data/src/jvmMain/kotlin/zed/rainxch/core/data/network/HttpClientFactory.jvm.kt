package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Credentials
import zed.rainxch.core.domain.model.settings.ProxyConfig
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector

actual fun createPlatformHttpClient(proxyConfig: ProxyConfig): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                buildOsTrustChainOrNull()?.let { chain ->
                    sslSocketFactory(chain.socketFactory, chain.trustManager)
                }

                Authenticator.setDefault(null)

                when (proxyConfig) {
                    is ProxyConfig.None -> {
                        proxy(Proxy.NO_PROXY)
                    }

                    is ProxyConfig.System -> {
                        proxySelector(ProxySelector.getDefault())
                    }

                    is ProxyConfig.Http -> {
                        proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxyConfig.host, proxyConfig.port)))
                        val username = proxyConfig.username
                        val password = proxyConfig.password
                        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                            proxyAuthenticator { _, response ->
                                response.request
                                    .newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        Credentials.basic(username, password),
                                    ).build()
                            }
                        }
                    }

                    is ProxyConfig.Socks -> {
                        proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(proxyConfig.host, proxyConfig.port)))
                        val username = proxyConfig.username
                        val password = proxyConfig.password
                        val proxyHost = proxyConfig.host
                        val proxyPort = proxyConfig.port
                        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                            Authenticator.setDefault(
                                object : Authenticator() {
                                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                                        val hostMatches =
                                            requestingHost.equals(proxyHost, ignoreCase = true)
                                        val portMatches = requestingPort == proxyPort
                                        return if (hostMatches && portMatches) {
                                            PasswordAuthentication(username, password.toCharArray())
                                        } else {
                                            null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
