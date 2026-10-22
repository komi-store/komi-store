package zed.rainxch.core.presentation.components.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun rememberMarkdownColors(): MarkdownColors {
    val colorScheme = MaterialTheme.colorScheme

    return DefaultMarkdownColors(
        text = colorScheme.onBackground,
        codeBackground = colorScheme.surfaceContainerHigh,
        inlineCodeBackground = colorScheme.surfaceContainerHigh,
        dividerColor = colorScheme.outlineVariant,
        tableBackground = colorScheme.surface,
        highlightText = colorScheme.onSecondaryContainer,
        highlightBackground = colorScheme.secondaryContainer,
    )
}

@Composable
fun rememberMarkdownTypography(): MarkdownTypography {
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val monoFamily = LocalPersonality.current.type.mono.fontFamily

    return DefaultMarkdownTypography(
        h1 = typography.headlineMedium,
        h2 = typography.titleLarge,
        h3 = typography.titleMedium,
        h4 = typography.titleSmall,
        h5 = typography.titleSmall,
        h6 = typography.labelMedium,
        text = typography.bodyLarge,
        code =
            typography.bodyMedium.copy(
                fontFamily = monoFamily,
                color = colorScheme.onSurfaceVariant,
            ),
        inlineCode =
            typography.bodyMedium.copy(
                fontFamily = monoFamily,
                color = colorScheme.onSurfaceVariant,
            ),
        quote =
            typography.bodyLarge.copy(
                color = colorScheme.onSurfaceVariant,
            ),
        paragraph = typography.bodyLarge,
        ordered = typography.bodyLarge,
        bullet = typography.bodyLarge,
        list = typography.bodyLarge,
        textLink =
            TextLinkStyles(
                style =
                    SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
            ),
        table = typography.bodyMedium,
    )
}
