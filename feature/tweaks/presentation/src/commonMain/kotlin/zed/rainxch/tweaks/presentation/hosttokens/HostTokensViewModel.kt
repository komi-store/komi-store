package zed.rainxch.tweaks.presentation.hosttokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.ForgeKind
import zed.rainxch.core.domain.model.account.HostNames
import zed.rainxch.core.domain.model.error.TokenValidationException
import zed.rainxch.core.domain.repository.HostTokenRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.host_tokens_delete_failed
import zed.rainxch.githubstore.core.presentation.res.host_tokens_load_failed
import zed.rainxch.githubstore.core.presentation.res.host_tokens_save_failed
import zed.rainxch.githubstore.core.presentation.res.host_tokens_saved
import zed.rainxch.githubstore.core.presentation.res.host_tokens_undo_failed
import zed.rainxch.githubstore.core.presentation.res.host_tokens_validation_failed_fallback
import zed.rainxch.githubstore.core.presentation.res.host_tokens_validation_host_invalid
import zed.rainxch.githubstore.core.presentation.res.host_tokens_validation_host_required
import zed.rainxch.githubstore.core.presentation.res.host_tokens_validation_http
import zed.rainxch.githubstore.core.presentation.res.host_tokens_validation_token_required
import zed.rainxch.githubstore.core.presentation.res.host_tokens_validation_token_short
import zed.rainxch.githubstore.core.presentation.res.host_tokens_validation_unreachable

@Suppress("TooGenericExceptionCaught")
class HostTokensViewModel(
    private val repository: HostTokenRepository,
    private val userSessionRepository: UserSessionRepository,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(HostTokensState(isLoading = true))
    val state = _state.asStateFlow()

    private val _events = Channel<HostTokensEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {

            repository
                .observeAll()
                .catch { t ->
                    logger.error("HostTokensViewModel: failed to load tokens", t)
                    _state.update { it.copy(isLoading = false) }
                    _events.send(HostTokensEvent.Message(getString(Res.string.host_tokens_load_failed)))
                }.collect { tokens ->
                    _state.update {
                        it.copy(
                            tokens = tokens.sortedBy { t -> t.host },
                            isLoading = false,
                        )
                    }
                }
        }
        viewModelScope.launch {
            userSessionRepository
                .isUserLoggedIn()
                .catch { }
                .collect { signedIn ->
                    _state.update { it.copy(isOAuthSignedInToGithub = signedIn) }
                }
        }
    }

    fun onAction(action: HostTokensAction) {
        when (action) {
            HostTokensAction.OnNavigateBack -> { }

            HostTokensAction.OnAddClicked -> {
                _state.update { it.copy(draftMode = DraftMode.Picker) }
            }

            HostTokensAction.OnAddDismiss -> {
                closeDraft()
            }

            is HostTokensAction.OnPickPresetForge -> {
                openCompose(forge = action.kind, host = action.kind.tokenHost)
            }

            HostTokensAction.OnPickOtherForge -> {
                openCompose(forge = null, host = "")
            }

            is HostTokensAction.OnOpenTokenCreationPage -> {
                viewModelScope.launch {
                    _events.send(HostTokensEvent.OpenUrl(action.kind.tokenCreationUrl))
                }
            }

            is HostTokensAction.OnReplaceToken -> {
                _state.update {
                    it.copy(
                        draftMode = DraftMode.Compose(replacingExisting = action.existing),
                        draftForge = ForgeKind.fromHost(action.existing.host),
                        draftHost = action.existing.host,
                        draftHostNormalized = action.existing.host,
                        draftToken = "",
                        draftDisplayName = action.existing.displayName.orEmpty(),
                        draftHostError = null,
                        draftTokenError = null,
                        draftDetectedTokenKind = null,
                    )
                }
            }

            is HostTokensAction.OnEditLabel -> {
                _state.update {
                    it.copy(
                        draftMode = DraftMode.Compose(replacingExisting = action.existing),
                        draftForge = ForgeKind.fromHost(action.existing.host),
                        draftHost = action.existing.host,
                        draftHostNormalized = action.existing.host,
                        draftToken = "",
                        draftDisplayName = action.existing.displayName.orEmpty(),
                        draftHostError = null,
                        draftTokenError = null,
                        draftDetectedTokenKind = null,
                    )
                }
            }

            is HostTokensAction.OnDraftHostChanged -> {
                val normalized = HostNames.normalize(action.value)
                _state.update {
                    it.copy(
                        draftHost = action.value,
                        draftHostNormalized = normalized,
                        draftHostError = validateHost(normalized),
                    )
                }
            }

            is HostTokensAction.OnDraftTokenChanged -> {
                val sanitized = HostNames.sanitizePastedToken(action.value)
                _state.update {
                    it.copy(
                        draftToken = sanitized,
                        draftTokenError = validateToken(sanitized),
                        draftDetectedTokenKind = HostNames.detectPatKind(sanitized),
                    )
                }
            }

            is HostTokensAction.OnDraftDisplayNameChanged -> {
                _state.update { it.copy(draftDisplayName = action.value) }
            }

            HostTokensAction.OnAddConfirm -> {
                persistDraft()
            }

            is HostTokensAction.OnDelete -> {
                deleteHost(action.host)
            }

            HostTokensAction.OnUndoDelete -> {
                undoDelete()
            }

            HostTokensAction.OnDismissUndoDelete -> {
                _state.update { it.copy(pendingUndoDelete = null) }
            }

            is HostTokensAction.OnValidate -> {
                validateExisting(action.host)
            }
        }
    }

    private fun openCompose(
        forge: ForgeKind?,
        host: String,
    ) {
        _state.update {
            it.copy(
                draftMode = DraftMode.Compose(replacingExisting = null),
                draftForge = forge,
                draftHost = host,
                draftHostNormalized = host,
                draftToken = "",
                draftDisplayName = "",
                draftHostError = null,
                draftTokenError = null,
                draftDetectedTokenKind = null,
            )
        }
    }

    private fun closeDraft() {
        _state.update {
            it.copy(
                draftMode = DraftMode.Closed,
                draftForge = null,
                draftHost = "",
                draftHostNormalized = "",
                draftToken = "",
                draftDisplayName = "",
                draftHostError = null,
                draftTokenError = null,
                draftDetectedTokenKind = null,
            )
        }
    }

    private fun validateHost(normalized: String): org.jetbrains.compose.resources.StringResource? =
        when {
            normalized.isEmpty() -> Res.string.host_tokens_validation_host_required
            !normalized.contains('.') -> Res.string.host_tokens_validation_host_invalid
            else -> null
        }

    private fun validateToken(value: String): org.jetbrains.compose.resources.StringResource? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> Res.string.host_tokens_validation_token_required
            trimmed.length < 8 -> Res.string.host_tokens_validation_token_short
            else -> null
        }
    }

    private fun persistDraft() {
        val current = state.value
        val mode = current.draftMode as? DraftMode.Compose ?: return
        val replacing = mode.replacingExisting

        val hostError = validateHost(current.draftHostNormalized)
        val token = current.draftToken.trim()
        val keepExistingToken = replacing != null && token.isEmpty()
        val tokenError = if (keepExistingToken) null else validateToken(token)
        if (hostError != null || tokenError != null) {
            _state.update { it.copy(draftHostError = hostError, draftTokenError = tokenError) }
            return
        }
        val host = current.draftHostNormalized
        val effectiveToken = if (keepExistingToken) replacing.token else token
        val displayName = current.draftDisplayName.trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            try {
                repository.set(host, effectiveToken, displayName)
                closeDraft()
                _events.send(HostTokensEvent.Message(getString(Res.string.host_tokens_saved, host)))
                validateExisting(host, fromAdd = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("HostTokensViewModel: failed to save token for $host", e)
                _events.send(HostTokensEvent.Message(getString(Res.string.host_tokens_save_failed, host)))
            }
        }
    }

    private fun deleteHost(host: String) {
        viewModelScope.launch {
            val snapshot = state.value.tokens.firstOrNull { it.host == host }
            try {
                repository.delete(host)
                _state.update {
                    it.copy(
                        pendingUndoDelete = snapshot,
                        validationByHost = it.validationByHost - host,
                        validatingHosts = it.validatingHosts - host,
                    )
                }
                if (snapshot != null) {
                    _events.send(HostTokensEvent.TokenDeletedWithUndo(snapshot))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("HostTokensViewModel: failed to delete token for $host", e)
                _events.send(HostTokensEvent.Message(getString(Res.string.host_tokens_delete_failed, host)))
            }
        }
    }

    private fun undoDelete() {
        val pending = state.value.pendingUndoDelete ?: return
        viewModelScope.launch {
            try {
                repository.set(pending.host, pending.token, pending.displayName)
                _state.update { it.copy(pendingUndoDelete = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("HostTokensViewModel: failed to restore token for ${pending.host}", e)
                _events.send(
                    HostTokensEvent.Message(
                        getString(Res.string.host_tokens_undo_failed, pending.host),
                    ),
                )
            }
        }
    }

    private fun validateExisting(
        host: String,
        fromAdd: Boolean = false,
    ) {
        if (host in state.value.validatingHosts) return
        viewModelScope.launch {
            val token =
                try {
                    repository.get(host)?.token
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("HostTokensViewModel: failed to read token for $host", e)
                    null
                } ?: return@launch
            _state.update { it.copy(validatingHosts = it.validatingHosts + host) }
            val result = repository.validate(host, token)
            val line =
                result.fold(
                    onSuccess = { v ->
                        ValidationLine(
                            login = v.login,
                            scopes = v.scopes,
                            rateLimitRemaining = v.rateLimitRemaining,
                            errorMessage = null,
                        )
                    },
                    onFailure = { t ->
                        ValidationLine(
                            login = null,
                            scopes = emptyList(),
                            rateLimitRemaining = null,
                            errorMessage = validationErrorMessage(host, t),
                        )
                    },
                )
            _state.update {
                it.copy(
                    validatingHosts = it.validatingHosts - host,
                    validationByHost = it.validationByHost + (host to line),
                )
            }
            if (fromAdd && line.isSuccess && !line.login.isNullOrBlank()) {
                val current = repository.get(host)
                if (current != null && current.displayName.isNullOrBlank()) {
                    try {
                        repository.set(host, current.token, line.login)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("HostTokensViewModel: failed to save the label for $host", e)
                    }
                }
            }
        }
    }

    private suspend fun validationErrorMessage(
        host: String,
        error: Throwable,
    ): String =
        when (error) {
            is TokenValidationException.HttpStatus -> {
                getString(Res.string.host_tokens_validation_http, error.status)
            }

            is TokenValidationException.Unreachable -> {
                logger.error("HostTokensViewModel: couldn't reach $host", error)
                getString(Res.string.host_tokens_validation_unreachable)
            }

            else -> {
                logger.error("HostTokensViewModel: validation failed for $host", error)
                getString(Res.string.host_tokens_validation_failed_fallback)
            }
        }
}
