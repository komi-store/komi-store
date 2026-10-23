package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.details.presentation.model.TranslationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RawDetailsStateMapperTest {
    @Test
    fun the_readme_section_renders_the_translation_while_it_is_shown() {
        val state =
            RawDetailsState(
                readmeMarkdown = "# Original",
                readmeLanguage = "en",
                aboutTranslation =
                    TranslationState(
                        translatedText = "# Übersetzt",
                        isShowingTranslation = true,
                        targetLanguageCode = "de",
                        targetLanguageDisplayName = "German",
                    ),
            ).toView()

        assertEquals("# Übersetzt", state.readmeMarkdown)
        assertEquals("German", state.readmeLanguage)
    }

    @Test
    fun the_readme_section_keeps_the_original_when_the_translation_is_hidden() {
        val state =
            RawDetailsState(
                readmeMarkdown = "# Original",
                readmeLanguage = "en",
                aboutTranslation =
                    TranslationState(
                        translatedText = "# Übersetzt",
                        isShowingTranslation = false,
                        targetLanguageCode = "de",
                        targetLanguageDisplayName = "German",
                    ),
            ).toView()

        assertEquals("# Original", state.readmeMarkdown)
        assertEquals("en", state.readmeLanguage)
    }

    @Test
    fun the_release_section_renders_the_translated_notes_while_they_are_shown() {
        val release = release(id = 1, description = "Fixes a crash")
        val state =
            RawDetailsState(
                selectedRelease = release,
                allReleases = listOf(release),
                whatsNewTranslation =
                    TranslationState(
                        translatedText = "Behebt einen Absturz",
                        isShowingTranslation = true,
                        targetLanguageCode = "de",
                        targetLanguageDisplayName = "German",
                    ),
            ).toView()

        assertEquals("Behebt einen Absturz", state.selectedRelease?.description)
        assertEquals("Fixes a crash", release.description)
    }

    @Test
    fun the_release_section_keeps_the_original_without_a_visible_translation() {
        val release = release(id = 1, description = "Fixes a crash")
        val state =
            RawDetailsState(
                selectedRelease = release,
                allReleases = listOf(release),
                whatsNewTranslation =
                    TranslationState(
                        translatedText = "Behebt einen Absturz",
                        isShowingTranslation = false,
                    ),
            ).toView()

        assertEquals("Fixes a crash", state.selectedRelease?.description)
    }

    @Test
    fun an_absent_translation_leaves_the_release_notes_empty() {
        val release = release(id = 1, description = null)
        val state =
            RawDetailsState(
                selectedRelease = release,
                allReleases = listOf(release),
            ).toView()

        assertNull(state.selectedRelease?.description)
    }

    private fun release(
        id: Long,
        description: String?,
    ): GithubRelease =
        GithubRelease(
            id = id,
            tagName = "v1.0.$id",
            name = null,
            publishedAt = "2026-01-01T00:00:00Z",
            description = description,
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
        )
}
