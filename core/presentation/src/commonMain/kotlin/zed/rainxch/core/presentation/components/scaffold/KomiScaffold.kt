package zed.rainxch.core.presentation.components.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.bars.HandTopBarToTabCard
import zed.rainxch.core.presentation.components.bars.KomiActionBar
import zed.rainxch.core.presentation.components.bars.KomiTabCard
import zed.rainxch.core.presentation.components.bars.LocalScaffoldChrome
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.components.bars.ScaffoldChrome
import zed.rainxch.core.presentation.components.bars.foldsTabCard
import zed.rainxch.core.presentation.components.bars.rememberScaffoldChrome
import zed.rainxch.core.presentation.components.bars.scrollsChrome
import zed.rainxch.core.presentation.components.bars.tabCardContentInsets
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.overlays.KomiToastHost
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.gridPaper
import zed.rainxch.core.presentation.personality.manga.decoration.screentoneFill
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing

@Composable
fun KomiScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    toastState: KomiToastState? = null,
    overlay: (@Composable () -> Unit)? = null,
    grid: Boolean = true,
    screentone: Boolean = false,
    dividers: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    val inkDividers = isManga && dividers
    // On a Classic tab screen the tab card at the bottom holds the top bar's search and actions
    // and the FAB, and folds as the content scrolls; the content keeps room for the status bar.
    val tabCard = LocalTabCardSlot.current?.takeIf { !isManga }
    val scaffoldTopBar = topBar.takeIf { tabCard == null }
    val scaffoldFab = floatingActionButton.takeIf { tabCard == null }
    val chrome = rememberScaffoldChrome(enabled = !isManga && scaffoldTopBar != null)
    // The FAB joins the action bar when there is one, instead of floating on its own.
    val fabInBar = chrome?.hasActionBar == true

    Box(modifier = modifier.fillMaxSize().scrollsChrome(chrome).foldsTabCard(tabCard)) {
        HandTopBarToTabCard(slot = tabCard, topBar = topBar)
        Scaffold(
            containerColor = colors.background,
            contentColor = colors.onBackground,
            contentWindowInsets = tabCardContentInsets(tabCard),
            topBar = {
                scaffoldTopBar?.let { bar ->
                    CompositionLocalProvider(LocalScaffoldChrome provides chrome) {
                        Column {
                            bar()
                            if (inkDividers) HorizontalDivider(thickness = 3.dp, color = colors.outline)
                        }
                    }
                }
            },
            bottomBar = {
                bottomBar?.let { bar ->
                    Column {
                        if (inkDividers) HorizontalDivider(thickness = 3.dp, color = colors.outline)
                        bar()
                    }
                }
            },
            floatingActionButton = { if (!fabInBar) scaffoldFab?.invoke() },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (isManga && grid) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .gridPaper(color = colors.onSurface, opacity = colors.gridOpacity),
                    )
                }
                if (isManga && screentone) {
                    Box(
                        modifier =
                            Modifier.matchParentSize().screentoneFill(
                                color = colors.onSurface,
                                opacity = colors.screentoneOpacity,
                            ),
                    )
                }
                content(innerPadding)
                chrome?.let { FloatingActionBar(chrome = it, floatingActionButton = floatingActionButton, padding = innerPadding) }
            }
        }
        KomiTabCard(slot = tabCard, fab = floatingActionButton, modifier = Modifier.align(Alignment.BottomCenter))
        toastState?.let { KomiToastHost(state = it) }
        overlay?.invoke()
    }
}

// Floats over the content, above any bottom bar, and slides out of the way while the content
// scrolls down, so it neither reserves a strip of the screen nor hides the end of a list.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.FloatingActionBar(
    chrome: ScaffoldChrome,
    floatingActionButton: (@Composable () -> Unit)?,
    padding: PaddingValues,
) {
    if (!chrome.hasActionBar) return
    val state = chrome.actionBarBehavior.state
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(padding)
                .clipToBounds(),
    ) {
        KomiActionBar(
            onBack = chrome.onBack.value,
            floatingActionButton = floatingActionButton,
            actions = chrome.actions.value,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { state.offsetLimit = -it.height.toFloat() }
                    .graphicsLayer { translationY = -state.offset },
        )
    }
}

@Composable
private fun PreviewScaffold() {
    val colors = LocalPersonality.current.colors
    KomiScaffold(
        topBar = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(text = "Discover", role = KomiTextRole.ScreenTitle)
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Explore", "Search", "Top Charts", "You").forEach {
                    KomiText(
                        text = it,
                        role = KomiTextRole.LabelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        floatingActionButton = {
            KomiIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add",
                onClick = {},
                variant = KomiButtonVariant.Primary,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            repeat(3) {
                KomiSurface(
                    elevation = KomiSurfaceElevation.Card,
                    contentPadding = PaddingValues(Spacing.lg),
                ) {
                    KomiText(text = "Panel ${it + 1}", role = KomiTextRole.CardTitle)
                }
            }
        }
    }
}

@Preview
@Composable
private fun KomiScaffoldMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewScaffold() }
}

@Preview
@Composable
private fun KomiScaffoldClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewScaffold() }
}
