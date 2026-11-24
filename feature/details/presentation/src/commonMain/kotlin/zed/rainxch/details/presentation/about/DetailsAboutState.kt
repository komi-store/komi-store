package zed.rainxch.details.presentation.about

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.details.presentation.model.SupportedLanguage
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState

data class DetailsAboutState(
    val isLoading: Boolean = true,
    val repoName: String = "",
    val readmeMarkdown: String = "",
    val displayedMarkdown: String = "",
    val readmeLanguage: String? = null,
    val errorMessage: String? = null,
    val deviceLanguageCode: String = "en",
    val translation: TranslationState = TranslationState(),
    val isLanguagePickerVisible: Boolean = false,
    val languagePickerQuery: String = "",
    val filteredLanguages: ImmutableList<SupportedLanguage> = SupportedLanguages.all.toImmutableList(),
)
