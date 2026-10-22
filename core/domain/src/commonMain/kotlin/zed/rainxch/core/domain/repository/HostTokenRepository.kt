package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.account.HostToken
import zed.rainxch.core.domain.model.account.TokenValidation

interface HostTokenRepository {
    fun observeAll(): Flow<List<HostToken>>

    suspend fun get(host: String): HostToken?

    suspend fun set(
        host: String,
        token: String,
        displayName: String? = null,
    )

    suspend fun delete(host: String)

    suspend fun validate(
        host: String,
        token: String,
    ): Result<TokenValidation>
}
