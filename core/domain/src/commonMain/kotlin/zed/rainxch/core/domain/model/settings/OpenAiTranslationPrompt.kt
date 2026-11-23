package zed.rainxch.core.domain.model.settings

object OpenAiTranslationPrompt {
    const val DEFAULT: String =
        "Translate the following text into {target}. " +
            "Preserve Markdown, links, code blocks and terminology. " +
            "Output only the translation without explanations."

    fun render(
        template: String,
        targetLanguage: String,
        sourceLanguage: String?,
        text: String,
    ): String {
        val prompt = template.ifBlank { DEFAULT }
        val rendered =
            prompt
                .replace("{target}", targetLanguage)
                .replace("{source}", sourceLanguage ?: "the source language")
                .replace("{text}", text)
        return if (prompt.contains("{text}")) rendered else "$rendered\n\n$text"
    }
}
