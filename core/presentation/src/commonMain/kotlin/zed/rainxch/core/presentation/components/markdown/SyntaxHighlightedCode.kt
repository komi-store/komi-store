package zed.rainxch.core.presentation.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

private const val MAX_HIGHLIGHTABLE_CHARS = 16_000

@Composable
fun SyntaxHighlightedCode(
    model: MarkdownComponentModel,
    isDark: Boolean,
) {
    val (language, code) =
        remember(model.node, model.content) {
            extractFenceContent(model.node, model.content)
        }

    var highlighted by remember(code, language, isDark) {
        mutableStateOf(AnnotatedString(code))
    }
    LaunchedEffect(code, language, isDark) {
        if (code.isEmpty() || language == SyntaxLanguage.DEFAULT) return@LaunchedEffect

        if (code.length > MAX_HIGHLIGHTABLE_CHARS) return@LaunchedEffect
        val result =
            withContext(Dispatchers.Default) {
                buildHighlighted(code, language, isDark)
            }
        highlighted = result
    }

    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = MaterialTheme.colorScheme.onSurface

    Text(
        text = highlighted,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = onContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .background(container)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

private fun extractFenceContent(
    node: ASTNode,
    content: String,
): Pair<SyntaxLanguage, String> {
    var language: SyntaxLanguage = SyntaxLanguage.DEFAULT
    val body = StringBuilder()
    var sawContent = false
    node.children.forEach { child ->
        when (child.type) {
            MarkdownTokenTypes.FENCE_LANG -> {
                val hint =
                    content
                        .substring(child.startOffset, child.endOffset)
                        .trim()
                        .lowercase()
                language = languageOf(hint)
            }

            MarkdownTokenTypes.CODE_FENCE_CONTENT -> {
                if (sawContent) body.append('\n')
                body.append(content.substring(child.startOffset, child.endOffset))
                sawContent = true
            }

            MarkdownTokenTypes.EOL -> {
                if (sawContent) body.append('\n')
            }

            else -> {}
        }
    }
    return language to body.toString()
}

private fun languageOf(hint: String): SyntaxLanguage =
    when (hint) {
        "kt", "kotlin", "kts" -> SyntaxLanguage.KOTLIN
        "java" -> SyntaxLanguage.JAVA
        "py", "python" -> SyntaxLanguage.PYTHON
        "js", "javascript", "node", "nodejs" -> SyntaxLanguage.JAVASCRIPT
        "ts", "typescript" -> SyntaxLanguage.TYPESCRIPT
        "rs", "rust" -> SyntaxLanguage.RUST
        "swift" -> SyntaxLanguage.SWIFT
        "cs", "csharp", "c#" -> SyntaxLanguage.CSHARP
        "rb", "ruby" -> SyntaxLanguage.RUBY
        "perl", "pl" -> SyntaxLanguage.PERL
        "sh", "bash", "zsh", "shell" -> SyntaxLanguage.SHELL
        "coffee", "coffeescript" -> SyntaxLanguage.COFFEESCRIPT
        "dart" -> SyntaxLanguage.DART
        else -> SyntaxLanguage.DEFAULT
    }

private fun buildHighlighted(
    code: String,
    language: SyntaxLanguage,
    isDark: Boolean,
): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString(code)
    val theme = if (isDark) SyntaxThemes.darcula() else SyntaxThemes.atom()
    val tokens =
        runCatching {
            Highlights
                .Builder()
                .code(code)
                .language(language)
                .theme(theme)
                .build()
                .getHighlights()
        }.getOrElse { return AnnotatedString(code) }

    return buildAnnotatedString(code) {
        tokens.forEach { highlight ->
            when (highlight) {
                is ColorHighlight -> {
                    addStyle(
                        SpanStyle(color = Color(0xFF000000 or highlight.rgb.toLong())),
                        highlight.location.start,
                        highlight.location.end.coerceAtMost(code.length),
                    )
                }

                is BoldHighlight -> {
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        highlight.location.start,
                        highlight.location.end.coerceAtMost(code.length),
                    )
                }
            }
        }
    }
}

private inline fun buildAnnotatedString(
    base: String,
    block: AnnotatedString.Builder.() -> Unit,
): AnnotatedString = AnnotatedString.Builder(base).apply(block).toAnnotatedString()

@Suppress("UnusedReceiverParameter")
private fun MarkdownElementTypes.placeholder(): Nothing = error("placeholder")
