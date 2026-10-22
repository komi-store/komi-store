package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import zed.rainxch.core.domain.utils.applyThemeAwareImages
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.components.markdown.MarkdownImageTransformer
import zed.rainxch.core.presentation.components.markdown.githubStoreMarkdownComponents
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownColors
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownTypography
import zed.rainxch.details.presentation.utils.ProvideLanguageLinkInterceptor
import zed.rainxch.details.presentation.utils.splitMarkdownIntoChunks
import zed.rainxch.githubstore.core.presentation.res.*
import kotlin.math.abs

fun LazyListScope.about(
    readmeMarkdown: String,
    readmeLanguage: String?,
    onTranslateLanguage: ((String) -> Unit)? = null,
    onOpenInternalMarkdown: ((String) -> Unit)? = null,
) {
    item {
        val colors = LocalPersonality.current.colors
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text = stringResource(Res.string.about_this_app),
                    role = KomiTextRole.SectionTitle,
                    color = colors.onBackground,
                )
                readmeLanguage?.let {
                    KomiText(
                        text = it,
                        role = KomiTextRole.Caption,
                        color = colors.outline,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    item(key = "about_markdown") {
        val isDark = isSystemInDarkTheme()
        val raw = applyThemeAwareImages(readmeMarkdown, isDark = isDark)

        val probeClient = koinInject<io.ktor.client.HttpClient>(
            qualifier = org.koin.core.qualifier.named("test"),
        )
        val imageTransformer = remember(probeClient) {
            MarkdownImageTransformer(probeClient)
        }

        if (onTranslateLanguage != null) {
            ProvideLanguageLinkInterceptor(
                onTranslate = onTranslateLanguage,
                onOpenInternalMarkdown = onOpenInternalMarkdown,
            ) {
                MarkdownContent(
                    rawMarkdown = raw,
                    isDark = isDark,
                    imageTransformer = imageTransformer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            MarkdownContent(
                rawMarkdown = raw,
                isDark = isDark,
                imageTransformer = imageTransformer,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun MarkdownContent(
    rawMarkdown: String,
    isDark: Boolean,
    imageTransformer: ImageTransformer,
    modifier: Modifier = Modifier,
) {
    val personalityColors = LocalPersonality.current.colors
    val colors = rememberMarkdownColors()
    val typography = rememberMarkdownTypography()

    var fullChunks by remember(rawMarkdown, isDark) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(rawMarkdown, isDark) {
        val processed = withContext(Dispatchers.Default) {
            val themed = applyThemeAwareImages(rawMarkdown, isDark)
            zed.rainxch.core.domain.utils.separateAdjacentImageLinks(themed)
        }
        val chunks = withContext(Dispatchers.Default) {
            splitMarkdownIntoChunks(processed, targetChunkChars = 4000)
        }
        fullChunks = chunks
    }

    val flavour = remember { GFMFlavourDescriptor() }
    val parser = remember(flavour) { MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable) }
    val components = remember(isDark, imageTransformer) {
        githubStoreMarkdownComponents(imageTransformer, isDark)
    }

    CompositionLocalProvider(LocalContentColor provides personalityColors.onBackground) {
        Column(modifier = modifier) {
            ProgressiveMarkdown(
                isExpanded = true,
                fullChunks = fullChunks,
                collapsedHeight = 0.dp,
                colors = colors,
                typography = typography,
                components = components,
                flavour = flavour,
                parser = parser,
                imageTransformer = imageTransformer,
                onMeasured = {},
                effectiveHeight = 1f,
                collapsedHeightPx = 0f,
                rawKey = rawMarkdown,
            )
        }
    }
}

@Composable
internal fun ProgressiveMarkdown(
    isExpanded: Boolean,
    fullChunks: List<String>?,
    collapsedHeight: Dp,
    colors: com.mikepenz.markdown.model.MarkdownColors,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    components: com.mikepenz.markdown.compose.components.MarkdownComponents,
    flavour: GFMFlavourDescriptor,
    parser: MarkdownParser,
    imageTransformer: ImageTransformer,
    onMeasured: (Float) -> Unit,
    effectiveHeight: Float,
    collapsedHeightPx: Float,
    rawKey: String,
) {
    val chunks = fullChunks
    if (chunks == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(collapsedHeight.takeIf { it > 0.dp } ?: 120.dp),
            contentAlignment = Alignment.Center,
        ) {
            KomiCircularProgress(modifier = Modifier.size(28.dp))
        }
        return
    }

    var renderedCount by remember(rawKey) { mutableStateOf(1) }
    LaunchedEffect(rawKey, isExpanded, chunks.size) {
        if (!isExpanded) return@LaunchedEffect
        while (renderedCount < chunks.size) {

            kotlinx.coroutines.yield()
            renderedCount++
        }
    }

    var lastReportedPx by remember(rawKey) { mutableStateOf(0f) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                val measured = size.height.toFloat()
                val decisive = effectiveHeight > collapsedHeightPx
                if (decisive) return@onSizeChanged
                if (abs(measured - lastReportedPx) < 1f) return@onSizeChanged
                lastReportedPx = measured
                if (measured > effectiveHeight) onMeasured(measured)
            },
    ) {
        val visible = chunks.take(renderedCount.coerceAtMost(chunks.size))
        visible.forEachIndexed { index, chunk ->
            androidx.compose.runtime.key(rawKey, index) {
                val markdownState = rememberMarkdownState(
                    content = chunk,
                    flavour = flavour,
                    parser = parser,
                    retainState = true,
                )
                Markdown(
                    markdownState = markdownState,
                    colors = colors,
                    typography = typography,
                    imageTransformer = imageTransformer,
                    components = components,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (isExpanded && renderedCount < chunks.size) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                KomiCircularProgress(modifier = Modifier.size(20.dp))
            }
        }
    }
}
