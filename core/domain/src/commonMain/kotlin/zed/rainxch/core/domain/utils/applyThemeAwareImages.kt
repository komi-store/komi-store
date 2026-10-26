package zed.rainxch.core.domain.utils

fun applyThemeAwareImages(
    content: String,
    isDark: Boolean,
): String {
    if (content.isEmpty()) return content
    var processed = content

    processed =
        processed.replace(
            Regex("""!\[([^\]]*)\]\(([^)]*?)#gh-(dark|light)-mode-only([^)]*)\)"""),
        ) { match ->
            val (alt, urlBase, mode, trailing) = match.destructured
            if (isHiddenInTheme(mode, isDark)) {
                ""
            } else {
                "![$alt]($urlBase$trailing)"
            }
        }

    processed =
        processed.replace(
            Regex(
                """<img(\s[^>]*?)src\s*=\s*(["'])([^"']*?)#gh-(dark|light)-mode-only([^"']*?)\2([^>]*?)>""",
                RegexOption.IGNORE_CASE,
            ),
        ) { match ->
            val (pre, quote, urlBase, mode, trailing, post) = match.destructured
            if (isHiddenInTheme(mode, isDark)) {
                ""
            } else {
                "<img${pre}src=$quote$urlBase$trailing$quote$post>"
            }
        }

    return processed
}

private fun isHiddenInTheme(
    mode: String,
    isDark: Boolean,
): Boolean = mode == if (isDark) "light" else "dark"
