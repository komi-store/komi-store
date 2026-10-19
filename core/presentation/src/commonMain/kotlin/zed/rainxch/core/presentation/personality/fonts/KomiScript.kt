package zed.rainxch.core.presentation.personality.fonts

enum class KomiScript {
    Latin,
    Cyrillic,
    Arabic,
    Devanagari,
    Bengali,
    HanSimplified,
    Japanese,
    Korean,
}

fun komiScriptForLanguageTag(tag: String?): KomiScript {
    val language =
        tag
            ?.trim()
            ?.replace('_', '-')
            ?.substringBefore('-')
            ?.lowercase()
    return when (language) {
        "ru" -> KomiScript.Cyrillic
        "ar" -> KomiScript.Arabic
        "hi" -> KomiScript.Devanagari
        "bn" -> KomiScript.Bengali
        "zh" -> KomiScript.HanSimplified
        "ja" -> KomiScript.Japanese
        "ko" -> KomiScript.Korean
        else -> KomiScript.Latin
    }
}
