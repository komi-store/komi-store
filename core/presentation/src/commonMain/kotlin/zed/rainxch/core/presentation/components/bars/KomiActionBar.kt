package zed.rainxch.core.presentation.components.bars

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
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

// A tonal cookie instead of an arrow in the top bar. Pressed, it rounds into a circle, turns and
// dips on the personality's spring; its ripple and shadow follow the shape.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieBackButton(onClick: () -> Unit) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val label = stringResource(Res.string.navigate_back)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = personality.motion.fastSpatial(),
        label = "cookiePress",
    )
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val shape = remember(morph, press) { MorphShape(morph, press) }
    KomiTooltip(text = label) {
        Box(
            modifier =
                Modifier
                    .size(BackButtonSize)
                    .graphicsLayer {
                        scaleX = 1f - PRESS_DIP * press
                        scaleY = 1f - PRESS_DIP * press
                    }.shadow(FloatingElevation, shape)
                    .clip(shape)
                    .background(colors.surfaceContainerHigh)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(color = colors.onSurface),
                        role = Role.Button,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = label,
                tint = colors.onSurface,
            )
        }
    }
}

// A Morph at one point of its progress, turned with the press around its center. A spring can
// overshoot, so the outline itself stays between the two shapes.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress = progress.coerceIn(0f, 1f))
        val center = path.getBounds().center
        path.translate(-center)
        path.transform(Matrix().apply { rotateZ(PRESS_TURN * progress) })
        path.translate(center)
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        return Outline.Generic(path)
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
        expandedShadowElevation = FloatingElevation,
    ) {
        CompositionLocalProvider(LocalInBar provides true) { content() }
    }
}

private val BackButtonSize = 56.dp

private const val PRESS_DIP = 0.1f

private const val PRESS_TURN = 90f

// Material's level 3, a FAB's: both float over the content.
private val FloatingElevation = 6.dp

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
