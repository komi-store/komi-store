package zed.rainxch.core.presentation.components.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            contentColor = colors.onBackground,
            topBar = {
                topBar?.let { bar ->
                    Column {
                        bar()
                        if (inkDividers) HorizontalDivider(thickness = 3.dp, color = colors.outline)
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
            floatingActionButton = { floatingActionButton?.invoke() },
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
            }
        }
        toastState?.let { KomiToastHost(state = it) }
        overlay?.invoke()
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
                KomiText(text = "Discover", role = KomiTextRole.Title)
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
                        role = KomiTextRole.Label,
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
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
                    KomiText(text = "Panel ${it + 1}", role = KomiTextRole.Title)
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
