package zed.rainxch.core.presentation.components.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.layout.WindowBreakpoints
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun rememberAdaptiveListDetailState(): AdaptiveListDetailState =
    rememberSaveable(saver = AdaptiveListDetailStateSaver) {
        AdaptiveListDetailState()
    }

private val AdaptiveListDetailStateSaver: Saver<AdaptiveListDetailState, Any> =
    Saver(
        save = { state ->
            val args = state.currentArgs ?: return@Saver booleanArrayOf(false)
            listOf(
                true,
                args.repositoryId,
                args.owner ?: "",
                args.repo ?: "",
                args.isComingFromUpdate,
                args.sourceHost ?: "",
            )
        },
        restore = { saved ->
            if (saved is BooleanArray || saved !is List<*> || saved.isEmpty() || saved[0] != true) {
                AdaptiveListDetailState(initial = null)
            } else {
                AdaptiveListDetailState(
                    initial =
                        AdaptiveDetailArgs(
                            repositoryId = (saved[1] as? Long) ?: 0L,
                            owner = (saved[2] as? String)?.takeIf { it.isNotEmpty() },
                            repo = (saved[3] as? String)?.takeIf { it.isNotEmpty() },
                            isComingFromUpdate = (saved[4] as? Boolean) ?: false,
                            sourceHost = (saved[5] as? String)?.takeIf { it.isNotEmpty() },
                        ),
                )
            }
        },
    )

@Composable
fun AdaptiveListDetailScaffold(
    state: AdaptiveListDetailState,
    list: @Composable (isExpanded: Boolean) -> Unit,
    detail: @Composable (AdaptiveDetailArgs) -> Unit,
    emptyPaneTitle: String,
    emptyPaneSubtitle: String,
    modifier: Modifier = Modifier,
    initialListFraction: Float = 0.46f,
    minListWidthDp: Int = 480,
    minDetailWidthDp: Int = 600,
    expandedBreakpointDp: Int = WindowBreakpoints.Expanded.value.toInt(),
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthDp = maxWidth.value
        val effectiveBreakpoint =
            maxOf(expandedBreakpointDp.toFloat(), (minListWidthDp + minDetailWidthDp).toFloat())
        val isExpanded = totalWidthDp >= effectiveBreakpoint
        if (!isExpanded) {
            Box(modifier = Modifier.fillMaxSize()) { list(false) }
            return@BoxWithConstraints
        }

        val minList = minListWidthDp.toFloat()
        val maxList = (totalWidthDp - minDetailWidthDp).coerceAtLeast(minList)

        var listWidthDp by rememberSaveable {
            mutableFloatStateOf((totalWidthDp * initialListFraction).coerceIn(minList, maxList))
        }
        val clamped = listWidthDp.coerceIn(minList, maxList)
        val density = LocalDensity.current

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .width(clamped.dp)
                        .fillMaxHeight(),
            ) {
                list(true)
            }
            PaneResizeHandle(
                onDrag = { deltaPx ->
                    val deltaDp = with(density) { deltaPx.toDp().value }
                    listWidthDp = (listWidthDp + deltaDp).coerceIn(minList, maxList)
                },
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) {
                val args = state.currentArgs
                if (args == null) {
                    EmptyDetailPlaceholder(
                        title = emptyPaneTitle,
                        subtitle = emptyPaneSubtitle,
                    )
                } else {
                    // The detail beside a tab's list must not hand its actions to the tab card.
                    CompositionLocalProvider(LocalTabCardSlot provides null) { detail(args) }
                }
            }
        }
    }
}

@Composable
private fun PaneResizeHandle(onDrag: (Float) -> Unit) {
    val draggableState = rememberDraggableState(onDelta = onDrag)
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(10.dp)
                .pointerHoverIcon(PointerIcon.Crosshair)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(LocalPersonality.current.colors.divider),
        )
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(LocalPersonality.current.colors.outlineVariant),
        )
    }
}

@Composable
private fun EmptyDetailPlaceholder(
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.OpenInBrowser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

fun AdaptiveDetailArgs.matches(other: AdaptiveDetailArgs): Boolean =
    repositoryId == other.repositoryId &&
        owner == other.owner &&
        repo == other.repo &&
        sourceHost == other.sourceHost
