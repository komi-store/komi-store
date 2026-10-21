package zed.rainxch.core.presentation.components.bars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.badge.KomiBadge
import zed.rainxch.core.presentation.components.badge.KomiBadgedBox
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.search_repositories_hint
import zed.rainxch.githubstore.core.presentation.res.show_tabs

// Classic's tab card, after Chocola: one card at the bottom of the tab screens with the screen's
// search field and actions, and the tab row under them. The row folds behind a toggle while the
// content scrolls down and unfolds on the way up. The app shell draws it, so it stays put while the
// tabs slide; each tab screen hands it its content through LocalTabCardSlot.
@Stable
class KomiTabCardState {
    internal val search = mutableStateMapOf<String, @Composable () -> Unit>()
    internal val actions = mutableStateMapOf<String, @Composable RowScope.() -> Unit>()
    internal val fab = mutableStateMapOf<String, @Composable () -> Unit>()
    internal var folded by mutableStateOf(false)

    internal val foldConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y < 0f) {
                    folded = true
                } else if (consumed.y > 0f) {
                    folded = false
                }
                return Offset.Zero
            }
        }

    fun slot(tabId: String): KomiTabCardSlot = KomiTabCardSlot(this, tabId)
}

// The tab a screen belongs to, so the card shows the content of the selected tab only.
@Immutable
data class KomiTabCardSlot(
    val state: KomiTabCardState,
    val tabId: String,
)

val LocalTabCardSlot = staticCompositionLocalOf<KomiTabCardSlot?> { null }

// What the folded card leaves of the screen: the search row and its margins, above the
// navigation bar inset. Content is padded by this; the tab row unfolds over it.
object KomiTabCardDefaults {
    val FoldedHeight = 80.dp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KomiTabCard(
    state: KomiTabCardState,
    items: ImmutableList<KomiNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val motion = personality.motion
    val haptics = LocalHapticFeedback.current
    val search = state.search[selectedId]
    val actions = state.actions[selectedId]
    val fab = state.fab[selectedId]
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // The screen's FAB rides above the card's end, like Chocola's shuffle button.
        fab?.let { CompositionLocalProvider(LocalInBar provides true, content = it) }
        Surface(
            shape = RoundedCornerShape(CardCorner),
            color = personality.colors.surfaceContainerHigh,
            shadowElevation = CardElevation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.animateContentSize(motion.bouncySpatial())) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SearchRowHeight).padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AnimatedVisibility(
                        visible = state.folded,
                        enter = scaleIn(motion.bouncySpatial()) + fadeIn(motion.fastEffects()),
                        exit = scaleOut(motion.fastEffects()) + fadeOut(motion.fastEffects()),
                    ) {
                        TabToggle(
                            item = items.firstOrNull { it.id == selectedId },
                            anyBadge = items.any { it.badgeCount > 0 },
                            onClick = { state.folded = false },
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (search != null) search() else SearchPill(onClick = onSearch)
                    }
                    actions?.let { content ->
                        CompositionLocalProvider(LocalInBar provides true) {
                            Row(verticalAlignment = Alignment.CenterVertically, content = content)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !state.folded,
                    enter = expandVertically(motion.bouncySpatial()) + fadeIn(motion.fastEffects()),
                    exit = shrinkVertically(motion.fastEffects()) + fadeOut(motion.fastEffects()),
                ) {
                    ClassicNavRow(
                        items = items,
                        selectedId = selectedId,
                        onSelect = { id ->
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSelect(id)
                        },
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                }
            }
        }
    }
}

// The current tab's icon, standing in for the folded row; a dot when any tab has news.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabToggle(
    item: KomiNavItem?,
    anyBadge: Boolean,
    onClick: () -> Unit,
) {
    if (item == null) return
    KomiBadgedBox(badge = { if (anyBadge) KomiBadge(dot = true) }) {
        FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(imageVector = item.selectedIcon ?: item.icon, contentDescription = stringResource(Res.string.show_tabs))
        }
    }
}

// On a tab without a search of its own, the field opens Search.
@Composable
private fun SearchPill(onClick: () -> Unit) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val container = (personality as? ClassicPersonality)?.colorScheme?.surfaceContainerHighest ?: colors.surfaceContainer
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        modifier = Modifier.fillMaxWidth().height(SearchPillHeight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant)
            KomiText(
                text = stringResource(Res.string.search_repositories_hint),
                role = KomiTextRole.Reading,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// A tab screen's top bar and scaffold hand their search field, actions and FAB to the card.
@Composable
internal fun HandToTabCard(
    slot: KomiTabCardSlot,
    search: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    val currentSearch by rememberUpdatedState(search)
    val currentActions by rememberUpdatedState(actions)
    val hasSearch = search != null
    val hasActions = actions != null
    DisposableEffect(slot, hasSearch, hasActions) {
        if (hasSearch) slot.state.search[slot.tabId] = { currentSearch?.invoke() }
        if (hasActions) slot.state.actions[slot.tabId] = { currentActions?.invoke(this) }
        onDispose {
            slot.state.search.remove(slot.tabId)
            slot.state.actions.remove(slot.tabId)
        }
    }
}

@Composable
internal fun HandFabToTabCard(
    slot: KomiTabCardSlot?,
    fab: (@Composable () -> Unit)?,
) {
    val currentFab by rememberUpdatedState(fab)
    val hasFab = fab != null
    DisposableEffect(slot, hasFab) {
        if (slot != null && hasFab) slot.state.fab[slot.tabId] = { currentFab?.invoke() }
        onDispose { slot?.state?.fab?.remove(slot.tabId) }
    }
}

internal fun Modifier.foldsTabCard(slot: KomiTabCardSlot?): Modifier = if (slot == null) this else nestedScroll(slot.state.foldConnection)

private val CardCorner = 28.dp

private val CardElevation = 6.dp

private val SearchRowHeight = 64.dp

private val SearchPillHeight = 48.dp
