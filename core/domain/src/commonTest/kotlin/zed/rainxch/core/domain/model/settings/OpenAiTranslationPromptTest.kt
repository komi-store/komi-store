package zed.rainxch.core.domain.model.settings

import kotlin.test.Test
import kotlin.test.assertEquals

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

        assertEquals("Translate into Chinese.\n\nhello", rendered)
    }

    @Test
    fun renders_the_default_prompt_when_the_template_is_blank() {
        val rendered =
            OpenAiTranslationPrompt.render(
                template = "",
                targetLanguage = "Chinese",
                sourceLanguage = null,
                text = "hello",
            )

        assertEquals(
            OpenAiTranslationPrompt.DEFAULT.replace("{target}", "Chinese") + "\n\nhello",
            rendered,
        )
    }
}
