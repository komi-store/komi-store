// Suppressed rather than allowlisted: compose-rules lowercases compose_allowed_composition_locals
// and then compares names case-sensitively, so no Local* name can match that list.
@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package zed.rainxch.core.presentation.components.bars

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

// Classic top bars hold Material's standard icon buttons, so KomiIconButton drops its fill there.
internal val LocalInTopBar = staticCompositionLocalOf { false }

internal val LocalTopBarScroll = staticCompositionLocalOf<TopBarScroll?> { null }

// The scaffold collapses a Classic masthead as its content scrolls. It scrolls the bar only once a
// masthead has attached: until a bar measures it, the behavior would swallow every upward scroll.
@OptIn(ExperimentalMaterial3Api::class)
@Stable
internal class TopBarScroll(
    val behavior: TopAppBarScrollBehavior,
    val attached: MutableState<Boolean>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberTopBarScroll(enabled: Boolean): TopBarScroll? {
    val attached = remember { mutableStateOf(false) }
    val canScroll = remember { { attached.value } }
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = canScroll)
    return remember(behavior, enabled) { if (enabled) TopBarScroll(behavior, attached) else null }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.collapsesTopBar(scroll: TopBarScroll?): Modifier =
    if (scroll == null) this else nestedScroll(scroll.behavior.nestedScrollConnection)
