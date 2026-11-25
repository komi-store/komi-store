package zed.rainxch.githubstore.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.app.desktop.KeyboardNavigation
import zed.rainxch.githubstore.app.desktop.KeyboardNavigationEvent

@Composable
fun HandleKeyboardEvents(onOpenSearch: () -> Unit) {
    val currentOnOpenSearch by rememberUpdatedState(onOpenSearch)

    ObserveAsEvents(KeyboardNavigation.events) { event ->
        when (event) {
            KeyboardNavigationEvent.OnCtrlFClick -> currentOnOpenSearch()
        }
    }
}
