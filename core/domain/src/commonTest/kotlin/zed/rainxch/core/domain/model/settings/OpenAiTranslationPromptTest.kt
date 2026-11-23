package zed.rainxch.core.domain.model.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiTranslationPromptTest {
    @Test
    fun replaces_target_source_and_text_placeholders() {
        val rendered =
            OpenAiTranslationPrompt.render(
                template = "Translate into {target} from {source}:\n{text}",
                targetLanguage = "Chinese",
                sourceLanguage = "English",
                text = "hello",
            )

        assertEquals("Translate into Chinese from English:\nhello", rendered)
    }

    @Test
    fun appends_the_text_when_the_template_omits_it() {
        val rendered =
            OpenAiTranslationPrompt.render(
                template = "Translate into {target}.",
                targetLanguage = "Chinese",
                sourceLanguage = null,
                text = "hello",
            )

        assertTrue(rendered.endsWith("hello"))
    }
}
