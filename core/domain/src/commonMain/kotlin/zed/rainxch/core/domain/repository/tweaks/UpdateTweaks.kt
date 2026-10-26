package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow

interface UpdateTweaks {
    fun getAutoUpdateEnabled(): Flow<Boolean>

    suspend fun setAutoUpdateEnabled(enabled: Boolean)

    fun getUpdateCheckEnabled(): Flow<Boolean>

    suspend fun setUpdateCheckEnabled(enabled: Boolean)

    fun getUpdateCheckInterval(): Flow<Long>

    suspend fun setUpdateCheckInterval(hours: Long)

    fun getIncludePreReleases(): Flow<Boolean>

    suspend fun setIncludePreReleases(enabled: Boolean)
}
