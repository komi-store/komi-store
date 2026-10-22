package zed.rainxch.core.presentation.components.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.model.ImageTransformer
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

private const val DETAILS_INFO_PREFIX = "ghs-details"

fun infoStringForDetails(infoString: String): String? {
    val trimmed = infoString.trim()
    return when {
        trimmed == DETAILS_INFO_PREFIX -> ""
        trimmed.startsWith("$DETAILS_INFO_PREFIX|") -> trimmed.removePrefix("$DETAILS_INFO_PREFIX|")
        else -> null
    }
}

@Composable
fun ExpandableDetails(
    model: MarkdownComponentModel,
    encodedSummary: String,
    imageTransformer: ImageTransformer,
) {
    val summary = remember(encodedSummary) { decodeDetailsSummary(encodedSummary) }
    val body = remember(model.node, model.content) { extractFenceBody(model) }
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary.ifBlank { "Details" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = expanded) {
                val isDark = isSystemInDarkTheme()
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Markdown(
                        content = body,
                        colors = rememberMarkdownColors(),
                        typography = rememberMarkdownTypography(),
                        flavour = GFMFlavourDescriptor(),
                        imageTransformer = imageTransformer,
                        components = githubStoreMarkdownComponents(imageTransformer, isDark),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.width(0.dp))
                }
            }
        }
    }
}

private fun decodeDetailsSummary(encoded: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < encoded.length) {
        val c = encoded[i]
        if (c == '%' && i + 2 < encoded.length) {
            val hex = encoded.substring(i + 1, i + 3)
            val code = hex.toIntOrNull(16)
            if (code != null) {
                out.append(code.toChar())
                i += 3
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

private fun extractFenceBody(model: MarkdownComponentModel): String {
    val content = model.content
    val contentNodes = model.node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    if (contentNodes.isEmpty()) return ""
    val start = contentNodes.first().startOffset
    val end = contentNodes.last().endOffset
    return content.substring(start, end).trim()
}
