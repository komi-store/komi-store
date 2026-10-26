package zed.rainxch.core.presentation.components.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

private const val MAX_LINK_ANCESTOR_DEPTH = 4

fun githubStoreMarkdownComponents(
    imageTransformer: ImageTransformer,
    isDark: Boolean,
): MarkdownComponents =
    markdownComponents(
        blockQuote = { model ->
            AlertBlockQuote(
                model = model,
                imageTransformer = imageTransformer,
                fallback = { DefaultBlockQuoteFallback(model) },
            )
        },
        codeFence = { model ->
            val lang =
                model.node.children
                    .firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
                    ?.let { model.content.substring(it.startOffset, it.endOffset).trim() }
                    .orEmpty()
            val detailsSummary = infoStringForDetails(lang)
            if (detailsSummary != null) {
                ExpandableDetails(
                    model = model,
                    encodedSummary = detailsSummary,
                    imageTransformer = imageTransformer,
                )
            } else {
                SyntaxHighlightedCode(model, isDark)
            }
        },
        image = { model -> LinkAwareMarkdownImage(model.content, model.node, imageTransformer) },
    )

@Composable
private fun LinkAwareMarkdownImage(
    content: String,
    node: ASTNode,
    imageTransformer: ImageTransformer,
) {
    val imageSrc =
        findChildRecursive(node, MarkdownElementTypes.LINK_DESTINATION)
            ?.getUnescapedTextInNode(content)
            ?: return
    val outerHref = findEnclosingLinkDestination(node, content)
    val imageData = imageTransformer.transform(imageSrc) ?: return

    val blockModifier = Modifier.fillMaxWidth()

    if (outerHref != null) {
        val uriHandler = LocalUriHandler.current
        Image(
            painter = imageData.painter,
            contentDescription = imageData.contentDescription,
            modifier =
                blockModifier.clickable {
                    runCatching { uriHandler.openUri(outerHref) }
                },
            alignment = imageData.alignment,
            contentScale = imageData.contentScale,
            alpha = imageData.alpha,
            colorFilter = imageData.colorFilter,
        )
    } else {
        Image(
            painter = imageData.painter,
            contentDescription = imageData.contentDescription,
            modifier = blockModifier,
            alignment = imageData.alignment,
            contentScale = imageData.contentScale,
            alpha = imageData.alpha,
            colorFilter = imageData.colorFilter,
        )
    }
}

private fun findChildRecursive(
    node: ASTNode,
    type: IElementType,
): ASTNode? =
    node.children.firstNotNullOfOrNull { child ->
        if (child.type == type) child else findChildRecursive(child, type)
    }

private fun findEnclosingLinkDestination(
    imageNode: ASTNode,
    content: String,
): String? {
    val link =
        generateSequence(imageNode.parent) { it.parent }
            .take(MAX_LINK_ANCESTOR_DEPTH)
            .firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK }
            ?: return null
    return link.children
        .firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
        ?.getUnescapedTextInNode(content)
}
