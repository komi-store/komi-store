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
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.badge.KomiBadge
import zed.rainxch.core.presentation.components.badge.KomiBadgedBox
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalAnimatedVisibilityScope
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalSharedTransitionScope
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.search_repositories_hint
import zed.rainxch.githubstore.core.presentation.res.show_tabs

// Classic's tab card, after Chocola: one card at the bottom of the tab screens with the screen's
// search field and actions, and the tab row under them. The row folds behind a toggle while the
// content scrolls down and unfolds on the way up. The app shell owns the tabs and the fold, shared
// by every tab, and gives each tab destination a LocalTabCardSlot. KomiScaffold draws the card in
// the screen's own composition, after its top bar hands over the search field and actions, so the
// field never recomposes with a value its screen has already replaced (that loses typing). A
// shared element keeps the card in place while the tabs slide.
@Stable
class KomiTabCardState {
    internal var folded by mutableStateOf(false)
    internal var items by mutableStateOf<ImmutableList<KomiNavItem>>(persistentListOf())
    internal var onSelect: (String) -> Unit = {}
    internal var onSearch: () -> Unit = {}
    private val parts = HashMap<String, TabCardParts>()

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

    // The app shell's part: the tabs, what picking one does and what the search field opens.
    fun setTabs(
        items: ImmutableList<KomiNavItem>,
        onSelect: (String) -> Unit,
        onSearch: () -> Unit,
    ) {
        this.items = items
        this.onSelect = onSelect
        this.onSearch = onSearch
    }

    internal fun parts(tabId: String): TabCardParts = parts.getOrPut(tabId) { TabCardParts() }
}

// A tab destination's slot: the shared state and the tab the screen belongs to, which its card
// shows as selected.
@Immutable
data class KomiTabCardSlot(
    val state: KomiTabCardState,
    val tabId: String,
)

val LocalTabCardSlot = staticCompositionLocalOf<KomiTabCardSlot?> { null }

// What a tab screen's top bar hands its card, written while composing, ahead of the card that
// reads it further down the same composition.
@Stable
internal class TabCardParts {
    var search by mutableStateOf<(@Composable () -> Unit)?>(null)
    var actions by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
}

@Composable
internal fun HandToTabCard(
    slot: KomiTabCardSlot,
    search: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    val parts = slot.state.parts(slot.tabId)
    parts.search = search
    parts.actions = actions
}

// A tab screen's scaffold runs its top bar here, ahead of the card, instead of in Material's top
// bar slot, which is composed during layout, after the card.
@Composable
internal fun HandTopBarToTabCard(
    slot: KomiTabCardSlot?,
    topBar: (@Composable () -> Unit)?,
) {
    if (slot != null) topBar?.invoke()
}

internal fun Modifier.foldsTabCard(slot: KomiTabCardSlot?): Modifier = if (slot == null) this else nestedScroll(slot.state.foldConnection)

// The room a tab screen's list leaves at its end for the folded card. The list scrolls under the
// card (see withoutBottom); the unfolded tab row covers a little more of it.
@Composable
internal fun tabCardContentInsets(slot: KomiTabCardSlot?): WindowInsets =
    if (slot == null) {
        ScaffoldDefaults.contentWindowInsets
    } else {
        ScaffoldDefaults.contentWindowInsets.add(WindowInsets(bottom = FoldedHeight))
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun KomiTabCard(
    slot: KomiTabCardSlot?,
    fab: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (slot == null) return
    ResizeWindowForKeyboard()
    val state = slot.state
    val parts = state.parts(slot.tabId)
    val search = parts.search
    val actions = parts.actions
    val motion = LocalPersonality.current.motion
    val haptics = LocalHapticFeedback.current
    // While the keyboard is up the card keeps only the field and the actions.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val typing by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // The screen's FAB rides above the card's end, like Chocola's shuffle button.
        if (!typing) {
            fab?.let {
                Box(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    CompositionLocalProvider(LocalInBar provides true, content = it)
                }
            }
        }
        CardSurface(modifier = tabSwitchModifier().widthIn(max = 560.dp).testTag(KomiTestTags.CLASSIC_TAB_CARD)) {
            Column(modifier = Modifier.animateContentSize(motion.bouncySpatial())) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SearchRowHeight).padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AnimatedVisibility(
                        visible = state.folded && !typing,
                        enter = scaleIn(motion.bouncySpatial()) + fadeIn(motion.fastEffects()),
                        exit = scaleOut(motion.fastEffects()) + fadeOut(motion.fastEffects()),
                    ) {
                        TabToggle(
                            item = state.items.firstOrNull { it.id == slot.tabId },
                            anyBadge = state.items.any { it.badgeCount > 0 },
                            onClick = { state.folded = false },
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (search != null) search() else SearchPill(onClick = { state.onSearch() })
                    }
                    actions?.let { content ->
                        CompositionLocalProvider(LocalInBar provides true) {
                            Row(verticalAlignment = Alignment.CenterVertically, content = content)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !state.folded && !typing,
                    enter = expandVertically(motion.bouncySpatial()) + fadeIn(motion.fastEffects()),
                    exit = shrinkVertically(motion.fastEffects()) + fadeOut(motion.fastEffects()),
                ) {
                    ClassicNavRow(
                        items = state.items,
                        selectedId = slot.tabId,
                        onSelect = { id ->
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            state.onSelect(id)
                        },
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(CardCorner),
        color = LocalPersonality.current.colors.surfaceContainerHigh,
        shadowElevation = CardElevation,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

// Each tab screen draws its own card; matched across a tab switch, it stays where it is.
@Composable
private fun tabSwitchModifier(): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    if (sharedScope == null || visibilityScope == null) return Modifier
    return with(sharedScope) {
        Modifier.sharedElement(rememberSharedContentState(key = SHARED_KEY), visibilityScope)
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

private const val SHARED_KEY = "komi-tab-card"

// The search row and the card's margins: what the folded card covers above the navigation bar.
private val FoldedHeight = 80.dp

private val CardCorner = 28.dp

private val CardElevation = 6.dp

private val SearchRowHeight = 64.dp

private val SearchPillHeight = 48.dp
