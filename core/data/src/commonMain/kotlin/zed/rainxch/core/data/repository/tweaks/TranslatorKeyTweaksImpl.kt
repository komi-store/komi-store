package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.tweaks.TranslatorKeyTweaks

internal class TranslatorKeyTweaksImpl(
    private val store: TweakStore,
) : TranslatorKeyTweaks {
    override fun getYoudaoAppKey(): Flow<String> = store.gatedGetFlow(K_YOUDAO_APP_KEY, "")

    override suspend fun setYoudaoAppKey(appKey: String) {
        store.awaitMigration()
        val trimmed = appKey.trim()
        if (trimmed.isEmpty()) store.ksafe.safeDelete(K_YOUDAO_APP_KEY) else store.ksafe.safePut(K_YOUDAO_APP_KEY, trimmed)
    }

    override fun getYoudaoAppSecret(): Flow<String> = store.gatedGetFlow(K_YOUDAO_APP_SECRET, "")

    override suspend fun setYoudaoAppSecret(appSecret: String) {
        store.awaitMigration()
        val trimmed = appSecret.trim()
        if (trimmed.isEmpty()) store.ksafe.safeDelete(K_YOUDAO_APP_SECRET) else store.ksafe.safePut(K_YOUDAO_APP_SECRET, trimmed)
    }

    override fun getDeeplAuthKey(): Flow<String> = store.gatedGetFlow(K_DEEPL_AUTH_KEY, "")

    override suspend fun setDeeplAuthKey(authKey: String) {
        store.awaitMigration()
        val trimmed = authKey.trim()
        if (trimmed.isEmpty()) store.ksafe.safeDelete(K_DEEPL_AUTH_KEY) else store.ksafe.safePut(K_DEEPL_AUTH_KEY, trimmed)
    }

    override fun getMicrosoftTranslatorKey(): Flow<String> = store.gatedGetFlow(K_MS_KEY, "")

    override suspend fun setMicrosoftTranslatorKey(key: String) {
        store.awaitMigration()
        val trimmed = key.trim()
        if (trimmed.isEmpty()) store.ksafe.safeDelete(K_MS_KEY) else store.ksafe.safePut(K_MS_KEY, trimmed)
    }

    override fun getMicrosoftTranslatorRegion(): Flow<String> = store.gatedGetFlow(K_MS_REGION, "")

    override suspend fun setMicrosoftTranslatorRegion(region: String) {
        store.awaitMigration()
        val trimmed = region.trim()
        if (trimmed.isEmpty()) store.ksafe.safeDelete(K_MS_REGION) else store.ksafe.safePut(K_MS_REGION, trimmed)
    }
}
