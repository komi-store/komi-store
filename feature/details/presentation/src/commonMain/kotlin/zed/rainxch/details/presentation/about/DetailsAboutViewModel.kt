package zed.rainxch.details.presentation.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.translation_failed

class DetailsAboutViewModel(
    private val repositoryId: Long,
    private val owner: String,
    private val repo: String,
    private val sourceHost: String?,
    private val detailsRepository: DetailsRepository,
    private val translationRepository: TranslationRepository,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            DetailsAboutState(deviceLanguageCode = translationRepository.getDeviceLanguageCode()),
        )
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun translate(targetLanguageCode: String) {
        val markdown = _state.value.readmeMarkdown
        if (markdown.isBlank()) return
        val displayName = SupportedLanguages.all.firstOrNull { it.code == targetLanguageCode }?.displayName
        _state.update {
            it.copy(
                translation =
                    it.translation.copy(
                        isTranslating = true,
                        targetLanguageCode = targetLanguageCode,
                        targetLanguageDisplayName = displayName,
                        error = null,
                    ),
            )
        }
        viewModelScope.launch {
            runCatching {
                translationRepository.translate(markdown, targetLanguageCode)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        translation =
                            it.translation.copy(
                                isTranslating = false,
                                translatedText = result.translatedText,
                                isShowingTranslation = true,
                                detectedSourceLanguage = result.detectedSourceLanguage,
                                error = null,
                            ),
                    )
                }
                rebuildDisplayed()
            }.onFailure { _ ->
                _state.update {
                    it.copy(
                        translation =
                            it.translation.copy(
                                isTranslating = false,
                                error = getString(Res.string.translation_failed),
                            ),
                    )
                }
            }
        }
    }

    fun toggleTranslation() {
        _state.update {
            it.copy(
                translation =
                    it.translation.copy(
                        isShowingTranslation = !it.translation.isShowingTranslation,
                    ),
            )
        }
        rebuildDisplayed()
    }

    fun clearTranslation() {
        _state.update {
            it.copy(translation = TranslationState())
        }
        rebuildDisplayed()
    }

    fun showLanguagePicker() {
        _state.update {
            it.copy(
                isLanguagePickerVisible = true,
                languagePickerQuery = "",
                filteredLanguages = SupportedLanguages.all.toImmutableList(),
            )
        }
    }

    fun dismissLanguagePicker() {
        _state.update { it.copy(isLanguagePickerVisible = false) }
    }

    fun onLanguageQueryChange(query: String) {
        val filtered =
            if (query.isBlank()) {
                SupportedLanguages.all
            } else {
                SupportedLanguages.all.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                        it.code.contains(query, ignoreCase = true)
                }
            }
        _state.update { it.copy(languagePickerQuery = query, filteredLanguages = filtered.toImmutableList()) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val resolved =
                    if (owner.isNotBlank() && repo.isNotBlank()) {
                        detailsRepository.getRepositoryByOwnerAndName(owner, repo, sourceHost)
                    } else {
                        detailsRepository.getRepositoryById(repositoryId)
                    }
                val readme =
                    detailsRepository.getReadme(
                        owner = resolved.owner.login,
                        repo = resolved.name,
                        defaultBranch = resolved.defaultBranch,
                        sourceHost = sourceHost,
                    )
                resolved to readme
            }.onSuccess { (resolved, readme) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        repoName = resolved.name,
                        readmeMarkdown = readme?.first.orEmpty(),
                        readmeLanguage = readme?.second,
                    )
                }
                rebuildDisplayed()
            }.onFailure { _ ->
                _state.update { it.copy(isLoading = false, errorMessage = getString(Res.string.failed_to_load)) }
            }
        }
    }

    private fun rebuildDisplayed() {
        _state.update {
            val translation = it.translation
            val displayed =
                if (translation.isShowingTranslation && translation.translatedText != null) {
                    translation.translatedText
                } else {
                    it.readmeMarkdown
                }
            it.copy(displayedMarkdown = displayed)
        }
    }
}
