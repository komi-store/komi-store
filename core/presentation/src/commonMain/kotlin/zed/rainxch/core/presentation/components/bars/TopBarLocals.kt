package zed.rainxch.core.presentation.components.bars

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
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

// What a Classic KomiScaffold draws around its content: the top bar, which slides away as the
// content scrolls down and returns on the way up, and a KomiActionBar floating over the content
// with the back action and the actions the top bar hands it, which does the same at the bottom.
// The top bar scrolls only once a bar has attached: until one measures it, the behavior would
// swallow every scroll.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Stable
internal class ScaffoldChrome(
    val behavior: TopAppBarScrollBehavior,
    val actionBarBehavior: FloatingToolbarScrollBehavior,
    val barAttached: MutableState<Boolean>,
    val onBack: MutableState<(() -> Unit)?>,
    val actions: MutableState<(@Composable RowScope.() -> Unit)?>,
) {
    val hasActionBar: Boolean get() = onBack.value != null || actions.value != null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberScaffoldChrome(enabled: Boolean): ScaffoldChrome? {
    val attached = remember { mutableStateOf(false) }
    val onBack = remember { mutableStateOf<(() -> Unit)?>(null) }
    val actions = remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    val canScroll = remember { { attached.value } }
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = canScroll)
    val actionBarBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
    return remember(behavior, actionBarBehavior, enabled) {
        if (enabled) ScaffoldChrome(behavior, actionBarBehavior, attached, onBack, actions) else null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
internal fun Modifier.scrollsChrome(chrome: ScaffoldChrome?): Modifier =
    if (chrome == null) {
        this
    } else {
        nestedScroll(chrome.behavior.nestedScrollConnection).nestedScroll(chrome.actionBarBehavior)
    }
