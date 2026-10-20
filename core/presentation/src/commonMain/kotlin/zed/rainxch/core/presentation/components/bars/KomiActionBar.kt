package zed.rainxch.core.presentation.components.bars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.navigate_back

// A screen's actions within thumb reach: back at the start, the actions and a floating action
// button at the end. A Classic KomiScaffold draws one from what its KomiTopBar hands it.
@Composable
fun KomiActionBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { back ->
            when (personality) {
                is ClassicPersonality -> {
                    CookieBackButton(onClick = back)
                }

                is MangaPersonality -> {
                    KomiIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.navigate_back),
                        onClick = back,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        actions?.let { content ->
            when (personality) {
                is ClassicPersonality -> {
                    ClassicActions(content = content)
                }

                is MangaPersonality -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        content = content,
                    )
                }
            }
        }
        floatingActionButton?.invoke()
    }
}

// A tonal cookie instead of an arrow in the top bar.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieBackButton(onClick: () -> Unit) {
    val colors = LocalPersonality.current.colors
    val label = stringResource(Res.string.navigate_back)
    KomiTooltip(text = label) {
        FilledTonalIconButton(
            onClick = onClick,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface,
                ),
            modifier = Modifier.size(BackButtonSize),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = label)
        }
    }
}

// Material's floating toolbar, in the cookie's color so the two read as one set.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicActions(content: @Composable RowScope.() -> Unit) {
    val colors = LocalPersonality.current.colors
    HorizontalFloatingToolbar(
        expanded = true,
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = colors.surfaceContainerHigh,
                toolbarContentColor = colors.onSurface,
            ),
    ) {
        CompositionLocalProvider(LocalInBar provides true) { content() }
    }
}

private val BackButtonSize = 56.dp

@Composable
private fun PreviewActionBar() {
    KomiActionBar(
        onBack = {},
        floatingActionButton = { KomiFab(icon = Icons.Default.Add, contentDescription = "Add", onClick = {}) },
    ) {
        KomiIconButton(Icons.Default.GridView, "Grid", {}, variant = KomiButtonVariant.Primary)
        KomiIconButton(Icons.AutoMirrored.Filled.Sort, "Sort", {})
        KomiIconButton(Icons.Default.Share, "Share", {})
    }
}

@Preview
@Composable
private fun KomiActionBarClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewActionBar() }
}

@Preview
@Composable
private fun KomiActionBarClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewActionBar() }
}

@Preview
@Composable
private fun KomiActionBarMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewActionBar() }
}
