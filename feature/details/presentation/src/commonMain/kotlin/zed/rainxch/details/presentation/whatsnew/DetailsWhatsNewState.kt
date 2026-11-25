package zed.rainxch.details.presentation.whatsnew

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.details.presentation.model.SupportedLanguage
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.details.presentation.model.WhatsNewReleaseUi

data class DetailsWhatsNewState(
    val isLoading: Boolean = true,
    val repoName: String = "",
    val releases: ImmutableList<WhatsNewReleaseUi> = persistentListOf(),
    val errorMessage: String? = null,
    val deviceLanguageCode: String = "en",
    val translation: TranslationState = TranslationState(),
    val isLanguagePickerVisible: Boolean = false,
    val languagePickerQuery: String = "",
    val filteredLanguages: ImmutableList<SupportedLanguage> = SupportedLanguages.all.toImmutableList(),
)
