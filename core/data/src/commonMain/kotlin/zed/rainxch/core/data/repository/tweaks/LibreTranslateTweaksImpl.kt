package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.tweaks.LibreTranslateTweaks

internal class LibreTranslateTweaksImpl(
    private val store: TweakStore,
) : LibreTranslateTweaks {
    override fun getLibreTranslateBaseUrl(): Flow<String> = store.gatedGetFlow(K_LIBRE_BASE_URL, "")

    override suspend fun setLibreTranslateBaseUrl(url: String) {
        store.awaitMigration()
        val trimmed = url.trim()
        if (trimmed.isEmpty()) store.ksafe.safeDelete(K_LIBRE_BASE_URL) else store.ksafe.safePut(K_LIBRE_BASE_URL, trimmed)
    }

    override fun getLibreTranslateApiKey(): Flow<String> = store.gatedGetFlow(K_LIBRE_API_KEY, "")

    override suspend fun setLibreTranslateApiKey(apiKey: String) {
        store.awaitMigration()
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) store.ksafe.safeDelete(K_LIBRE_API_KEY) else store.ksafe.safePut(K_LIBRE_API_KEY, trimmed)
    }
}
