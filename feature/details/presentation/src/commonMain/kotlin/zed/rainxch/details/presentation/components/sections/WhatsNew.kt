package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import zed.rainxch.core.domain.utils.applyThemeAwareImages
import zed.rainxch.core.domain.utils.separateAdjacentImageLinks
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.markdown.MarkdownImageTransformer
import zed.rainxch.core.presentation.components.markdown.githubStoreMarkdownComponents
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownColors
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownTypography
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.fadeOutBrush
import zed.rainxch.core.presentation.utils.formatIsoDateOrRaw
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.model.ReleaseUi
import zed.rainxch.details.presentation.utils.splitMarkdownIntoChunks
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.no_release_notes
import zed.rainxch.githubstore.core.presentation.res.read_more
import zed.rainxch.githubstore.core.presentation.res.show_less
import zed.rainxch.githubstore.core.presentation.res.whats_new

fun LazyListScope.whatsNew(
    release: ReleaseUi,
    state: DetailsState,
    collapsedHeight: Dp,
    onAction: (DetailsAction) -> Unit,
    onReadMore: (() -> Unit)? = null,
) {
    item { WhatsNewHeading(release = release) }
    item(key = "whats_new_markdown") {
        Spacer(Modifier.height(12.dp))
        ExpandableMarkdownContent(
            release = release,
            collapsedHeight = collapsedHeight,
            isExpanded = state.isWhatsNewExpanded,
            onToggleExpand = onReadMore ?: { onAction(DetailsAction.ToggleWhatsNewExpanded) },
            measuredHeightPx = state.whatsNewMeasuredHeightPx,
            onMeasure = { onAction(DetailsAction.OnWhatsNewMeasured(it)) },
        )
    }
}

@Composable
private fun WhatsNewHeading(release: ReleaseUi) {
    val colors = LocalPersonality.current.colors
    Column {
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.whats_new),
                role = KomiTextRole.SectionTitle,
                color = colors.onBackground,
            )
        }
        Spacer(Modifier.height(10.dp))
        ReleaseLine(release = release)
    }
}

@Composable
private fun ReleaseLine(release: ReleaseUi) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surface, shape = RoundedCornerShape(LocalPersonality.current.shape.corner))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiText(
            text = release.tagName,
            role = KomiTextRole.CardTitle,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        KomiText(
            text = formatIsoDateOrRaw(release.publishedAt),
            role = KomiTextRole.Caption,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExpandableMarkdownContent(
    release: ReleaseUi,
    collapsedHeight: Dp,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    measuredHeightPx: Float?,
    onMeasure: (Float) -> Unit,
) {
    val raw = release.description ?: stringResource(Res.string.no_release_notes)
    val isDark = isSystemInDarkTheme()
    val fullChunks = rememberMarkdownChunks(raw, isDark)
    val density = LocalDensity.current
    val probeClient = koinInject<io.ktor.client.HttpClient>(qualifier = named("test"))
    val imageTransformer = remember(probeClient) { MarkdownImageTransformer(probeClient) }
    val components = remember(isDark, imageTransformer) { githubStoreMarkdownComponents(imageTransformer, isDark) }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }
    val measuredDp = measuredHeightPx?.let { with(density) { it.toDp() } }
    val effectiveHeight = measuredHeightPx ?: 0f
    val needsExpansion = effectiveHeight > collapsedHeightPx && collapsedHeightPx > 0f
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(isExpanded) {
        if (isExpanded) bringIntoViewRequester.bringIntoView()
    }
    Column(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        Box {
            Box(modifier = Modifier.markdownBox(isExpanded, needsExpansion, collapsedHeight, measuredDp)) {
                ProgressiveMarkdown(
                    isExpanded = isExpanded,
                    fullChunks = fullChunks,
                    collapsedHeight = collapsedHeight,
                    colors = rememberMarkdownColors(),
                    typography = rememberMarkdownTypography(),
                    components = components,
                    imageTransformer = imageTransformer,
                    onMeasure = onMeasure,
                    effectiveHeight = effectiveHeight,
                    collapsedHeightPx = collapsedHeightPx,
                    rawKey = raw,
                )
            }
            if (!isExpanded && needsExpansion) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(LocalPersonality.current.colors.background.fadeOutBrush),
                )
            }
        }
        if (needsExpansion) {
            Spacer(Modifier.height(12.dp))
            ReadMoreToggle(isExpanded = isExpanded, onToggleExpand = onToggleExpand)
        }
    }
}

// Release notes are themed and chunked off the main thread so a long changelog doesn't stall the page.
@Composable
private fun rememberMarkdownChunks(
    raw: String,
    isDark: Boolean,
): ImmutableList<String>? {
    var fullChunks by remember(raw, isDark) { mutableStateOf<ImmutableList<String>?>(null) }
    LaunchedEffect(raw, isDark) {
        fullChunks =
            withContext(Dispatchers.Default) {
                splitMarkdownIntoChunks(separateAdjacentImageLinks(applyThemeAwareImages(raw, isDark)), targetChunkChars = CHUNK_CHARS)
                    .toImmutableList()
            }
    }
    return fullChunks
}

private fun Modifier.markdownBox(
    isExpanded: Boolean,
    needsExpansion: Boolean,
    collapsedHeight: Dp,
    measuredDp: Dp?,
): Modifier =
    when {
        !isExpanded && needsExpansion -> height(collapsedHeight).clipToBounds()
        isExpanded && measuredDp != null -> heightIn(min = measuredDp)
        else -> this
    }

@Composable
private fun ColumnScope.ReadMoreToggle(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier =
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .background(colors.onSurface)
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KomiText(
            text = stringResource(if (isExpanded) Res.string.show_less else Res.string.read_more),
            role = KomiTextRole.CardTitle,
            color = colors.surface,
        )
        if (!isExpanded) {
            KomiIcon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = colors.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private const val CHUNK_CHARS = 4000
