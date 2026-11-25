package zed.rainxch.auth.presentation.handlers

import kotlinx.coroutines.Job
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.presentation.AuthenticationAction
import zed.rainxch.auth.presentation.mapper.toPatError
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.auth.presentation.model.PatError
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger

// The personal access token sheet: open, type, submit, dismiss.
internal class PatHandler(
    private val store: AuthStore,
    private val repository: AuthenticationRepository,
    private val browserHelper: BrowserHelper,
    private val logger: KomiStoreLogger,
) {
    private var submissionJob: Job? = null

    fun onAction(action: AuthenticationAction.Pat) {
        when (action) {
            AuthenticationAction.OpenPatSheet -> {
                store.update {
                    it.copy(
                        isPatSheetVisible = true,
                        patInput = "",
                        patError = null,
                    )
                }
            }

            AuthenticationAction.DismissPatSheet -> {
                dismiss()
            }

            is AuthenticationAction.OnPatInputChanged -> {
                store.update { it.copy(patInput = action.input, patError = null) }
            }

            AuthenticationAction.SubmitPat -> {
                submit()
            }

            AuthenticationAction.OpenPatSettingsPage -> {
                browserHelper.openUrl(PAT_SETTINGS_URL) { error ->
                    logger.debug("Failed to open PAT settings page: $error")
                }
            }
        }
    }

    private fun dismiss() {
        submissionJob?.cancel()
        submissionJob = null
        store.update {
            it.copy(
                isPatSheetVisible = false,
                patInput = "",
                patError = null,
                isPatSubmitting = false,
            )
        }
    }

    private fun submit() {
        if (store.current.isPatSubmitting) return
        val input = store.current.patInput.trim()
        if (input.isEmpty()) {
            store.update { it.copy(patError = PatError.Empty) }
            return
        }

        submissionJob?.cancel()
        submissionJob =
            store
                .launch {
                    store.update { it.copy(isPatSubmitting = true, patError = null) }
                    repository
                        .signInWithPat(input)
                        .onSuccess {
                            store.update {
                                it.copy(
                                    isPatSheetVisible = false,
                                    patInput = "",
                                    patError = null,
                                    isPatSubmitting = false,
                                    loginState = AuthLoginState.LoggedIn,
                                )
                            }
                            store.navigateToMain()
                        }.onFailure { t ->
                            logger.debug("PAT sign-in failed: ${t.message}")
                            store.update {
                                it.copy(
                                    isPatSubmitting = false,
                                    patError = t.toPatError(),
                                )
                            }
                        }
                }.also { job ->
                    job.invokeOnCompletion { submissionJob = null }
                }
    }

    private companion object {
        const val PAT_SETTINGS_URL = "https://github.com/settings/tokens/new"
    }
}
