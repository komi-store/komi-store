package zed.rainxch.core.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ScrollbarContainer(
    listState: LazyListState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Composable
expect fun ScrollbarContainer(
    gridState: LazyStaggeredGridState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Composable
expect fun ScrollbarContainer(
    gridState: LazyGridState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
