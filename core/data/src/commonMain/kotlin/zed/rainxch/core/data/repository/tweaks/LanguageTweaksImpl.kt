package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.settings.AppLanguages
import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.core.domain.repository.tweaks.LanguageTweaks

internal class LanguageTweaksImpl(
    private val store: TweakStore,
) : LanguageTweaks {
    override fun getTranslationProvider(): Flow<TranslationProvider> =
        store.gatedGetFlow(K_TRANSLATION_PROVIDER, "").map { TranslationProvider.fromName(it.ifEmpty { null }) }

    override suspend fun setTranslationProvider(provider: TranslationProvider) {
        store.awaitMigration()
        store.ksafe.safePut(K_TRANSLATION_PROVIDER, provider.name)
    }

    override fun getAppLanguage(): Flow<String?> =
        store.gatedGetFlow<String?>(K_APP_LANGUAGE, null).map { raw ->
            raw?.trim()?.takeIf { it.isNotEmpty() && AppLanguages.containsTag(it) }
        }

    override suspend fun setAppLanguage(tag: String?) {
        store.awaitMigration()
        val normalized = tag?.trim().orEmpty()
        if (normalized.isEmpty() || !AppLanguages.containsTag(normalized)) {
            store.ksafe.safeDelete(K_APP_LANGUAGE)
        } else {
            store.ksafe.safePut(K_APP_LANGUAGE, normalized)
        }
    }

    override fun getAutoTranslateEnabled(): Flow<Boolean> = store.gatedGetFlow(K_AUTO_TRANSLATE_ENABLED, false)

    override suspend fun setAutoTranslateEnabled(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.put(K_AUTO_TRANSLATE_ENABLED, enabled)
    }

    override fun getAutoTranslateTargetLang(): Flow<String?> =
        store.gatedGetFlow<String?>(K_AUTO_TRANSLATE_TARGET, null).map { raw ->
            raw?.trim()?.takeIf { it.isNotEmpty() }
        }

    override suspend fun setAutoTranslateTargetLang(tag: String?) {
        store.awaitMigration()
        val normalized = tag?.trim().orEmpty()
        if (normalized.isEmpty()) {
            store.ksafe.delete(K_AUTO_TRANSLATE_TARGET)
        } else {
            store.ksafe.put(K_AUTO_TRANSLATE_TARGET, normalized)
        }
    }
}
