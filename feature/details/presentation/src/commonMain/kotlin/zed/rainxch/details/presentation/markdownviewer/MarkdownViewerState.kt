package zed.rainxch.details.presentation.markdownviewer

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.details.presentation.model.SupportedLanguage
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState

data class MarkdownViewerState(
    val url: String = "",
    val filename: String = "",
    val isLoading: Boolean = false,
    val markdown: String = "",
    val markdownChunks: ImmutableList<String> = persistentListOf(),
    val isReadyToRender: Boolean = false,
    val errorMessage: String? = null,
    val translation: TranslationState = TranslationState(),
    val isLanguagePickerVisible: Boolean = false,
    val languagePickerQuery: String = "",
    val filteredLanguages: ImmutableList<SupportedLanguage> = SupportedLanguages.all.toImmutableList(),
    val deviceLanguageCode: String = "en",
)
