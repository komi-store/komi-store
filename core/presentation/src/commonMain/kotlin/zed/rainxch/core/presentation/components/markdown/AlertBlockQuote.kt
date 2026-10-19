package zed.rainxch.core.presentation.components.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownBlockQuote
import com.mikepenz.markdown.model.ImageTransformer
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*

enum class GithubAlertKind(
    val token: String,
) {
    NOTE("NOTE"),
    TIP("TIP"),
    IMPORTANT("IMPORTANT"),
    WARNING("WARNING"),
    CAUTION("CAUTION"),
    ;

    companion object {
        private val PATTERN = Regex("""^\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*$""")

        fun parse(blockquoteText: String): Match? {
            val stripped =
                blockquoteText
                    .lineSequence()
                    .map { line -> line.trimStart().removePrefix(">").trimStart() }
                    .toList()
            if (stripped.isEmpty()) return null
            val first = stripped.first()
            val match = PATTERN.matchEntire(first) ?: return null
            val kind = entries.firstOrNull { it.token == match.groupValues[1] } ?: return null
            val body = stripped.drop(1).joinToString("\n").trim()
            return Match(kind, body)
        }
    }

    data class Match(
        val kind: GithubAlertKind,
        val body: String,
    )
}

@Composable
fun AlertBlockQuote(
    model: MarkdownComponentModel,
    imageTransformer: ImageTransformer,
    fallback: @Composable () -> Unit,
) {
    val raw = model.node.text(model.content)
    val match = GithubAlertKind.parse(raw)
    if (match == null) {
        fallback()
        return
    }

    val (kind, body) = match
    val palette = paletteFor(kind)
    val label = labelFor(kind)
    val icon = iconFor(kind)

    Surface(
        color = palette.container,
        contentColor = palette.content,
        shape = RoundedCornerShape(LocalPersonality.current.shape.corner),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                )
            }

            if (body.isNotBlank()) {
                Markdown(
                    content = body,
                    colors = rememberMarkdownColors(),
                    typography = rememberMarkdownTypography(),
                    flavour = GFMFlavourDescriptor(),
                    imageTransformer = imageTransformer,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                )
            }
        }
    }
}

private fun ASTNode.text(content: String): String = content.substring(startOffset, endOffset)

private data class AlertPalette(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
private fun paletteFor(kind: GithubAlertKind): AlertPalette {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        GithubAlertKind.NOTE -> {
            AlertPalette(
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                accent = scheme.secondary,
            )
        }

        GithubAlertKind.TIP -> {
            AlertPalette(
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
                accent = scheme.tertiary,
            )
        }

        GithubAlertKind.IMPORTANT -> {
            AlertPalette(
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
                accent = scheme.primary,
            )
        }

        GithubAlertKind.WARNING -> {
            AlertPalette(
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
                accent = scheme.tertiary,
            )
        }

        GithubAlertKind.CAUTION -> {
            AlertPalette(
                container = scheme.errorContainer,
                content = scheme.onErrorContainer,
                accent = scheme.error,
            )
        }
    }
}

private fun iconFor(kind: GithubAlertKind): ImageVector =
    when (kind) {
        GithubAlertKind.NOTE -> Icons.Outlined.Info
        GithubAlertKind.TIP -> Icons.Outlined.Lightbulb
        GithubAlertKind.IMPORTANT -> Icons.Outlined.Campaign
        GithubAlertKind.WARNING -> Icons.Outlined.Warning
        GithubAlertKind.CAUTION -> Icons.Outlined.Report
    }

@Composable
private fun labelFor(kind: GithubAlertKind): String =
    when (kind) {
        GithubAlertKind.NOTE -> stringResource(Res.string.markdown_alert_note)
        GithubAlertKind.TIP -> stringResource(Res.string.markdown_alert_tip)
        GithubAlertKind.IMPORTANT -> stringResource(Res.string.markdown_alert_important)
        GithubAlertKind.WARNING -> stringResource(Res.string.markdown_alert_warning)
        GithubAlertKind.CAUTION -> stringResource(Res.string.markdown_alert_caution)
    }

@Composable
fun defaultBlockQuoteFallback(model: MarkdownComponentModel) {
    MarkdownBlockQuote(
        content = model.content,
        node = model.node,
        style = model.typography.quote,
    )
}
