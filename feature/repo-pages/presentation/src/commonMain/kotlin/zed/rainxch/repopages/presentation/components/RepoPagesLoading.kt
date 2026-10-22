package zed.rainxch.repopages.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator

@Composable
fun RepoPagesLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        KomiLoadingIndicator()
    }
}
