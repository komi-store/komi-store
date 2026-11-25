package zed.rainxch.githubstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.repository.TweaksRepository

// The tweaks the app shell applies itself: theme, language, scrollbar and content width.
class AppearancePreferences(
    private val tweaksRepository: TweaksRepository,
    private val localizationManager: LocalizationManager,
) {
    fun appearance(): Flow<Appearance> =
        combine(
            tweaksRepository.getPersonality(),
            tweaksRepository.getAccentId(),
            tweaksRepository.getMangaPaper(),
            tweaksRepository.getAmoledTheme(),
            tweaksRepository.getIsDarkTheme(),
        ) { personality, accent, paper, amoled, isDark ->
            Appearance(personality, accent, paper, amoled, isDark)
        }.combine(tweaksRepository.getAppLanguage()) { appearance, appLanguageTag ->
            appearance.copy(appLanguageTag = appLanguageTag)
        }.onEach { snapshot ->
            localizationManager.setActiveLanguageTag(snapshot.appLanguageTag)
        }

    fun scrollbarEnabled(): Flow<Boolean> = tweaksRepository.getScrollbarEnabled()

    fun contentWidth(): Flow<ContentWidth> = tweaksRepository.getContentWidth()
}
