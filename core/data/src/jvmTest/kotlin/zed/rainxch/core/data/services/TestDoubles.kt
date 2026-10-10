package zed.rainxch.core.data.services

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto

internal class FakeFileLocations(private val dir: File) : FileLocationsProvider {
    override fun appDownloadsDir(): String = dir.absolutePath

    override fun userDownloadsDir(): String = dir.absolutePath

    override fun setExecutableIfNeeded(path: String) = Unit

    override fun getCacheSizeBytes(): Long = 0L

    override fun clearCacheFiles(): Boolean = false
}

internal class FakeTokenStore : TokenStore {
    override fun tokenFlow(): Flow<GithubDeviceTokenSuccessDto?> = emptyFlow()

    override suspend fun currentToken(): GithubDeviceTokenSuccessDto? = null

    override fun blockingCurrentToken(): GithubDeviceTokenSuccessDto? = null

    override suspend fun save(token: GithubDeviceTokenSuccessDto) = Unit

    override suspend fun clear() = Unit

    override suspend fun isTokenExpired(): Boolean = false
}
