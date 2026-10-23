package zed.rainxch.details.presentation.markdownviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.compose.resources.getString
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.translation_failed

private const val RENDER_ENTER_DELAY_MS = 350L

class MarkdownViewerViewModel(
    private val url: String,
    private val detailsRepository: DetailsRepository,
    private val translationRepository: TranslationRepository,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            MarkdownViewerState(
                url = url,
                filename = url.substringAfterLast("/", "Document").substringBefore("?"),
                deviceLanguageCode = translationRepository.getDeviceLanguageCode(),
            ),
        )
    val state = _state.asStateFlow()

    private var renderJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun translate(targetLanguageCode: String) {
        val markdown = _state.value.markdown
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
                recompose()
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
        recompose()
    }

    fun clearTranslation() {
        _state.update {
            it.copy(translation = TranslationState())
        }
        recompose()
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
                detailsRepository.fetchRawMarkdown(url)
            }.onSuccess { rawMarkdown ->
                if (rawMarkdown != null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            markdown = rawMarkdown,
                        )
                    }
                    recompose()
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = getString(Res.string.failed_to_load)) }
                }
            }.onFailure { _ ->
                _state.update { it.copy(isLoading = false, errorMessage = getString(Res.string.failed_to_load)) }
            }
        }
    }

    private fun recompose() {
        renderJob?.cancel()
        renderJob =
            viewModelScope.launch {
                val translation = _state.value.translation
                val displayed =
                    if (translation.isShowingTranslation && translation.translatedText != null) {
                        translation.translatedText
                    } else {
                        _state.value.markdown
                    }
                val chunks =
                    if (displayed.isEmpty()) {
                        emptyList()
                    } else {
                        withContext(Dispatchers.Default) { chunkMarkdown(displayed) }
                    }
                _state.update {
                    it.copy(markdownChunks = chunks.toImmutableList(), isReadyToRender = false)
                }
                if (displayed.isNotEmpty()) {
                    delay(RENDER_ENTER_DELAY_MS)
                    _state.update { it.copy(isReadyToRender = true) }
                }
            }
    }

    private fun chunkMarkdown(markdown: String): List<String> {
        val flavour = GFMFlavourDescriptor()
        // Typed as CharSequence: the String overload is deprecated.
        val text: CharSequence = markdown
        val parsedTree =
            MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable)
                .buildMarkdownTreeFromString(text)
        return parsedTree.children
            .map { node -> markdown.substring(node.startOffset, node.endOffset) }
            .filter { it.isNotBlank() }
    }
}
