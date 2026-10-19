package zed.rainxch.repopages.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import zed.rainxch.core.presentation.components.markdown.MarkdownImageTransformer
import zed.rainxch.core.presentation.components.markdown.githubStoreMarkdownComponents
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownColors
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownTypography

@Composable
fun RepoMarkdown(
    content: String,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val imageTransformer: ImageTransformer =
        if (LocalInspectionMode.current) {
            remember { NoOpImageTransformerImpl() }
        } else {
            val probeClient = koinInject<HttpClient>(qualifier = named("test"))
            remember(probeClient) { MarkdownImageTransformer(probeClient) }
        }
    val colors = rememberMarkdownColors()
    val typography = rememberMarkdownTypography()
    val components =
        remember(isDark, imageTransformer) {
            githubStoreMarkdownComponents(imageTransformer, isDark)
        }
    Markdown(
        content = content,
        colors = colors,
        typography = typography,
        imageTransformer = imageTransformer,
        components = components,
        modifier = modifier,
    )
}
