package zed.rainxch.core.presentation.components.bars

import androidx.compose.foundation.layout.RowScope
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

// Classic bars hold Material's standard icon buttons, so KomiIconButton drops its fill there.
internal val LocalInBar = staticCompositionLocalOf { false }

internal val LocalScaffoldChrome = staticCompositionLocalOf<ScaffoldChrome?> { null }

// What a Classic KomiScaffold draws around its top bar: it collapses a masthead as the content
// scrolls, and draws the back action and the actions the top bar hands it in a KomiActionBar at the
// bottom. It scrolls the bar only once a masthead has attached: until a bar measures it, the
// behavior would swallow every upward scroll.
@OptIn(ExperimentalMaterial3Api::class)
@Stable
internal class ScaffoldChrome(
    val behavior: TopAppBarScrollBehavior,
    val mastheadAttached: MutableState<Boolean>,
    val onBack: MutableState<(() -> Unit)?>,
    val actions: MutableState<(@Composable RowScope.() -> Unit)?>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberScaffoldChrome(enabled: Boolean): ScaffoldChrome? {
    val attached = remember { mutableStateOf(false) }
    val onBack = remember { mutableStateOf<(() -> Unit)?>(null) }
    val actions = remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    val canScroll = remember { { attached.value } }
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = canScroll)
    return remember(behavior, enabled) {
        if (enabled) ScaffoldChrome(behavior, attached, onBack, actions) else null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.collapsesTopBar(chrome: ScaffoldChrome?): Modifier =
    if (chrome == null) this else nestedScroll(chrome.behavior.nestedScrollConnection)
