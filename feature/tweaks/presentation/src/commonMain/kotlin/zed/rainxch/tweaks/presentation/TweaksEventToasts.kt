package zed.rainxch.tweaks.presentation

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.downloads_cleared
import zed.rainxch.githubstore.core.presentation.res.language_restart_action
import zed.rainxch.githubstore.core.presentation.res.language_restart_required
import zed.rainxch.githubstore.core.presentation.res.proxy_saved
import zed.rainxch.githubstore.core.presentation.res.proxy_test_master_result
import zed.rainxch.githubstore.core.presentation.res.proxy_test_probe_failed
import zed.rainxch.githubstore.core.presentation.res.proxy_test_probe_ok
import zed.rainxch.githubstore.core.presentation.res.proxy_test_success
import zed.rainxch.githubstore.core.presentation.res.seen_history_cleared
import zed.rainxch.githubstore.core.presentation.res.translation_deepl_saved
import zed.rainxch.githubstore.core.presentation.res.translation_libre_saved
import zed.rainxch.githubstore.core.presentation.res.translation_microsoft_saved
import zed.rainxch.githubstore.core.presentation.res.translation_provider_saved
import zed.rainxch.githubstore.core.presentation.res.translation_youdao_saved
import zed.rainxch.githubstore.core.presentation.res.tweaks_diagnostics_share_error

internal suspend fun KomiToastState.showTweaksEvent(event: TweaksEvent) {
    when (event) {
        is TweaksEvent.Proxy -> {
            showProxyEvent(event)
        }

        is TweaksEvent.Storage -> {
            showStorageEvent(event)
        }

        is TweaksEvent.Translation -> {
            success(getString(event.savedMessage()))
        }

        TweaksEvent.OnAppLanguageChangeRequiresRestart -> {
            warning(
                message = getString(Res.string.language_restart_required),
                actionLabel = getString(Res.string.language_restart_action),
                onAction = { restartAppAfterLanguageChange() },
            )
        }

        TweaksEvent.OnDiagnosticsShareFailed -> {
            danger(getString(Res.string.tweaks_diagnostics_share_error))
        }
    }
}

private suspend fun KomiToastState.showProxyEvent(event: TweaksEvent.Proxy) {
    when (event) {
        TweaksEvent.OnProxySaved -> success(getString(Res.string.proxy_saved))
        is TweaksEvent.OnProxySaveError -> danger(event.message)
        is TweaksEvent.OnProxyTestSuccess -> success(getString(Res.string.proxy_test_success, event.latencyMs))
        is TweaksEvent.OnProxyTestError -> danger(event.message)
        is TweaksEvent.OnMasterProxyTestResult -> showMasterProxyTestResult(event)
    }
}

private suspend fun KomiToastState.showMasterProxyTestResult(event: TweaksEvent.OnMasterProxyTestResult) {
    val latencies = listOf(event.searchMs, event.downloadMs, event.translationMs)
    val parts =
        latencies.map { ms ->
            if (ms != null) getString(Res.string.proxy_test_probe_ok, ms) else getString(Res.string.proxy_test_probe_failed)
        }
    val message = getString(Res.string.proxy_test_master_result, parts[0], parts[1], parts[2])
    when {
        latencies.all { it != null } -> success(message)
        latencies.all { it == null } -> danger(message)
        else -> warning(message)
    }
}

private suspend fun KomiToastState.showStorageEvent(event: TweaksEvent.Storage) {
    when (event) {
        TweaksEvent.OnCacheCleared -> info(getString(Res.string.downloads_cleared))
        is TweaksEvent.OnCacheClearError -> danger(event.message)
        TweaksEvent.OnSeenHistoryCleared -> info(getString(Res.string.seen_history_cleared))
    }
}

private fun TweaksEvent.Translation.savedMessage(): StringResource =
    when (this) {
        TweaksEvent.OnTranslationProviderSaved -> Res.string.translation_provider_saved
        TweaksEvent.OnYoudaoCredentialsSaved -> Res.string.translation_youdao_saved
        TweaksEvent.OnLibreTranslateCredentialsSaved -> Res.string.translation_libre_saved
        TweaksEvent.OnDeeplCredentialsSaved -> Res.string.translation_deepl_saved
        TweaksEvent.OnMicrosoftTranslatorCredentialsSaved -> Res.string.translation_microsoft_saved
    }
