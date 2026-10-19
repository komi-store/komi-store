package zed.rainxch.core.presentation.locals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

val LocalAppLanguageTag = staticCompositionLocalOf<String?> { null }

// Display text is uppercased in this locale: Kotlin's uppercase() ignores it, so Turkish
// "i" would lose its dot.
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalAppLanguageTag.current?.let(::Locale) ?: Locale.current
