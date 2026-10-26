package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow

interface TranslatorKeyTweaks {
    fun getYoudaoAppKey(): Flow<String>

    suspend fun setYoudaoAppKey(appKey: String)

    fun getYoudaoAppSecret(): Flow<String>

    suspend fun setYoudaoAppSecret(appSecret: String)

    fun getDeeplAuthKey(): Flow<String>

    suspend fun setDeeplAuthKey(authKey: String)

    fun getMicrosoftTranslatorKey(): Flow<String>

    suspend fun setMicrosoftTranslatorKey(key: String)

    fun getMicrosoftTranslatorRegion(): Flow<String>

    suspend fun setMicrosoftTranslatorRegion(region: String)
}
