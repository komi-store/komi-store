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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import zed.rainxch.core.domain.utils.applyThemeAwareImages
import zed.rainxch.core.domain.utils.separateAdjacentImageLinks
import zed.rainxch.core.presentation.components.markdown.MarkdownImageTransformer
import zed.rainxch.core.presentation.components.markdown.githubStoreMarkdownComponents
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownColors
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownTypography
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.utils.ProvideLanguageLinkInterceptor
import zed.rainxch.details.presentation.utils.splitMarkdownIntoChunks
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.about_this_app
import kotlin.math.abs

fun LazyListScope.about(
    readmeMarkdown: String,
    readmeLanguage: String?,
    onTranslateLanguage: ((String) -> Unit)? = null,
    onOpenInternalMarkdown: ((String) -> Unit)? = null,
) {
    item { AboutHeading(readmeLanguage = readmeLanguage) }
    item(key = "about_markdown") {
        AboutMarkdown(
            readmeMarkdown = readmeMarkdown,
            onTranslateLanguage = onTranslateLanguage,
            onOpenInternalMarkdown = onOpenInternalMarkdown,
        )
    }
}

@Composable
private fun AboutHeading(readmeLanguage: String?) {
    val colors = LocalPersonality.current.colors
    Column {
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
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
                readmeLanguage?.let { KomiText(text = it, role = KomiTextRole.Caption, color = colors.outline) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AboutMarkdown(
    readmeMarkdown: String,
    onTranslateLanguage: ((String) -> Unit)?,
    onOpenInternalMarkdown: ((String) -> Unit)?,
) {
    val isDark = isSystemInDarkTheme()
    val raw = applyThemeAwareImages(readmeMarkdown, isDark = isDark)
    val probeClient = koinInject<io.ktor.client.HttpClient>(qualifier = named("test"))
    val imageTransformer = remember(probeClient) { MarkdownImageTransformer(probeClient) }
    if (onTranslateLanguage != null) {
        ProvideLanguageLinkInterceptor(onTranslate = onTranslateLanguage, onOpenInternalMarkdown = onOpenInternalMarkdown) {
            MarkdownContent(rawMarkdown = raw, isDark = isDark, imageTransformer = imageTransformer, modifier = Modifier.fillMaxWidth())
        }
    } else {
        MarkdownContent(rawMarkdown = raw, isDark = isDark, imageTransformer = imageTransformer, modifier = Modifier.fillMaxWidth())
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
    var fullChunks by remember(rawMarkdown, isDark) { mutableStateOf<ImmutableList<String>?>(null) }
    LaunchedEffect(rawMarkdown, isDark) {
        fullChunks =
            withContext(Dispatchers.Default) {
                splitMarkdownIntoChunks(
                    separateAdjacentImageLinks(applyThemeAwareImages(rawMarkdown, isDark)),
                    targetChunkChars = MARKDOWN_CHUNK_CHARS,
                ).toImmutableList()
            }
    }
    val components = remember(isDark, imageTransformer) { githubStoreMarkdownComponents(imageTransformer, isDark) }
    CompositionLocalProvider(LocalContentColor provides personalityColors.onBackground) {
        Column(modifier = modifier) {
            ProgressiveMarkdown(
                isExpanded = true,
                fullChunks = fullChunks,
                collapsedHeight = 0.dp,
                colors = rememberMarkdownColors(),
                typography = rememberMarkdownTypography(),
                components = components,
                imageTransformer = imageTransformer,
                onMeasure = {},
                effectiveHeight = 1f,
                collapsedHeightPx = 0f,
                rawKey = rawMarkdown,
            )
        }
    }
}

// Renders the chunks one frame at a time once expanded, so a long document never blocks a frame.
@Composable
internal fun ProgressiveMarkdown(
    isExpanded: Boolean,
    fullChunks: ImmutableList<String>?,
    collapsedHeight: Dp,
    colors: MarkdownColors,
    typography: MarkdownTypography,
    components: MarkdownComponents,
    imageTransformer: ImageTransformer,
    onMeasure: (Float) -> Unit,
    effectiveHeight: Float,
    collapsedHeightPx: Float,
    rawKey: String,
) {
    val chunks = fullChunks
    if (chunks == null) {
        Box(
            modifier = Modifier.fillMaxWidth().height(collapsedHeight.takeIf { it > 0.dp } ?: 120.dp),
            contentAlignment = Alignment.Center,
        ) {
            KomiCircularProgress(modifier = Modifier.size(28.dp))
        }
        return
    }
    val flavour = remember { GFMFlavourDescriptor() }
    val parser = remember(flavour) { MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable) }
    var renderedCount by remember(rawKey) { mutableIntStateOf(1) }
    LaunchedEffect(rawKey, isExpanded, chunks.size) {
        if (!isExpanded) return@LaunchedEffect
        while (renderedCount < chunks.size) {
            yield()
            renderedCount++
        }
    }
    var lastReportedPx by remember(rawKey) { mutableFloatStateOf(0f) }
    Column(
        modifier =
            Modifier.fillMaxWidth().onSizeChanged { size ->
                val measured = size.height.toFloat()
                val decisive = effectiveHeight > collapsedHeightPx
                if (!decisive && abs(measured - lastReportedPx) >= 1f) {
                    lastReportedPx = measured
                    if (measured > effectiveHeight) onMeasure(measured)
                }
            },
    ) {
        chunks.take(renderedCount.coerceAtMost(chunks.size)).forEachIndexed { index, chunk ->
            key(rawKey, index) {
                val markdownState = rememberMarkdownState(content = chunk, flavour = flavour, parser = parser, retainState = true)
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
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                KomiCircularProgress(modifier = Modifier.size(20.dp))
            }
        }
    }
}

private const val MARKDOWN_CHUNK_CHARS = 4000
