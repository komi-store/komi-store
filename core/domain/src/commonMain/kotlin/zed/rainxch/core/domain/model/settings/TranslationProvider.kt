package zed.rainxch.core.domain.model.settings

enum class TranslationProvider {
    GOOGLE,
    YOUDAO,
    LIBRE_TRANSLATE,
    DEEPL,
    MICROSOFT,
    ;

    companion object {
        val Default: TranslationProvider = GOOGLE

        fun fromName(name: String?): TranslationProvider = entries.firstOrNull { it.name == name } ?: Default
    }
}
