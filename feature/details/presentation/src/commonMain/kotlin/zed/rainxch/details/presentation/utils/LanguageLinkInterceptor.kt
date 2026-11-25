package zed.rainxch.details.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import zed.rainxch.details.presentation.model.SupportedLanguages

@Composable
fun ProvideLanguageLinkInterceptor(
    onTranslate: (String) -> Unit,
    onClearTranslation: (() -> Unit)? = null,
    onOpenInternalMarkdown: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val defaultUriHandler = LocalUriHandler.current
    val customUriHandler =
        remember(defaultUriHandler, onTranslate, onClearTranslation, onOpenInternalMarkdown) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val clearTranslation = onClearTranslation?.takeIf { README_LINK.containsMatchIn(uri) }
                    val languageCode = translatedReadmeLanguage(uri)
                    val openInternal = onOpenInternalMarkdown?.takeIf { INTERNAL_MARKDOWN_LINK.containsMatchIn(uri) }
                    when {
                        clearTranslation != null -> clearTranslation()
                        languageCode != null -> onTranslate(languageCode)
                        openInternal != null -> openInternal(uri)
                        else -> defaultUriHandler.openUri(uri)
                    }
                }
            }
        }
    CompositionLocalProvider(
        LocalUriHandler provides customUriHandler,
        content = content,
    )
}

// A README-xx.md link names a translation; the code maps to a supported language, by exact code first.
private fun translatedReadmeLanguage(uri: String): String? {
    val code = TRANSLATED_README_LINK.find(uri)?.groupValues?.get(1) ?: return null
    val normalizedCode = LANGUAGE_ALIASES[code.lowercase()] ?: code
    val language =
        SupportedLanguages.all.find { it.code.equals(normalizedCode, ignoreCase = true) }
            ?: SupportedLanguages.all.find {
                it.code.startsWith(normalizedCode, ignoreCase = true) || normalizedCode.startsWith(it.code, ignoreCase = true)
            }
    return language?.code
}

private val README_LINK = Regex("""(?i)README\.md(?:[#?].*)?$""")
private val TRANSLATED_README_LINK = Regex("""(?i)README[-._]([a-z]{2}(?:-[a-z]{2})?)\.md(?:[#?].*)?$""")
private val INTERNAL_MARKDOWN_LINK =
    Regex("""(?i)^https://(?:raw\.githubusercontent\.com|github\.com)/[^/]+/[^/]+/(?:blob/)?[^/]+/.+\.md(?:[#?].*)?$""")

private val LANGUAGE_ALIASES =
    mapOf(
        "cn" to "zh-CN",
        "tw" to "zh-TW",
        "jp" to "ja",
        "kr" to "ko",
        "br" to "pt-BR",
    )
