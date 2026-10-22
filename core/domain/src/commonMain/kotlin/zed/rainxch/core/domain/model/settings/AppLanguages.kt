package zed.rainxch.core.domain.model.settings

object AppLanguages {
    val ALL: List<AppLanguage> =
        listOf(
            AppLanguage("en", "English"),
            AppLanguage("ar", "العربية"),
            AppLanguage("bn", "বাংলা"),
            AppLanguage("es", "Español"),
            AppLanguage("fr", "Français"),
            AppLanguage("hi", "हिन्दी"),
            AppLanguage("it", "Italiano"),
            AppLanguage("ja", "日本語"),
            AppLanguage("ko", "한국어"),
            AppLanguage("pl", "Polski"),
            AppLanguage("ru", "Русский"),
            AppLanguage("tr", "Türkçe"),
            AppLanguage("zh-CN", "简体中文"),
            AppLanguage("zh-TW", "繁體中文"),
        )

    fun findByTag(tag: String?): AppLanguage? = if (tag.isNullOrBlank()) null else ALL.find { it.tag == tag }

    fun containsTag(tag: String?): Boolean = findByTag(tag) != null
}
