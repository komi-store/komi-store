package zed.rainxch.core.presentation.components.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

// Names what the anchor does, on mouse hover or long-press. Never the only place a fact appears.
@Composable
fun KomiTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { KomiPlainTooltip(text) },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

// Explains a badge or label that has no action of its own: a tap, hover or long-press opens it.
@Composable
fun KomiRichTooltip(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { KomiRichTooltipPanel(title = title, text = text) },
        state = state,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { scope.launch { state.show() } },
                ),
        ) {
            content()
        }
    }
}

// A one-time tip that points at its anchor and stays until the user acknowledges it.
@Composable
fun KomiCoachmark(
    visible: Boolean,
    title: String,
    text: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    LaunchedEffect(visible) {
        if (visible) state.show() else state.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            KomiRichTooltipPanel(
                title = title,
                text = text,
                action = {
                    KomiButton(
                        onClick = onDismiss,
                        label = dismissLabel,
                        variant = KomiButtonVariant.Text,
                        size = KomiButtonSize.Sm,
                    )
                },
            )
        },
        state = state,
        modifier = modifier,
        onDismissRequest = {},
        enableUserInput = false,
        hasAction = true,
        content = content,
    )
}

@Composable
private fun TooltipScope.KomiPlainTooltip(text: String) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            PlainTooltip(
                caretShape = TooltipDefaults.caretShape(),
                shape = RectangleShape,
                containerColor = personality.colors.onSurface,
                contentColor = personality.colors.surface,
            ) {
                KomiText(text = text, role = KomiTextRole.Caption, color = personality.colors.surface)
            }
        }

        is ClassicPersonality -> {
            PlainTooltip(caretShape = TooltipDefaults.caretShape()) {
                KomiText(text = text, role = KomiTextRole.Caption, color = LocalContentColor.current)
            }
        }
    }
}

@Composable
private fun TooltipScope.KomiRichTooltipPanel(
    title: String,
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            val ink = personality.colors.onSurface
            val paper = personality.colors.surface
            RichTooltip(
                caretShape = TooltipDefaults.caretShape(),
                shape = RectangleShape,
                colors =
                    TooltipDefaults.richTooltipColors(
                        containerColor = ink,
                        contentColor = paper,
                        titleContentColor = paper,
                        actionContentColor = paper,
                    ),
                title = { KomiText(text = title, role = KomiTextRole.CardTitle, color = paper) },
                action = action,
            ) {
                KomiText(text = text, role = KomiTextRole.Body, color = paper)
            }
        }

        is ClassicPersonality -> {
            RichTooltip(
                caretShape = TooltipDefaults.caretShape(),
                title = { KomiText(text = title, role = KomiTextRole.CardTitle, color = LocalContentColor.current) },
                action = action,
            ) {
                KomiText(text = text, role = KomiTextRole.Body, color = LocalContentColor.current)
            }
        }
    }
}

@Preview
@Composable
private fun KomiTooltipAnchorsPreview() {
    PersonalityPreview {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 96.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KomiTooltip(text = "Share") { KomiText(text = "Share", role = KomiTextRole.Label) }
                KomiRichTooltip(
                    title = "Verified build",
                    text = "GitHub attests this file was built from the repository's own workflow.",
                ) {
                    KomiText(text = "Verified build", role = KomiTextRole.LabelSmall)
                }
            }
        }
    }
}

@Preview
@Composable
private fun KomiCoachmarkClassicPreview() {
    PersonalityPreview(classicPersonality()) {
        Box(modifier = Modifier.padding(top = 160.dp)) {
            KomiCoachmark(
                visible = true,
                title = "Inspect before installing",
                text = "See the permissions and trackers inside this APK.",
                dismissLabel = "Got it",
                onDismiss = {},
            ) {
                KomiText(text = "Inspect", role = KomiTextRole.Label)
            }
        }
    }
}
