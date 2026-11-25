package zed.rainxch.tweaks.presentation.handlers

import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.network.ProxyTestOutcome
import zed.rainxch.core.domain.network.ProxyTester
import zed.rainxch.core.domain.repository.ProxyRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_auth_required
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_dns
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_status
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_timeout
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_unknown
import zed.rainxch.githubstore.core.presentation.res.proxy_test_error_unreachable
import zed.rainxch.githubstore.core.presentation.res.tweaks_custom_forge_invalid_hostname
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksEvent
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.connection.parseProxyUrl
import zed.rainxch.tweaks.presentation.model.ProxyScopeFormState
import zed.rainxch.tweaks.presentation.model.ProxyType

class ConnectionHandler(
    private val proxyRepository: ProxyRepository,
    private val proxyTester: ProxyTester,
    private val tweaksRepository: TweaksRepository,
) {
    internal fun observe(store: TweaksStore) {
        ProxyScope.entries.forEach { scope ->
            store.observe(proxyRepository.getProxyConfig(scope)) { config ->
                val form = formFor(scope)
                if (form.isDraftDirty) this else withScopeForm(scope, form.withConfig(config))
            }
            store.observe(proxyRepository.getUseMaster(scope)) { useMaster ->
                copy(useMasterByScope = (useMasterByScope + (scope to useMaster)).toImmutableMap())
            }
        }
        store.observe(proxyRepository.getMasterProxyConfig()) { config ->
            if (masterProxyForm.isDraftDirty) this else copy(masterProxyForm = masterProxyForm.withConfig(config ?: ProxyConfig.None))
        }
        store.observe(tweaksRepository.getCustomForgeHosts()) { copy(customForgeHosts = it.toImmutableSet()) }
    }

    internal fun onAction(
        store: TweaksStore,
        action: TweaksAction.Connection,
    ) {
        when (action) {
            is TweaksAction.ScopeProxy -> onScopeProxyAction(store, action)
            is TweaksAction.MainProxy -> onMainProxyAction(store, action)
            is TweaksAction.CustomForge -> onCustomForgeAction(store, action)
        }
    }

    private fun onScopeProxyAction(
        store: TweaksStore,
        action: TweaksAction.ScopeProxy,
    ) {
        when (action) {
            is TweaksAction.OnProxyTypeSelected -> selectScopeType(store, action.scope, action.type)
            is TweaksAction.OnProxyHostChanged -> editScopeDraft(store, action.scope) { it.copy(host = action.host) }
            is TweaksAction.OnProxyPortChanged -> editScopeDraft(store, action.scope) { it.copy(port = action.port) }
            is TweaksAction.OnProxyUsernameChanged -> editScopeDraft(store, action.scope) { it.copy(username = action.username) }
            is TweaksAction.OnProxyPasswordChanged -> editScopeDraft(store, action.scope) { it.copy(password = action.password) }
            is TweaksAction.OnProxyPasswordVisibilityToggle -> editScopeForm(store, action.scope) { it.withPasswordToggled() }
            is TweaksAction.OnProxySave -> saveScope(store, action.scope)
            is TweaksAction.OnProxyTest -> testScope(store, action.scope)
            is TweaksAction.OnProxyScopeExpandToggle -> toggleScopeExpanded(store, action.scope)
            is TweaksAction.OnScopeUseMainToggled -> toggleUseMain(store, action.scope, action.useMain)
        }
    }

    private fun onMainProxyAction(
        store: TweaksStore,
        action: TweaksAction.MainProxy,
    ) {
        when (action) {
            is TweaksAction.OnMasterProxyTypeSelected -> selectMainType(store, action.type)
            is TweaksAction.OnMasterProxyHostChanged -> editMainDraft(store) { it.copy(host = action.host) }
            is TweaksAction.OnMasterProxyPortChanged -> editMainDraft(store) { it.copy(port = action.port) }
            is TweaksAction.OnMasterProxyUsernameChanged -> editMainDraft(store) { it.copy(username = action.username) }
            is TweaksAction.OnMasterProxyPasswordChanged -> editMainDraft(store) { it.copy(password = action.password) }
            TweaksAction.OnMasterProxyPasswordVisibilityToggle -> editMainDraft(store) { it.withPasswordToggled() }
            TweaksAction.OnMasterProxySave -> saveMain(store)
            TweaksAction.OnMasterProxyTest -> testMain(store)
            TweaksAction.OnConnectionMasterExpandToggle -> store.update { it.copy(connectionMasterExpanded = !it.connectionMasterExpanded) }
            TweaksAction.OnConnectionPasteSheetOpen -> openPasteSheet(store)
            TweaksAction.OnConnectionPasteSheetDismiss -> store.update { it.copy(connectionPasteSheetOpen = false) }
            is TweaksAction.OnProxyPasteUrlChanged -> store.update { it.withPasteInput(action.value) }
            TweaksAction.OnProxyPasteUrlSubmit -> applyPastedUrl(store)
        }
    }

    private fun onCustomForgeAction(
        store: TweaksStore,
        action: TweaksAction.CustomForge,
    ) {
        when (action) {
            TweaksAction.OnOpenCustomForgesDialog -> {
                store.update { it.copy(showCustomForgesDialog = true, customForgeDraft = "", customForgeError = null) }
            }

            TweaksAction.OnDismissCustomForgesDialog -> {
                store.update { it.copy(showCustomForgesDialog = false) }
            }

            is TweaksAction.OnCustomForgeDraftChanged -> {
                store.update { it.copy(customForgeDraft = action.draft, customForgeError = null) }
            }

            TweaksAction.OnAddCustomForge -> {
                addCustomForge(store)
            }

            is TweaksAction.OnRemoveCustomForge -> {
                store.launch { tweaksRepository.removeCustomForgeHost(action.host) }
            }
        }
    }

    private fun selectScopeType(
        store: TweaksStore,
        scope: ProxyScope,
        type: ProxyType,
    ) {
        editScopeDraft(store, scope) { it.copy(type = type) }
        val config = type.withoutEndpoint() ?: return
        store.launch { persistScope(store, scope, config) }
    }

    private fun saveScope(
        store: TweaksStore,
        scope: ProxyScope,
    ) {
        when (val draft = store.current.formFor(scope).toDraft()) {
            is ProxyDraft.Rejected -> store.launch { store.send(TweaksEvent.OnProxySaveError(getString(draft.reason))) }
            is ProxyDraft.Ready -> store.launch { persistScope(store, scope, draft.config) }
        }
    }

    private suspend fun persistScope(
        store: TweaksStore,
        scope: ProxyScope,
        config: ProxyConfig,
    ) {
        proxyRepository.setProxyConfig(scope, config)
        store.update { state ->
            val form = state.formFor(scope)
            if (form.isDraftDirty) state.withScopeForm(scope, form.copy(isDraftDirty = false)) else state
        }
        store.send(TweaksEvent.OnProxySaved)
    }

    private fun testScope(
        store: TweaksStore,
        scope: ProxyScope,
    ) {
        val form = store.current.formFor(scope)
        if (form.isTestInProgress) return
        when (val draft = form.toDraft()) {
            is ProxyDraft.Rejected -> {
                store.launch { store.send(TweaksEvent.OnProxyTestError(getString(draft.reason))) }
            }

            is ProxyDraft.Ready -> {
                editScopeForm(store, scope) { it.copy(isTestInProgress = true) }
                store.launch {
                    val outcome =
                        try {
                            testOutcome(draft.config)
                        } finally {
                            editScopeForm(store, scope) { it.copy(isTestInProgress = false) }
                        }
                    store.send(outcome.toEvent())
                }
            }
        }
    }

    private fun toggleUseMain(
        store: TweaksStore,
        scope: ProxyScope,
        useMain: Boolean,
    ) {
        store.update { it.copy(useMasterByScope = (it.useMasterByScope + (scope to useMain)).toImmutableMap()) }
        store.launch {
            proxyRepository.setUseMaster(scope, useMain)
            if (useMain) {
                proxyRepository.getMasterProxyConfig().first()?.let { proxyRepository.setProxyConfig(scope, it) }
            }
        }
    }

    private fun selectMainType(
        store: TweaksStore,
        type: ProxyType,
    ) {
        editMainDraft(store) { it.copy(type = type) }
        val config = type.withoutEndpoint() ?: return
        store.launch { persistMain(store, config) }
    }

    private fun saveMain(store: TweaksStore) {
        when (val draft = store.current.masterProxyForm.toDraft()) {
            is ProxyDraft.Rejected -> store.launch { store.send(TweaksEvent.OnProxySaveError(getString(draft.reason))) }
            is ProxyDraft.Ready -> store.launch { persistMain(store, draft.config) }
        }
    }

    // Only the per-scope configs reach the network, so the main one is copied into every scope that follows it.
    private suspend fun persistMain(
        store: TweaksStore,
        config: ProxyConfig,
    ) {
        proxyRepository.setMasterProxyConfig(config)
        ProxyScope.entries.forEach { scope ->
            if (store.current.useMain(scope)) proxyRepository.setProxyConfig(scope, config)
        }
        store.update { it.copy(masterProxyForm = it.masterProxyForm.copy(isDraftDirty = false)) }
        store.send(TweaksEvent.OnProxySaved)
    }

    private fun testMain(store: TweaksStore) {
        if (store.current.masterProxyForm.isTestInProgress) return
        val config = store.current.masterProxyForm.toUncheckedConfig() ?: return
        setMainTestInProgress(store, true)
        store.launch {
            val result =
                try {
                    probeAll(config)
                } finally {
                    setMainTestInProgress(store, false)
                }
            store.send(result)
        }
    }

    private suspend fun probeAll(config: ProxyConfig): TweaksEvent.OnMasterProxyTestResult =
        coroutineScope {
            val search = async { probeLatency(config, SEARCH_PROBE_URL) }
            val download = async { probeLatency(config, DOWNLOAD_PROBE_URL) }
            val translation = async { probeLatency(config, TRANSLATION_PROBE_URL) }
            TweaksEvent.OnMasterProxyTestResult(
                searchMs = search.await(),
                downloadMs = download.await(),
                translationMs = translation.await(),
            )
        }

    // Building the test client rejects a port outside the valid range before any request.
    private suspend fun probeLatency(
        config: ProxyConfig,
        url: String,
    ): Long? =
        try {
            (proxyTester.test(config, url) as? ProxyTestOutcome.Success)?.latencyMs
        } catch (_: IllegalArgumentException) {
            null
        }

    private suspend fun testOutcome(config: ProxyConfig): ProxyTestOutcome =
        try {
            proxyTester.test(config)
        } catch (e: IllegalArgumentException) {
            ProxyTestOutcome.Failure.Unknown(e.message)
        }

    private fun toggleScopeExpanded(
        store: TweaksStore,
        scope: ProxyScope,
    ) {
        store.update { it.copy(expandedProxyScope = if (it.expandedProxyScope == scope) null else scope) }
    }

    private fun openPasteSheet(store: TweaksStore) {
        store.update { it.withPasteInput("").copy(connectionPasteSheetOpen = true) }
    }

    private fun applyPastedUrl(store: TweaksStore) {
        val parsed = parseProxyUrl(store.current.proxyPasteUrlInput)
        if (parsed == null) {
            store.update { it.copy(proxyPasteUrlError = true) }
        } else {
            store.update { it.withPasteInput("").copy(connectionPasteSheetOpen = false) }
            editMainDraft(store) {
                it.copy(
                    type = parsed.type,
                    host = parsed.host,
                    port = parsed.port.toString(),
                    username = parsed.username.orEmpty(),
                    password = parsed.password.orEmpty(),
                )
            }
        }
    }

    private fun addCustomForge(store: TweaksStore) {
        val host =
            store.current.customForgeDraft
                .trim()
                .lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
        store.launch {
            if (host.isEmpty() || '.' !in host || ' ' in host) {
                val error = getString(Res.string.tweaks_custom_forge_invalid_hostname)
                store.update { it.copy(customForgeError = error) }
            } else {
                tweaksRepository.addCustomForgeHost(host)
                store.update { it.copy(customForgeDraft = "", customForgeError = null) }
            }
        }
    }

    private fun editScopeDraft(
        store: TweaksStore,
        scope: ProxyScope,
        edit: (ProxyScopeFormState) -> ProxyScopeFormState,
    ) {
        editScopeForm(store, scope) { edit(it).copy(isDraftDirty = true) }
    }

    private fun editScopeForm(
        store: TweaksStore,
        scope: ProxyScope,
        edit: (ProxyScopeFormState) -> ProxyScopeFormState,
    ) {
        store.update { it.withScopeForm(scope, edit(it.formFor(scope))) }
    }

    private fun editMainDraft(
        store: TweaksStore,
        edit: (ProxyScopeFormState) -> ProxyScopeFormState,
    ) {
        store.update { it.copy(masterProxyForm = edit(it.masterProxyForm).copy(isDraftDirty = true)) }
    }

    private fun setMainTestInProgress(
        store: TweaksStore,
        inProgress: Boolean,
    ) {
        store.update { it.copy(masterProxyForm = it.masterProxyForm.copy(isTestInProgress = inProgress)) }
    }

    private fun TweaksState.withScopeForm(
        scope: ProxyScope,
        form: ProxyScopeFormState,
    ): TweaksState = copy(proxyForms = (proxyForms + (scope to form)).toImmutableMap())

    private fun TweaksState.withPasteInput(input: String): TweaksState = copy(proxyPasteUrlInput = input, proxyPasteUrlError = false)

    private fun ProxyScopeFormState.withPasswordToggled(): ProxyScopeFormState = copy(isPasswordVisible = !isPasswordVisible)

    private fun ProxyType.withoutEndpoint(): ProxyConfig? =
        when (this) {
            ProxyType.NONE -> ProxyConfig.None
            ProxyType.SYSTEM -> ProxyConfig.System
            ProxyType.HTTP, ProxyType.SOCKS -> null
        }

    private suspend fun ProxyTestOutcome.toEvent(): TweaksEvent =
        when (this) {
            is ProxyTestOutcome.Success -> TweaksEvent.OnProxyTestSuccess(latencyMs = latencyMs)
            is ProxyTestOutcome.Failure -> TweaksEvent.OnProxyTestError(failureMessage())
        }

    private suspend fun ProxyTestOutcome.Failure.failureMessage(): String =
        when (this) {
            ProxyTestOutcome.Failure.DnsFailure -> getString(Res.string.proxy_test_error_dns)
            ProxyTestOutcome.Failure.ProxyUnreachable -> getString(Res.string.proxy_test_error_unreachable)
            ProxyTestOutcome.Failure.Timeout -> getString(Res.string.proxy_test_error_timeout)
            ProxyTestOutcome.Failure.ProxyAuthRequired -> getString(Res.string.proxy_test_error_auth_required)
            is ProxyTestOutcome.Failure.UnexpectedResponse -> getString(Res.string.proxy_test_error_status, statusCode)
            is ProxyTestOutcome.Failure.Unknown -> getString(Res.string.proxy_test_error_unknown)
        }

    private companion object {
        const val SEARCH_PROBE_URL = "https://api.github.com/zen"
        const val DOWNLOAD_PROBE_URL = "https://github.com/robots.txt"
        const val TRANSLATION_PROBE_URL = "https://translate.disroot.org"
    }
}
