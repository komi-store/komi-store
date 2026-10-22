package zed.rainxch.core.presentation.components.overlays

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.layout.currentWindowSize
import zed.rainxch.core.presentation.layout.formFactorFor
import zed.rainxch.core.presentation.layout.isCompact
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.manga.decoration.screentoneCorner
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.model.MotionLevel
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

@Composable
fun KomiSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    placement: KomiSheetPlacement = KomiSheetPlacement.Auto,
    title: String? = null,
    titleJp: String? = null,
    maxWidth: Dp = 520.dp,
    dismissible: Boolean = true,
    screentone: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowSize = currentWindowSize()
    val screenHeight = windowSize.height
    val resolved =
        when (placement) {
            KomiSheetPlacement.Bottom -> {
                KomiSheetPlacement.Bottom
            }

            KomiSheetPlacement.Center -> {
                KomiSheetPlacement.Center
            }

            KomiSheetPlacement.Auto -> {
                if (formFactorFor(windowSize.width).isCompact) KomiSheetPlacement.Bottom else KomiSheetPlacement.Center
            }
        }

    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            if (resolved == KomiSheetPlacement.Bottom) {
                MangaBottomSheet(
                    personality = personality,
                    onDismiss = onDismiss,
                    title = title,
                    titleJp = titleJp,
                    footer = footer,
                    screentone = screentone,
                    screenHeight = screenHeight,
                    modifier = modifier,
                    content = content
                )
            } else {
                MangaCenterDialog(
                    personality = personality,
                    onDismiss = onDismiss,
                    title = title,
                    titleJp = titleJp,
                    footer = footer,
                    maxWidth = maxWidth,
                    dismissible = dismissible,
                    screentone = screentone,
                    screenHeight = screenHeight,
                    modifier = modifier,
                    content = content,
                )
            }
        }

        is ClassicPersonality -> {
            if (resolved == KomiSheetPlacement.Bottom) {
                ClassicBottomSheet(
                    onDismiss = onDismiss,
                    title = title,
                    footer = footer,
                    screenHeight = screenHeight,
                    modifier = modifier,
                    content = content
                )
            } else {
                ClassicCenterDialog(
                    onDismiss = onDismiss,
                    title = title,
                    footer = footer,
                    maxWidth = maxWidth,
                    dismissible = dismissible,
                    screenHeight = screenHeight,
                    modifier = modifier,
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaBottomSheet(
    personality: MangaPersonality,
    onDismiss: () -> Unit,
    title: String?,
    titleJp: String?,
    footer: (@Composable () -> Unit)?,
    screentone: Boolean,
    screenHeight: Dp,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = personality.colors
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = colors.background,
        contentColor = colors.onSurface,
        scrimColor = colors.scrimOverlay,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.outline))
            MangaSheetInner(
                personality = personality,
                title = title,
                titleJp = titleJp,
                footer = footer,
                screentone = screentone,
                grabber = true,
                reserveCloseSpace = false,
                contentPadding = PaddingSpec(top = 14.dp, bottom = 22.dp),
                maxHeight = screenHeight * 0.85f,
                content = content,
            )
        }
    }
}

@Composable
private fun MangaCenterDialog(
    personality: MangaPersonality,
    onDismiss: () -> Unit,
    title: String?,
    titleJp: String?,
    footer: (@Composable () -> Unit)?,
    maxWidth: Dp,
    dismissible: Boolean,
    screentone: Boolean,
    screenHeight: Dp,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = personality.colors
    val motionOff = personality.motion.level == MotionLevel.OFF
    var shown by remember { mutableStateOf(motionOff) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = personality.motion.defaultSpatial(),
        label = "komiSheetPop",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
            ),
    ) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = progress
                            scaleX = 0.92f + 0.08f * p
                            scaleY = 0.92f + 0.08f * p
                            alpha = p
                            rotationZ = -0.5f * (1f - p)
                        }.hardShadow(DpOffset(14.dp, 14.dp), colors.shadow)
                        .background(colors.background)
                        .border(personality.shape.borderPanel + 1.dp, colors.outline),
            ) {
                MangaSheetInner(
                    personality = personality,
                    title = title,
                    titleJp = titleJp,
                    footer = footer,
                    screentone = screentone,
                    grabber = false,
                    reserveCloseSpace = dismissible,
                    contentPadding = PaddingSpec(top = 20.dp, bottom = 24.dp),
                    maxHeight = screenHeight * 0.9f,
                    content = content,
                )
                if (dismissible) {
                    CloseStamp(
                        personality = personality,
                        onClose = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }
        }
    }
}

private data class PaddingSpec(
    val top: Dp,
    val bottom: Dp,
    val horizontal: Dp = 16.dp,
)

@Composable
private fun MangaSheetInner(
    personality: MangaPersonality,
    title: String?,
    titleJp: String?,
    footer: (@Composable () -> Unit)?,
    screentone: Boolean,
    grabber: Boolean,
    reserveCloseSpace: Boolean,
    contentPadding: PaddingSpec,
    maxHeight: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = personality.colors
    val toneMod =
        if (screentone) {
            Modifier.screentoneCorner(
                color = colors.onSurface,
                opacity = colors.screentoneOpacity,
                boost = 1.6f,
                regionWidth = 150.dp,
                regionHeight = 110.dp,
            )
        } else {
            Modifier
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .then(toneMod)
                .padding(
                    start = contentPadding.horizontal,
                    end = contentPadding.horizontal,
                    top = contentPadding.top,
                    bottom = contentPadding.bottom,
                ),
    ) {
        if (grabber) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = if (title != null) 12.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 46.dp, height = 5.dp).background(colors.outline))
            }
        }

        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(end = if (reserveCloseSpace) 44.dp else 0.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 10.dp, height = 18.dp)
                        .background(colors.primary, MangaMarkerShape)
                        .border(2.dp, colors.outline, MangaMarkerShape),
                )
                KomiText(
                    text = title,
                    role = KomiTextRole.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (titleJp != null) {
                    KomiText(
                        text = titleJp,
                        role = KomiTextRole.Caption,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            content = content,
        )

        if (footer != null) {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp)) { footer() }
        }
    }
}

@Composable
private fun CloseStamp(
    personality: MangaPersonality,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val colors = personality.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.size(44.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .hardShadow(DpOffset(3.dp, 3.dp), colors.shadow)
                    .background(colors.surface)
                    .border(3.dp, colors.outline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                modifier = Modifier.size(18.dp),
                tint = colors.onSurface,
            )
        }
    }
}

private val MangaMarkerShape =
    GenericShape { size, _ ->
        val s = size.height * 0.21f
        moveTo(s, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width - s, size.height)
        lineTo(0f, size.height)
        close()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicBottomSheet(
    onDismiss: () -> Unit,
    title: String?,
    footer: (@Composable () -> Unit)?,
    screenHeight: Dp,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.85f)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            if (title != null) {
                KomiText(
                    text = title,
                    role = KomiTextRole.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
            if (footer != null) {
                Spacer(Modifier.height(16.dp))
                footer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicCenterDialog(
    onDismiss: () -> Unit,
    title: String?,
    footer: (@Composable () -> Unit)?,
    maxWidth: Dp,
    dismissible: Boolean,
    screenHeight: Dp,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPersonality.current.colors

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        properties =
            DialogProperties(
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
            ),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = maxWidth).heightIn(max = screenHeight * 0.9f),
            shape = RoundedCornerShape(LocalPersonality.current.shape.corner),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                if (title != null) {
                    KomiText(
                        text = title,
                        role = KomiTextRole.CardTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
                if (footer != null) {
                    Spacer(Modifier.height(24.dp))
                    footer()
                }
            }
        }
    }
}

@Composable
private fun PreviewBody() {
    val colors = LocalPersonality.current.colors
    KomiText(
        text = "Remove immich from this device? Your library on the server is untouched.",
        role = KomiTextRole.Body,
        color = colors.onSurface,
    )
}

@Composable
private fun PreviewFooter() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KomiButton(
            onClick = {},
            label = "Cancel",
            variant = KomiButtonVariant.Outline,
            modifier = Modifier.weight(1f)
        )
        KomiButton(
            onClick = {},
            label = "Remove",
            variant = KomiButtonVariant.Destructive,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview
@Composable
private fun KomiSheetMangaPreview() {
    PersonalityPreview(mangaPersonality()) {
        Box(Modifier.fillMaxWidth().padding(8.dp)) {
            KomiSheet(
                onDismiss = {},
                placement = KomiSheetPlacement.Center,
                title = "Uninstall?",
                titleJp = "削除",
                footer = { PreviewFooter() },
            ) {
                PreviewBody()
            }
        }
    }
}

@Preview
@Composable
private fun KomiSheetMangaNightPreview() {
    PersonalityPreview(mangaPersonality(paper = MangaPaper.NIGHT, accent = MangaAccent.SUN)) {
        Box(Modifier.fillMaxWidth().padding(8.dp)) {
            KomiSheet(
                onDismiss = {},
                placement = KomiSheetPlacement.Center,
                title = "Choose platform",
                titleJp = "対応OS"
            ) {
                PreviewBody()
            }
        }
    }
}

@Preview
@Composable
private fun KomiSheetClassicPreview() {
    PersonalityPreview(classicPersonality()) {
        Box(Modifier.fillMaxWidth().padding(8.dp)) {
            KomiSheet(
                onDismiss = {},
                placement = KomiSheetPlacement.Center,
                title = "Uninstall?",
                footer = { PreviewFooter() }) {
                PreviewBody()
            }
        }
    }
}
