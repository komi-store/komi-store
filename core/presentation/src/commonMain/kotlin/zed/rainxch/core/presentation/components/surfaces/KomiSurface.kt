package zed.rainxch.core.presentation.components.surfaces

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing
import zed.rainxch.core.presentation.personality.manga.decoration.inkPress
import zed.rainxch.core.presentation.personality.manga.decoration.screentoneCorner
import zed.rainxch.core.presentation.personality.manga.decoration.screentoneFill
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing

@Composable
fun KomiSurface(
    modifier: Modifier = Modifier,
    elevation: KomiSurfaceElevation = KomiSurfaceElevation.Card,
    paper: KomiSurfacePaper = KomiSurfacePaper.Surface,
    screentone: KomiScreentone = KomiScreentone.None,
    screentoneBoost: Float = 1f,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    hoverEnabled: Boolean? = null,
    tilt: Float = 0f,
    topEdgeOnly: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaSurface(
                personality = personality,
                modifier = modifier,
                elevation = elevation,
                paper = paper,
                screentone = screentone,
                screentoneBoost = screentoneBoost,
                onClick = onClick,
                onLongClick = onLongClick,
                hoverEnabled = hoverEnabled,
                tilt = tilt,
                topEdgeOnly = topEdgeOnly,
                contentPadding = contentPadding,
                content = content,
            )
        }

        is ClassicPersonality -> {
            ClassicSurface(
                personality = personality,
                modifier = modifier,
                elevation = elevation,
                paper = paper,
                onClick = onClick,
                onLongClick = onLongClick,
                contentPadding = contentPadding,
                content = content,
            )
        }
    }
}

private data class MangaElevation(
    val shadow: Dp,
    val border: Dp,
)

private fun mangaElevation(elevation: KomiSurfaceElevation): MangaElevation =
    when (elevation) {
        KomiSurfaceElevation.Flat -> MangaElevation(0.dp, 3.dp)
        KomiSurfaceElevation.Card -> MangaElevation(6.dp, 3.dp)
        KomiSurfaceElevation.Raised -> MangaElevation(10.dp, 3.dp)
        KomiSurfaceElevation.Modal -> MangaElevation(14.dp, 4.dp)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaSurface(
    personality: MangaPersonality,
    modifier: Modifier,
    elevation: KomiSurfaceElevation,
    paper: KomiSurfacePaper,
    screentone: KomiScreentone,
    screentoneBoost: Float,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    hoverEnabled: Boolean?,
    tilt: Float,
    topEdgeOnly: Boolean,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val colors = personality.colors
    val elev = mangaElevation(elevation)
    val borderWidth = if (topEdgeOnly) maxOf(elev.border, 4.dp) else elev.border
    val shape = remember(personality.shape.corner) { RoundedCornerShape(personality.shape.corner) }
    val needsClip = personality.shape.corner > 0.dp
    val fill =
        when (paper) {
            KomiSurfacePaper.Surface -> colors.surface
            KomiSurfacePaper.Background -> colors.background
        }
    val contentInk = when (paper) {
        KomiSurfacePaper.Surface -> colors.onSurface
        KomiSurfacePaper.Background -> colors.onBackground
    }

    val pressable = onClick != null || onLongClick != null
    val tiltModifier = if (tilt != 0f) Modifier.rotate(tilt) else Modifier

    val depthModifier =
        if (pressable) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val hovered by interaction.collectIsHoveredAsState()
            val focused by interaction.collectIsFocusedAsState()
            val hover = hoverEnabled ?: true
            val pressProgress =
                animateFloatAsState(if (pressed) 1f else 0f, label = "komiSurfacePress")
            val hoverProgress =
                animateFloatAsState(if (hovered && hover) 1f else 0f, label = "komiSurfaceHover")
            Modifier
                .inkFocusRing(focused = { focused }, color = colors.primary)
                .inkPress(
                    pressProgress = { pressProgress.value },
                    hoverProgress = { hoverProgress.value },
                    shadow = DpOffset(elev.shadow, elev.shadow),
                    shadowColor = colors.shadow,
                    shape = shape,
                    pressInset = 2.dp,
                    hoverLift = 3.dp,
                ).combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
        } else if (elev.shadow > 0.dp) {
            Modifier.hardShadow(
                offset = DpOffset(elev.shadow, elev.shadow),
                color = colors.shadow,
                shape = shape
            )
        } else {
            Modifier
        }

    val toneModifier = when (screentone) {
        KomiScreentone.None -> Modifier
        KomiScreentone.Corner -> Modifier.screentoneCorner(
            colors.onSurface,
            colors.screentoneOpacity,
            boost = screentoneBoost
        )

        KomiScreentone.Fill -> Modifier.screentoneFill(
            colors.onSurface,
            colors.screentoneOpacity
        )
    }

    val borderModifier =
        if (topEdgeOnly) {
            Modifier.drawBehind {
                drawRect(
                    color = colors.outline,
                    topLeft = Offset.Zero,
                    size = Size(size.width, borderWidth.toPx())
                )
            }
        } else {
            Modifier.border(width = borderWidth, color = colors.outline, shape = shape)
        }

    Box(
        modifier =
            modifier
                .then(tiltModifier)
                .then(depthModifier)
                .then(if (needsClip) Modifier.clip(shape) else Modifier)
                .background(color = fill, shape = shape)
                .then(toneModifier)
                .then(borderModifier)
                .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentInk) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassicSurface(
    personality: ClassicPersonality,
    modifier: Modifier,
    elevation: KomiSurfaceElevation,
    paper: KomiSurfacePaper,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val colors = personality.colors
    val shape = remember(personality.shape.corner) { RoundedCornerShape(personality.shape.corner) }
    val border =
        remember(personality.shape.borderPanel, colors.outlineVariant) {
            BorderStroke(personality.shape.borderPanel, colors.outlineVariant)
        }
    val fill =
        when (paper) {
            KomiSurfacePaper.Surface -> colors.surface
            KomiSurfacePaper.Background -> colors.background
        }
    val shadowElevation =
        when (elevation) {
            KomiSurfaceElevation.Flat -> 0.dp
            KomiSurfaceElevation.Card -> 1.dp
            KomiSurfaceElevation.Raised -> 3.dp
            KomiSurfaceElevation.Modal -> 8.dp
        }

    val clickModifier =
        if (onClick != null || onLongClick != null) {
            Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
        } else {
            Modifier
        }
    Surface(
        modifier = modifier,
        shape = shape,
        color = fill,
        contentColor = colors.onSurface,
        border = border,
        shadowElevation = shadowElevation,
    ) {
        Box(modifier = Modifier.then(clickModifier).padding(contentPadding)) { content() }
    }
}

@Composable
private fun PreviewPanelBody() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        KomiText("Ollama", role = KomiTextRole.Title)
        KomiText("Run large language models locally.", role = KomiTextRole.Body)
        KomiText("v0.5.4 · 174M", role = KomiTextRole.Mono)
    }
}

@Preview
@Composable
private fun KomiSurfaceMangaCardPreview() {
    PersonalityPreview(mangaPersonality()) {
        KomiSurface(
            elevation = KomiSurfaceElevation.Card,
            screentone = KomiScreentone.Corner,
            onClick = {},
            contentPadding = PaddingValues(Spacing.lg),
        ) {
            PreviewPanelBody()
        }
    }
}

@Preview
@Composable
private fun KomiSurfaceMangaModalPreview() {
    PersonalityPreview(mangaPersonality()) {
        KomiSurface(
            elevation = KomiSurfaceElevation.Modal,
            paper = KomiSurfacePaper.Background,
            contentPadding = PaddingValues(Spacing.lg),
        ) {
            PreviewPanelBody()
        }
    }
}

@Preview
@Composable
private fun KomiSurfaceMangaFillTiltPreview() {
    PersonalityPreview(mangaPersonality()) {
        KomiSurface(
            elevation = KomiSurfaceElevation.Raised,
            screentone = KomiScreentone.Fill,
            tilt = -0.4f,
            contentPadding = PaddingValues(Spacing.lg),
        ) {
            PreviewPanelBody()
        }
    }
}

@Preview
@Composable
private fun KomiSurfaceMangaNightPreview() {
    PersonalityPreview(mangaPersonality(paper = MangaPaper.NIGHT, accent = MangaAccent.FROST)) {
        KomiSurface(
            screentone = KomiScreentone.Corner,
            onClick = {},
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            PreviewPanelBody()
        }
    }
}

@Preview
@Composable
private fun KomiSurfaceClassicPreview() {
    PersonalityPreview(classicPersonality()) {
        KomiSurface(onClick = {}, contentPadding = PaddingValues(Spacing.lg)) { PreviewPanelBody() }
    }
}

@Preview
@Composable
private fun KomiSurfaceClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) {
        KomiSurface(
            elevation = KomiSurfaceElevation.Modal,
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            PreviewPanelBody()
        }
    }
}
