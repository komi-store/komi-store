package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow

interface LibreTranslateTweaks {
    fun getLibreTranslateBaseUrl(): Flow<String>

    suspend fun setLibreTranslateBaseUrl(url: String)

    fun getLibreTranslateApiKey(): Flow<String>

    suspend fun setLibreTranslateApiKey(apiKey: String)
}
