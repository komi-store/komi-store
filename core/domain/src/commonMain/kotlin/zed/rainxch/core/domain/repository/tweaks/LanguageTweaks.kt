package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.settings.TranslationProvider

interface LanguageTweaks {
    fun getTranslationProvider(): Flow<TranslationProvider>

    suspend fun setTranslationProvider(provider: TranslationProvider)

    fun getAppLanguage(): Flow<String?>

    suspend fun setAppLanguage(tag: String?)

    fun getAutoTranslateEnabled(): Flow<Boolean>

    suspend fun setAutoTranslateEnabled(enabled: Boolean)

    fun getAutoTranslateTargetLang(): Flow<String?>

    suspend fun setAutoTranslateTargetLang(tag: String?)
}
