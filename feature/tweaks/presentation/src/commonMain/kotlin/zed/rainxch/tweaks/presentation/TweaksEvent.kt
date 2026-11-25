package zed.rainxch.tweaks.presentation

sealed interface TweaksEvent {
    sealed interface Proxy : TweaksEvent

    sealed interface Storage : TweaksEvent

    sealed interface Translation : TweaksEvent

    data object OnProxySaved : Proxy

    data class OnProxySaveError(
        val message: String,
    ) : Proxy

    data class OnProxyTestSuccess(
        val latencyMs: Long,
    ) : Proxy

    data class OnProxyTestError(
        val message: String,
    ) : Proxy

    data class OnMasterProxyTestResult(
        val searchMs: Long?,
        val downloadMs: Long?,
        val translationMs: Long?,
    ) : Proxy

    data object OnCacheCleared : Storage

    data class OnCacheClearError(
        val message: String,
    ) : Storage

    data object OnSeenHistoryCleared : Storage

    data object OnTranslationProviderSaved : Translation

    data object OnYoudaoCredentialsSaved : Translation

    data object OnLibreTranslateCredentialsSaved : Translation

    data object OnDeeplCredentialsSaved : Translation

    data object OnMicrosoftTranslatorCredentialsSaved : Translation

    data object OnAppLanguageChangeRequiresRestart : TweaksEvent

    data object OnDiagnosticsShareFailed : TweaksEvent
}
