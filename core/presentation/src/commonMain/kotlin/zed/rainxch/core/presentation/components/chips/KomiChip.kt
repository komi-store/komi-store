package zed.rainxch.core.presentation.components.chips

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
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
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.tan
import androidx.compose.material3.FilterChip as MaterialFilterChip

@Composable
fun KomiChip(
    label: String,
    modifier: Modifier = Modifier,
    kind: KomiChipKind = KomiChipKind.Info,
    size: KomiChipSize = KomiChipSize.Md,
    selected: Boolean = false,
    index: Int = 0,
    tilt: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    count: Int? = null,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaChip(
                personality = personality,
                label = label,
                modifier = modifier,
                kind = kind,
                size = size,
                selected = selected,
                index = index,
                tilt = tilt,
                leadingIcon = leadingIcon,
                leadingContent = leadingContent,
                count = count,
                onClick = onClick,
                onRemove = onRemove,
            )
        }

        is ClassicPersonality -> {
            ClassicChip(
                label = label,
                modifier = modifier,
                kind = kind,
                selected = selected,
                leadingIcon = leadingIcon,
                leadingContent = leadingContent,
                count = count,
                onClick = onClick,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun MangaChip(
    personality: MangaPersonality,
    label: String,
    modifier: Modifier,
    kind: KomiChipKind,
    size: KomiChipSize,
    selected: Boolean,
    index: Int,
    tilt: Boolean,
    leadingIcon: ImageVector?,
    leadingContent: (@Composable () -> Unit)?,
    count: Int?,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val colors = personality.colors
    val metrics = chipMetrics(kind, size)
    val isFilter = kind == KomiChipKind.Filter || kind == KomiChipKind.Input
    val tappable = onClick != null || isFilter
    val compound = count != null || kind == KomiChipKind.Input
    val doSkew = kind == KomiChipKind.Filter && tilt && !compound
    val rot =
        if (doSkew) {
            if (index % 2 != 0) 0.4f else -0.4f
        } else {
            0f
        }
    val fg = if (selected) colors.onPrimary else colors.onSurface
    val bg =
        when {
            selected -> colors.primary
            isFilter -> colors.surface
            else -> colors.background
        }
    val hasShadow = selected && isFilter
    val shape = remember(doSkew) { if (doSkew) chipStampShape(CHIP_SKEW_DEGREES) else RectangleShape }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressProgress = animateFloatAsState(if (pressed) 1f else 0f, label = "komiChipPress")

    Row(
        modifier =
            modifier
                .then(if (rot != 0f) Modifier.rotate(rot) else Modifier)
                .then(if (tappable) Modifier.inkFocusRing(focused = { focused }, color = colors.primary) else Modifier)
                .offset { IntOffset(0, (1.dp.toPx() * pressProgress.value).roundToInt()) }
                .then(
                    if (hasShadow) {
                        Modifier.hardShadow(offset = DpOffset(2.5.dp, 2.5.dp), color = colors.shadow, shape = shape)
                    } else {
                        Modifier
                    },
                ).background(color = bg, shape = shape)
                .border(width = metrics.border, color = colors.outline, shape = shape)
                .then(
                    if (tappable) {
                        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick ?: {})
                    } else {
                        Modifier
                    },
                ).height(metrics.height)
                .padding(horizontal = metrics.paddingX),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(metrics.icon), tint = fg)
        }
        KomiText(
            text = label,
            role = if (isFilter) KomiTextRole.Stamp else KomiTextRole.LabelSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (count != null) {
            ChipCountPill(count = count, selected = selected, color = fg, colors = colors)
        }
        if (kind == KomiChipKind.Input) {
            ChipRemove(onRemove = onRemove, color = fg, iconSize = metrics.icon)
        }
    }
}

@Composable
private fun ChipCountPill(
    count: Int,
    selected: Boolean,
    color: Color,
    colors: PersonalityColors,
) {
    val pillBg = if (selected) colors.onPrimaryTint else colors.surfaceVariant
    KomiText(
        text = count.toString(),
        role = KomiTextRole.Mono,
        color = color,
        maxLines = 1,
        modifier = Modifier.background(pillBg).padding(horizontal = 4.dp),
    )
}

@Composable
private fun ChipRemove(
    onRemove: (() -> Unit)?,
    color: Color,
    iconSize: Dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onRemove ?: {}),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(iconSize), tint = color)
    }
}

@Composable
private fun ClassicChip(
    label: String,
    modifier: Modifier,
    kind: KomiChipKind,
    selected: Boolean,
    leadingIcon: ImageVector?,
    leadingContent: (@Composable () -> Unit)?,
    count: Int?,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val text = if (count != null) "$label  $count" else label
    val leading: @Composable (() -> Unit)? =
        leadingContent
            ?: leadingIcon?.let {
                { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }

    when (kind) {
        KomiChipKind.Info -> {
            AssistChip(
                onClick = onClick ?: {},
                label = { Text(text) },
                modifier = modifier,
                leadingIcon = leading,
            )
        }

        KomiChipKind.Tonal -> {
            ClassicTonalChip(text = text, selected = selected, leading = leading, onClick = onClick, modifier = modifier)
        }

        KomiChipKind.Filter -> {
            MaterialFilterChip(
                selected = selected,
                onClick = onClick ?: {},
                label = { Text(text) },
                modifier = modifier,
                leadingIcon = leading,
            )
        }

        KomiChipKind.Input -> {
            InputChip(
                selected = selected,
                onClick = onClick ?: {},
                label = { Text(text) },
                modifier = modifier,
                leadingIcon = leading,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp).clickable(onClick = onRemove ?: {}),
                    )
                },
            )
        }
    }
}

@Composable
private fun ClassicTonalChip(
    text: String,
    selected: Boolean,
    leading: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val container = if (selected) colors.primaryContainer else colors.surface
    val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    AssistChip(
        onClick = onClick ?: {},
        label = { Text(text) },
        modifier = if (onClick != null) modifier.semantics { this.selected = selected } else modifier,
        leadingIcon = leading,
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = container,
                labelColor = content,
                leadingIconContentColor = content,
            ),
        border = null,
    )
}

private const val CHIP_SKEW_DEGREES = 8f

private fun chipStampShape(skewDegrees: Float): Shape =
    GenericShape { size, _ ->
        val sk = size.height * tan(skewDegrees * (PI / 180.0)).toFloat()
        moveTo(sk, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width - sk, size.height)
        lineTo(0f, size.height)
        close()
    }

private data class ChipMetrics(
    val height: Dp,
    val paddingX: Dp,
    val icon: Dp,
    val border: Dp,
)

private fun chipMetrics(
    kind: KomiChipKind,
    size: KomiChipSize,
): ChipMetrics {
    val filterLike = kind == KomiChipKind.Filter || kind == KomiChipKind.Input
    return when {
        filterLike && size == KomiChipSize.Sm -> ChipMetrics(26.dp, 11.dp, 14.dp, 2.5.dp)
        filterLike && size == KomiChipSize.Md -> ChipMetrics(34.dp, 14.dp, 16.dp, 2.5.dp)
        size == KomiChipSize.Sm -> ChipMetrics(24.dp, 9.dp, 13.dp, 2.dp)
        else -> ChipMetrics(28.dp, 10.dp, 14.dp, 2.dp)
    }
}

@Composable
private fun PreviewInfoRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        KomiChip("Android", kind = KomiChipKind.Info, leadingIcon = Icons.Default.Place)
        KomiChip("Windows", kind = KomiChipKind.Info, leadingIcon = Icons.Default.Place)
        KomiChip("#self-hosted", kind = KomiChipKind.Info)
        KomiChip("AGPL-3.0", kind = KomiChipKind.Info)
    }
}

@Composable
private fun PreviewFilterRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf("all", "media", "privacy", "ai", "dev tools").forEachIndexed { i, c ->
            KomiChip(c, kind = KomiChipKind.Filter, index = i, selected = c == "media", onClick = {})
        }
    }
}

@Composable
private fun PreviewCountInputRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        KomiChip("Photos", kind = KomiChipKind.Filter, selected = true, count = 102, onClick = {})
        KomiChip("Media", kind = KomiChipKind.Filter, count = 38, onClick = {})
        KomiChip("self-hosted", kind = KomiChipKind.Input, onRemove = {})
    }
}

@Preview
@Composable
private fun KomiChipMangaPreview() {
    PersonalityPreview(mangaPersonality()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PreviewInfoRow()
            PreviewFilterRow()
            PreviewCountInputRow()
        }
    }
}

@Preview
@Composable
private fun KomiChipMangaNightPreview() {
    PersonalityPreview(mangaPersonality(paper = MangaPaper.NIGHT, accent = MangaAccent.SUN)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PreviewFilterRow()
            PreviewInfoRow()
        }
    }
}

@Preview
@Composable
private fun KomiChipClassicPreview() {
    PersonalityPreview(classicPersonality()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PreviewInfoRow()
            PreviewFilterRow()
            PreviewCountInputRow()
        }
    }
}
