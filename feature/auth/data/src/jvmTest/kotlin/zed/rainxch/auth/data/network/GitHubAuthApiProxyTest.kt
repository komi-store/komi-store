package zed.rainxch.auth.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import zed.rainxch.core.domain.model.settings.ProxyConfig
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GitHubAuthApiProxyTest {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        appScope.cancel()
    }

    @Test
    fun device_flow_requests_go_through_the_configured_proxy() =
        runBlocking {
            FakeProxy().use { proxy ->
                val api = GitHubAuthApi(MutableStateFlow(proxy.config), appScope)

                val start = api.startDeviceFlowViaBackend(BACKEND)

                assertEquals("ABCD-1234", start.userCode)
                assertEquals(listOf("POST $BACKEND/v1/auth/device/start HTTP/1.1"), proxy.requestLines)
            }
        }

    @Test
    fun pat_validation_goes_through_the_configured_proxy() =
        runBlocking {
            FakeProxy().use { proxy ->
                val api = GitHubAuthApi(MutableStateFlow(proxy.config), appScope)

                val result = api.validatePersonalAccessToken("ghp_not_a_real_token")

                assertIs<PatValidation.Unreachable>(result)
                assertTrue(proxy.requestLines.isNotEmpty())
                assertTrue(proxy.requestLines.all { it == "CONNECT api.github.com:443 HTTP/1.1" }, "${proxy.requestLines}")
            }
        }

    @Test
    fun a_proxy_change_moves_sign_in_requests_to_the_new_proxy() =
        runBlocking {
            FakeProxy().use { first ->
                FakeProxy().use { second ->
                    val proxyConfig = MutableStateFlow<ProxyConfig>(first.config)
                    val api = GitHubAuthApi(proxyConfig, appScope)
                    api.startDeviceFlowViaBackend(BACKEND)

                    val before = api.http
                    proxyConfig.value = second.config
                    withTimeout(5_000) {
                        while (api.http === before) delay(10)
                    }
                    api.startDeviceFlowViaBackend(BACKEND)

                    assertEquals(1, first.requestLines.size)
                    assertEquals(1, second.requestLines.size)
                }
            }
        }

    private class FakeProxy : AutoCloseable {
        private val server = ServerSocket(0, 0, InetAddress.getLoopbackAddress())

        val config = ProxyConfig.Http(host = "127.0.0.1", port = server.localPort)
        val requestLines: MutableList<String> = Collections.synchronizedList(mutableListOf())

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket =
                        try {
                            server.accept()
                        } catch (_: IOException) {
                            break
                        }
                    socket.use { answer(it) }
                }
            }
        }

        // Answers plain-HTTP requests with a device-flow start and refuses
        // every CONNECT, so HTTPS targets never leave the machine.
        private fun answer(socket: Socket) {
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            requestLines += requestLine
            var contentLength = 0
            while (true) {
                val header = input.readLine()
                if (header.isNullOrEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(':').trim().toInt()
                }
            }
            repeat(contentLength) { input.read() }

            val response =
                if (requestLine.startsWith("CONNECT ")) {
                    "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                } else {
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${DEVICE_START.length}\r\nConnection: close\r\n\r\n$DEVICE_START"
                }
            socket.getOutputStream().apply {
                write(response.toByteArray())
                flush()
            }
        }

        override fun close() = server.close()
    }

    private companion object {
        const val BACKEND = "http://backend.test"

        const val DEVICE_START =
            """{"device_code":"dc","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900}"""
    }
}
