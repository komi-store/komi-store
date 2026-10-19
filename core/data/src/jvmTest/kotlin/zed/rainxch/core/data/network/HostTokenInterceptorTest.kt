package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import zed.rainxch.core.data.network.interceptor.HostTokenInterceptor
import zed.rainxch.core.domain.model.account.HostNames
import zed.rainxch.core.domain.model.account.HostToken
import zed.rainxch.core.domain.model.account.TokenValidation
import zed.rainxch.core.domain.repository.HostTokenRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostTokenInterceptorTest {
    private val store = FakeHostTokenRepository()
    private val sent = mutableListOf<String?>()
    private val client =
        HttpClient(
            MockEngine { request ->
                sent += request.headers[HttpHeaders.Authorization]
                respondOk()
            },
        ) {
            install(HostTokenInterceptor) { repository = store }
        }

    private suspend fun authorizationFor(
        url: String,
        existing: String? = null,
    ): String? {
        client.get(url) { if (existing != null) header(HttpHeaders.Authorization, existing) }
        return sent.last()
    }

    @Test
    fun a_forge_token_goes_only_to_its_own_host() =
        runBlocking {
            store.set("codeberg.org", "cb-token")

            assertEquals("token cb-token", authorizationFor("https://codeberg.org/api/v1/repos/a/b"))
            assertNull(authorizationFor("https://gitea.com/api/v1/repos/a/b"))
            assertNull(authorizationFor("https://codeberg.org.example.com/api/v1/repos/a/b"))
            assertNull(authorizationFor("https://notcodeberg.org/api/v1/repos/a/b"))
            assertNull(authorizationFor("https://pages.codeberg.org/a/b"))
        }

    @Test
    fun the_github_token_goes_to_github_api_hosts_but_not_the_backend() =
        runBlocking {
            store.set("github.com", "gh-token")

            assertEquals("token gh-token", authorizationFor("https://api.github.com/repos/a/b"))
            assertEquals("token gh-token", authorizationFor("https://uploads.github.com/repos/a/b"))
            assertEquals("token gh-token", authorizationFor("https://raw.githubusercontent.com/a/b/main/README.md"))
            assertNull(authorizationFor("https://api.github-store.org/v1/repo/a/b"))
            assertNull(authorizationFor("https://github-store.org/auth/register"))
            assertNull(authorizationFor("https://objects.githubusercontent.com/a"))
        }

    @Test
    fun a_host_with_a_port_matches_only_that_port() =
        runBlocking {
            store.set("git.example.com:3000", "port-token")

            assertEquals("token port-token", authorizationFor("https://git.example.com:3000/api/v1/user"))
            assertNull(authorizationFor("https://git.example.com/api/v1/user"))
        }

    @Test
    fun plain_http_never_carries_a_token() =
        runBlocking {
            store.set("codeberg.org", "cb-token")

            assertNull(authorizationFor("http://codeberg.org/api/v1/repos/a/b"))
        }

    @Test
    fun an_authorization_already_on_the_request_is_kept() =
        runBlocking {
            store.set("github.com", "gh-token")

            assertEquals("Bearer account", authorizationFor("https://api.github.com/user", existing = "Bearer account"))
        }

    @Test
    fun adding_or_removing_a_token_applies_to_the_next_request() =
        runBlocking {
            assertNull(authorizationFor("https://codeberg.org/api/v1/user"))

            store.set("codeberg.org", "first")
            assertEquals("token first", authorizationFor("https://codeberg.org/api/v1/user"))

            store.set("codeberg.org", "second")
            assertEquals("token second", authorizationFor("https://codeberg.org/api/v1/user"))

            store.delete("codeberg.org")
            assertNull(authorizationFor("https://codeberg.org/api/v1/user"))
        }

    private class FakeHostTokenRepository : HostTokenRepository {
        private val tokens = MutableStateFlow<List<HostToken>>(emptyList())

        override fun observeAll(): Flow<List<HostToken>> = tokens

        override suspend fun get(host: String): HostToken? {
            val normalized = HostNames.normalize(host)
            return tokens.value.firstOrNull { it.host == normalized }
        }

        override suspend fun set(
            host: String,
            token: String,
            displayName: String?,
        ) {
            val key = HostNames.normalize(host)
            tokens.value = tokens.value.filterNot { it.host == key } +
                HostToken(host = key, token = token, displayName = displayName, createdAtEpochMillis = 0L)
        }

        override suspend fun delete(host: String) {
            val key = HostNames.normalize(host)
            tokens.value = tokens.value.filterNot { it.host == key }
        }

        override suspend fun validate(
            host: String,
            token: String,
        ): Result<TokenValidation> = Result.failure(UnsupportedOperationException())
    }
}
