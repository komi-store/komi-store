package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow

interface ExternalImportTweaks {
    fun getExternalImportEnabled(): Flow<Boolean>

    suspend fun setExternalImportEnabled(enabled: Boolean)

    fun getExternalMatchSearchEnabled(): Flow<Boolean>

    suspend fun setExternalMatchSearchEnabled(enabled: Boolean)

    fun getExternalImportBannerDismissedAtCount(): Flow<Int>

    suspend fun setExternalImportBannerDismissedAtCount(count: Int)
}
