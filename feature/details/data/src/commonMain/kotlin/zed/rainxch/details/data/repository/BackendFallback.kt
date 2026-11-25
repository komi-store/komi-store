package zed.rainxch.details.data.repository

import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.model.error.LocalStoreException

internal class BackendFallback(
    private val tokenStore: TokenStore,
) {
    suspend fun failsOverToGithub(cause: Throwable): Boolean = shouldFallbackToGithubOrRethrow(cause, isSignedIn())

    private suspend fun isSignedIn(): Boolean =
        try {
            tokenStore
                .currentToken()
                ?.accessToken
                ?.trim()
                ?.isNotEmpty() == true
        } catch (_: LocalStoreException) {
            false
        }
}
