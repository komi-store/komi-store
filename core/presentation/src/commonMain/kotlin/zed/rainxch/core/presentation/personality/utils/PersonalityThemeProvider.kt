package zed.rainxch.core.presentation.personality.utils

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.em
import zed.rainxch.core.presentation.locals.LocalAppLanguageTag
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalStatusColors
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.Personality
import zed.rainxch.core.presentation.personality.classic.withClassicFonts
import zed.rainxch.core.presentation.personality.fonts.komiScriptForLanguageTag
import zed.rainxch.core.presentation.personality.manga.withMangaFonts
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.status.statusColors

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonalityTheme(
    personality: Personality,
    languageTag: String? = LocalAppLanguageTag.current,
    content: @Composable () -> Unit,
) {
    val resolvedTag = languageTag ?: Locale.current.toLanguageTag()
    val script = komiScriptForLanguageTag(resolvedTag)
    val resolved =
        when (personality) {
            is MangaPersonality -> personality.copy(type = personality.type.withMangaFonts(script))
            is ClassicPersonality -> personality.copy(type = personality.type.withClassicFonts(script))
        }
    val colorScheme =
        when (resolved) {
            is ClassicPersonality -> resolved.colorScheme
            is MangaPersonality -> resolved.colors.toMaterialColorScheme()
        }
    val typography = resolved.toMaterialTypography()
    CompositionLocalProvider(
        LocalPersonality provides resolved,
        LocalStatusColors provides statusColors(resolved.colors.isDark),
        LocalContentColor provides resolved.colors.onBackground,
    ) {
        when (resolved) {
            is ClassicPersonality -> {
                MaterialExpressiveTheme(colorScheme = colorScheme, typography = typography, content = content)
            }

            is MangaPersonality -> {
                MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Personality.toMaterialTypography(): Typography {
    val roles = type
    val display = materialHeading(roles.display)
    val headline = materialHeading(roles.screenTitle)
    val title = materialHeading(roles.sectionTitle)
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = display,
        headlineMedium = headline,
        headlineSmall = headline,
        titleLarge = title,
        titleMedium = roles.cardTitle,
        titleSmall = roles.label,
        bodyLarge = roles.reading,
        bodyMedium = roles.body,
        bodySmall = roles.caption,
        labelLarge = roles.label,
        labelMedium = roles.labelSmall,
        labelSmall = roles.overline,
        displayLargeEmphasized = emphasized(display),
        displayMediumEmphasized = emphasized(display),
        displaySmallEmphasized = emphasized(display),
        headlineLargeEmphasized = emphasized(display),
        headlineMediumEmphasized = emphasized(headline),
        headlineSmallEmphasized = emphasized(headline),
        titleLargeEmphasized = emphasized(title),
        titleMediumEmphasized = emphasized(roles.cardTitle),
        titleSmallEmphasized = emphasized(roles.label),
        bodyLargeEmphasized = emphasized(roles.reading),
        bodyMediumEmphasized = emphasized(roles.body),
        bodySmallEmphasized = emphasized(roles.caption),
        labelLargeEmphasized = emphasized(roles.label),
        labelMediumEmphasized = emphasized(roles.labelSmall),
        labelSmallEmphasized = emphasized(roles.overline),
    )
}

// Material never uppercases, and Anton is drawn for capitals, so Manga's Material
// slots (markdown headings, dialog titles) take the body face instead.
private fun Personality.materialHeading(style: TextStyle): TextStyle =
    when (this) {
        is ClassicPersonality -> style
        is MangaPersonality -> style.copy(fontFamily = type.body.fontFamily, fontWeight = FontWeight.Bold, letterSpacing = 0.em)
    }

private fun Personality.emphasized(style: TextStyle): TextStyle {
    val weights =
        when (this) {
            is ClassicPersonality -> ClassicWeights
            is MangaPersonality -> MangaWeights
        }
    val step = weights.indexOf(style.fontWeight)
    return if (step in 0 until weights.lastIndex) style.copy(fontWeight = weights[step + 1]) else style
}

private val ClassicWeights = listOf(FontWeight.Medium, FontWeight.Bold, FontWeight.ExtraBold)
private val MangaWeights = listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold)

private fun PersonalityColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHigh,
        outline = outline,
        outlineVariant = outlineVariant,
        error = error,
        onError = onError,
        scrim = scrim,
    )
}
