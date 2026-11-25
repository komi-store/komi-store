package zed.rainxch.details.presentation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.refresh.KomiPullToRefresh
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.core.presentation.utils.contentWidthCap
import zed.rainxch.core.presentation.utils.isPullToRefreshSupported
import zed.rainxch.details.presentation.components.sections.about
import zed.rainxch.details.presentation.components.sections.header
import zed.rainxch.details.presentation.components.sections.logs
import zed.rainxch.details.presentation.components.sections.releaseChannel
import zed.rainxch.details.presentation.components.sections.reviews
import zed.rainxch.details.presentation.components.sections.stats
import zed.rainxch.details.presentation.components.sections.whatsNew
import zed.rainxch.details.presentation.components.states.ErrorState

@Composable
fun DetailsScreen(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    toastState: KomiToastState,
    modifier: Modifier = Modifier,
    onTranslateLanguage: ((String) -> Unit)? = null,
    onReadMoreWhatsNew: (() -> Unit)? = null,
    onOpenIssues: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
    onOpenReviews: ((composerRating: Int?) -> Unit)? = null,
) {
    KomiScaffold(
        modifier = modifier,
        topBar = {
            KomiTopBar(
                title = "",
                onBack = { onAction(DetailsAction.OnNavigateBackClick) },
                actions =
                    if (state.repository != null) {
                        { DetailsTopBarActions(state = state, onAction = onAction) }
                    } else {
                        null
                    },
            )
        },
        toastState = toastState,
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KomiLoadingIndicator()
                }
            }

            state.errorMessage != null -> {
                ErrorState(errorMessage = state.errorMessage, onAction = onAction)
            }

            else -> {
                DetailsContent(
                    state = state,
                    onAction = onAction,
                    contentPadding = innerPadding,
                    links =
                        DetailsLinks(
                            onTranslateLanguage = onTranslateLanguage,
                            onReadMoreWhatsNew = onReadMoreWhatsNew,
                            onOpenIssues = onOpenIssues,
                            onOpenSecurity = onOpenSecurity,
                            onOpenReviews = onOpenReviews,
                        ),
                )
            }
        }
    }
}

// The page's ways out to other screens; each is null when the page has nowhere to go for it.
private class DetailsLinks(
    val onTranslateLanguage: ((String) -> Unit)?,
    val onReadMoreWhatsNew: (() -> Unit)?,
    val onOpenIssues: (() -> Unit)?,
    val onOpenSecurity: (() -> Unit)?,
    val onOpenReviews: ((composerRating: Int?) -> Unit)?,
)

@Composable
private fun DetailsContent(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    contentPadding: PaddingValues,
    links: DetailsLinks,
) {
    val density = LocalDensity.current
    var containerHeightDp by remember { mutableStateOf(0.dp) }
    val listState = rememberLazyListState()
    val contentWidthDp = contentWidthCap()
    val pullEnabled = remember { isPullToRefreshSupported() }
    val isDesktop = remember { isDesktop() }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .scrollable(state = listState, orientation = Orientation.Vertical, reverseDirection = true, enabled = isDesktop)
                .onSizeChanged { size ->
                    val newHeight = with(density) { size.height.toDp() }
                    if (newHeight != containerHeightDp) containerHeightDp = newHeight
                },
        contentAlignment = Alignment.Center,
    ) {
        ScrollbarContainer(
            listState = listState,
            enabled = LocalScrollbarEnabled.current,
            modifier = Modifier.fillMaxHeight().widthIn(max = contentWidthDp).fillMaxWidth(),
        ) {
            PullToRefreshHost(
                enabled = pullEnabled,
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(DetailsAction.Refresh) },
            ) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = contentWidthDp)
                            .fillMaxWidth()
                            .arrowKeyScroll(listState, autoFocus = true)
                            .onPreviewKeyEvent { event -> event.isRefreshShortcut().also { if (it) onAction(DetailsAction.Refresh) } }
                            .padding(contentPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    sections(state, onAction, links, collapsedSectionHeight = containerHeightDp * COLLAPSED_SECTION_FRACTION)
                }
            }
        }
    }
}

private fun KeyEvent.isRefreshShortcut(): Boolean = type == KeyEventType.KeyDown && (isMetaPressed || isCtrlPressed) && key == Key.R

private fun LazyListScope.sections(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    links: DetailsLinks,
    collapsedSectionHeight: Dp,
) {
    header(state = state, onAction = onAction)
    state.stats?.let { stats(repoStats = it, onOpenIssues = links.onOpenIssues, onOpenSecurity = links.onOpenSecurity) }
    releaseChannel(state = state, onAction = onAction)
    state.selectedRelease?.let { release ->
        whatsNew(
            release = release,
            state = state,
            collapsedHeight = collapsedSectionHeight,
            onAction = onAction,
            onReadMore = links.onReadMoreWhatsNew,
        )
    }
    val onOpenReviews = links.onOpenReviews
    val summary = state.reviewSummary
    if (onOpenReviews != null && summary != null) {
        reviews(
            summary = summary,
            topReviews = state.topReviews,
            canWrite = !state.isCurrentUserOwner,
            onRate = { rating -> onOpenReviews(rating) },
            onSeeAll = { onOpenReviews(null) },
        )
    }
    state.readmeMarkdown?.let { readme ->
        about(readmeMarkdown = readme, readmeLanguage = state.readmeLanguage, onTranslateLanguage = links.onTranslateLanguage)
    }
    if (state.installLogs.isNotEmpty()) logs(state)
}

@Composable
private fun PullToRefreshHost(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { currentContent() } }
    if (enabled) {
        KomiPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            movableContent()
        }
    } else {
        movableContent()
    }
}

private const val COLLAPSED_SECTION_FRACTION = 0.4f

@Preview
@Composable
private fun DetailsScreenPreview() {
    PersonalityPreview {
        DetailsScreen(
            state = DetailsState(isLoading = false),
            onAction = {},
            toastState = KomiToastState(),
        )
    }
}
