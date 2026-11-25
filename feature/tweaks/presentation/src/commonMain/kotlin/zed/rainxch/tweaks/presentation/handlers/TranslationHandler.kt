package zed.rainxch.tweaks.presentation.handlers

import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksEvent
import zed.rainxch.tweaks.presentation.TweaksState

class TranslationHandler(
    private val tweaksRepository: TweaksRepository,
) {
    internal fun observe(store: TweaksStore) {
        store.observe(tweaksRepository.getTranslationProvider()) { copy(translationProvider = it) }
        store.observe(tweaksRepository.getYoudaoAppKey()) { copy(youdaoAppKey = it) }
        store.observe(tweaksRepository.getYoudaoAppSecret()) { copy(youdaoAppSecret = it) }
        store.observe(tweaksRepository.getLibreTranslateBaseUrl()) { copy(libreTranslateBaseUrl = it) }
        store.observe(tweaksRepository.getLibreTranslateApiKey()) { copy(libreTranslateApiKey = it) }
        store.observe(tweaksRepository.getDeeplAuthKey()) { copy(deeplAuthKey = it) }
        store.observe(tweaksRepository.getMicrosoftTranslatorKey()) { copy(microsoftTranslatorKey = it) }
        store.observe(tweaksRepository.getMicrosoftTranslatorRegion()) { copy(microsoftTranslatorRegion = it) }
        store.observe(tweaksRepository.getAutoTranslateEnabled()) { copy(autoTranslateEnabled = it) }
        store.observe(tweaksRepository.getAutoTranslateTargetLang()) { copy(autoTranslateTargetLang = it) }
    }

    internal fun onAction(
        store: TweaksStore,
        action: TweaksAction.Translation,
    ) {
        when (action) {
            is TweaksAction.YoudaoCredentials -> {
                onYoudaoAction(store, action)
            }

            is TweaksAction.LibreTranslateCredentials -> {
                onLibreTranslateAction(store, action)
            }

            is TweaksAction.DeeplCredentials -> {
                onDeeplAction(store, action)
            }

            is TweaksAction.MicrosoftCredentials -> {
                onMicrosoftAction(store, action)
            }

            TweaksAction.OnTranslationProviderExpandToggle -> {
                store.update { it.copy(translationProviderExpanded = !it.translationProviderExpanded) }
            }

            TweaksAction.OnTranslationTargetPickerOpen -> {
                store.update { it.copy(translationTargetPickerOpen = true, languageQuery = "") }
            }

            TweaksAction.OnTranslationTargetPickerDismiss -> {
                store.update { it.copy(translationTargetPickerOpen = false) }
            }

            is TweaksAction.OnTranslationProviderSelected -> {
                selectProvider(store, action.provider)
            }

            is TweaksAction.OnAutoTranslateEnabledToggle -> {
                store.launch { tweaksRepository.setAutoTranslateEnabled(action.enabled) }
            }

            is TweaksAction.OnAutoTranslateTargetSelected -> {
                selectAutoTranslateTarget(store, action.tag)
            }
        }
    }

    private fun onYoudaoAction(
        store: TweaksStore,
        action: TweaksAction.YoudaoCredentials,
    ) {
        when (action) {
            is TweaksAction.OnYoudaoAppKeyChanged -> {
                store.update { it.copy(youdaoAppKey = action.appKey) }
            }

            is TweaksAction.OnYoudaoAppSecretChanged -> {
                store.update { it.copy(youdaoAppSecret = action.appSecret) }
            }

            TweaksAction.OnYoudaoAppSecretVisibilityToggle -> {
                store.update { it.copy(isYoudaoAppSecretVisible = !it.isYoudaoAppSecretVisible) }
            }

            TweaksAction.OnYoudaoCredentialsSave -> {
                saveCredentials(store, TranslationProvider.YOUDAO, TweaksEvent.OnYoudaoCredentialsSaved) { saved ->
                    tweaksRepository.setYoudaoAppKey(saved.youdaoAppKey)
                    tweaksRepository.setYoudaoAppSecret(saved.youdaoAppSecret)
                }
            }
        }
    }

    private fun onLibreTranslateAction(
        store: TweaksStore,
        action: TweaksAction.LibreTranslateCredentials,
    ) {
        when (action) {
            is TweaksAction.OnLibreTranslateBaseUrlChanged -> {
                store.update { it.copy(libreTranslateBaseUrl = action.url) }
            }

            is TweaksAction.OnLibreTranslateApiKeyChanged -> {
                store.update { it.copy(libreTranslateApiKey = action.apiKey) }
            }

            TweaksAction.OnLibreTranslateApiKeyVisibilityToggle -> {
                store.update { it.copy(isLibreTranslateApiKeyVisible = !it.isLibreTranslateApiKeyVisible) }
            }

            TweaksAction.OnLibreTranslateCredentialsSave -> {
                saveCredentials(store, TranslationProvider.LIBRE_TRANSLATE, TweaksEvent.OnLibreTranslateCredentialsSaved) { saved ->
                    tweaksRepository.setLibreTranslateBaseUrl(saved.libreTranslateBaseUrl)
                    tweaksRepository.setLibreTranslateApiKey(saved.libreTranslateApiKey)
                }
            }
        }
    }

    private fun onDeeplAction(
        store: TweaksStore,
        action: TweaksAction.DeeplCredentials,
    ) {
        when (action) {
            is TweaksAction.OnDeeplAuthKeyChanged -> {
                store.update { it.copy(deeplAuthKey = action.authKey) }
            }

            TweaksAction.OnDeeplAuthKeyVisibilityToggle -> {
                store.update { it.copy(isDeeplAuthKeyVisible = !it.isDeeplAuthKeyVisible) }
            }

            TweaksAction.OnDeeplCredentialsSave -> {
                saveCredentials(store, TranslationProvider.DEEPL, TweaksEvent.OnDeeplCredentialsSaved) { saved ->
                    tweaksRepository.setDeeplAuthKey(saved.deeplAuthKey)
                }
            }
        }
    }

    private fun onMicrosoftAction(
        store: TweaksStore,
        action: TweaksAction.MicrosoftCredentials,
    ) {
        when (action) {
            is TweaksAction.OnMicrosoftTranslatorKeyChanged -> {
                store.update { it.copy(microsoftTranslatorKey = action.key) }
            }

            is TweaksAction.OnMicrosoftTranslatorRegionChanged -> {
                store.update { it.copy(microsoftTranslatorRegion = action.region) }
            }

            TweaksAction.OnMicrosoftTranslatorKeyVisibilityToggle -> {
                store.update { it.copy(isMicrosoftTranslatorKeyVisible = !it.isMicrosoftTranslatorKeyVisible) }
            }

            TweaksAction.OnMicrosoftTranslatorCredentialsSave -> {
                saveCredentials(store, TranslationProvider.MICROSOFT, TweaksEvent.OnMicrosoftTranslatorCredentialsSaved) { saved ->
                    tweaksRepository.setMicrosoftTranslatorKey(saved.microsoftTranslatorKey)
                    tweaksRepository.setMicrosoftTranslatorRegion(saved.microsoftTranslatorRegion)
                }
            }
        }
    }

    // A provider whose required credential is blank stays a draft until that credential is saved.
    private fun selectProvider(
        store: TweaksStore,
        provider: TranslationProvider,
    ) {
        if (provider == TranslationProvider.LIBRE_TRANSLATE || store.current.hasCredentialsFor(provider)) {
            store.update { it.copy(draftTranslationProvider = null) }
            store.launch {
                tweaksRepository.setTranslationProvider(provider)
                store.send(TweaksEvent.OnTranslationProviderSaved)
            }
        } else {
            store.update { it.copy(draftTranslationProvider = provider) }
        }
    }

    private fun saveCredentials(
        store: TweaksStore,
        provider: TranslationProvider,
        savedEvent: TweaksEvent,
        persist: suspend (TweaksState) -> Unit,
    ) {
        val current = store.current
        store.launch {
            persist(current)
            val activates =
                current.hasCredentialsFor(provider) &&
                    (current.translationProvider != provider || current.draftTranslationProvider == provider)
            if (activates) tweaksRepository.setTranslationProvider(provider)
            store.update { it.copy(draftTranslationProvider = null) }
            store.send(savedEvent)
        }
    }

    private fun selectAutoTranslateTarget(
        store: TweaksStore,
        tag: String?,
    ) {
        store.update { it.copy(translationTargetPickerOpen = false) }
        store.launch { tweaksRepository.setAutoTranslateTargetLang(tag) }
    }

    private fun TweaksState.hasCredentialsFor(provider: TranslationProvider): Boolean =
        when (provider) {
            TranslationProvider.GOOGLE -> true
            TranslationProvider.YOUDAO -> youdaoAppKey.isNotBlank() && youdaoAppSecret.isNotBlank()
            TranslationProvider.LIBRE_TRANSLATE -> libreTranslateBaseUrl.isNotBlank()
            TranslationProvider.DEEPL -> deeplAuthKey.isNotBlank()
            TranslationProvider.MICROSOFT -> microsoftTranslatorKey.isNotBlank()
        }
}
