package zed.rainxch.tweaks.presentation.handlers

import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.AppVersionInfo
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksEvent

class LookAndFeelHandler(
    private val tweaksRepository: TweaksRepository,
    private val appVersionInfo: AppVersionInfo,
) {
    internal fun observe(store: TweaksStore) {
        store.update { it.copy(versionName = appVersionInfo.versionName) }
        store.observe(tweaksRepository.getThemeColor()) { copy(selectedThemeColor = it) }
        store.observe(tweaksRepository.getPersonality()) { copy(selectedPersonality = it) }
        store.observe(tweaksRepository.getAccentId()) { copy(selectedAccent = it) }
        store.observe(tweaksRepository.getMangaPaper()) { copy(mangaPaper = it) }
        store.observe(tweaksRepository.getAmoledTheme()) { copy(isAmoledThemeEnabled = it) }
        store.observe(tweaksRepository.getIsDarkTheme()) { copy(isDarkTheme = it) }
        store.observe(tweaksRepository.getFontTheme()) { copy(selectedFontTheme = it) }
        store.observe(tweaksRepository.getRepoGridLayout()) { copy(isRepoGridLayout = it) }
        store.observe(tweaksRepository.getScrollbarEnabled()) { copy(isScrollbarEnabled = it) }
        store.observe(tweaksRepository.getContentWidth()) { copy(contentWidth = it) }
        store.observe(tweaksRepository.getDisplayModeId()) { copy(displayModeId = it) }
        store.observe(tweaksRepository.getAppLanguage()) { copy(selectedAppLanguage = it) }
    }

    internal fun onAction(
        store: TweaksStore,
        action: TweaksAction.LookAndFeel,
    ) {
        when (action) {
            is TweaksAction.Appearance -> store.launch { persist(action) }
            is TweaksAction.Language -> onLanguageAction(store, action)
        }
    }

    private suspend fun persist(action: TweaksAction.Appearance) {
        when (action) {
            is TweaksAction.OnPersonalitySelected -> tweaksRepository.setPersonality(action.personality)
            is TweaksAction.OnAccentSelected -> tweaksRepository.setAccentId(action.accent)
            is TweaksAction.OnMangaPaperSelected -> tweaksRepository.setMangaPaper(action.paper)
            is TweaksAction.OnThemeColorSelected -> tweaksRepository.setThemeColor(action.themeColor)
            is TweaksAction.OnAmoledThemeToggled -> tweaksRepository.setAmoledTheme(action.enabled)
            is TweaksAction.OnRepoLayoutSelected -> tweaksRepository.setRepoGridLayout(action.grid)
            is TweaksAction.OnDarkThemeChange -> tweaksRepository.setDarkTheme(action.isDarkTheme)
            is TweaksAction.OnFontThemeSelected -> tweaksRepository.setFontTheme(action.fontTheme)
            is TweaksAction.OnScrollbarToggled -> tweaksRepository.setScrollbarEnabled(action.enabled)
            is TweaksAction.OnContentWidthSelected -> tweaksRepository.setContentWidth(action.width)
            is TweaksAction.OnDisplayModeSelected -> tweaksRepository.setDisplayModeId(action.modeId)
        }
    }

    private fun onLanguageAction(
        store: TweaksStore,
        action: TweaksAction.Language,
    ) {
        when (action) {
            TweaksAction.OnLanguagePickerOpen -> store.update { it.copy(isLanguageSheetVisible = true, languageQuery = "") }
            TweaksAction.OnLanguagePickerDismiss -> store.update { it.copy(isLanguageSheetVisible = false) }
            is TweaksAction.OnLanguageQueryChange -> store.update { it.copy(languageQuery = action.query) }
            is TweaksAction.OnAppLanguageSelected -> selectAppLanguage(store, action.tag)
        }
    }

    private fun selectAppLanguage(
        store: TweaksStore,
        tag: String?,
    ) {
        store.update { it.copy(isLanguageSheetVisible = false) }
        if (tag == store.current.selectedAppLanguage) return
        store.launch {
            tweaksRepository.setAppLanguage(tag)
            if (isDesktop()) store.send(TweaksEvent.OnAppLanguageChangeRequiresRestart)
        }
    }
}
