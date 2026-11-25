package zed.rainxch.search.presentation.components

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridLayoutInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import zed.rainxch.search.presentation.SearchAction
import zed.rainxch.search.presentation.SearchState
import kotlin.time.Duration.Companion.milliseconds

private const val LOAD_MORE_THRESHOLD = 0.8f
private val EmptySpaceLoadDelay = 100.milliseconds

// Asks for the next page near the end of the grid, when the grid doesn't fill the screen, and while Hide seen hides every result.
@Composable
internal fun SearchLoadMoreEffects(
    state: SearchState,
    listState: LazyStaggeredGridState,
    onAction: (SearchAction) -> Unit,
) {
    val currentState by rememberUpdatedState(state)
    val currentOnAction by rememberUpdatedState(onAction)

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            layoutInfo.totalItemsCount > 0 && currentState.canLoadMore && layoutInfo.reachedLoadMoreZone()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentOnAction(SearchAction.LoadMore)
        }
    }

    LaunchedEffect(
        state.repositories.size,
        state.visibleRepos.size,
        state.isHideSeenEnabled,
        state.hasMorePages,
        state.isLoadingMore,
        state.isLoading,
    ) {
        if (state.areResultsHiddenBySeen && state.canLoadMore) {
            currentOnAction(SearchAction.LoadMore)
        }
    }

    LaunchedEffect(listState.layoutInfo.totalItemsCount, listState.layoutInfo.viewportEndOffset) {
        val layoutInfo = listState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.maxByOrNull { it.index } ?: return@LaunchedEffect
        if (state.canLoadMore && layoutInfo.hasEmptySpaceBelow(lastVisible)) {
            delay(EmptySpaceLoadDelay)
            currentOnAction(SearchAction.LoadMore)
        }
    }
}

private fun LazyStaggeredGridLayoutInfo.reachedLoadMoreZone(): Boolean {
    val lastVisible = visibleItemsInfo.maxByOrNull { it.index } ?: return false
    val isNearEnd = lastVisible.index >= (totalItemsCount * LOAD_MORE_THRESHOLD).toInt()
    return isNearEnd || hasEmptySpaceBelow(lastVisible)
}

private fun LazyStaggeredGridLayoutInfo.hasEmptySpaceBelow(lastVisible: LazyStaggeredGridItemInfo): Boolean =
    lastVisible.index == totalItemsCount - 1 &&
        lastVisible.offset.y + lastVisible.size.height < viewportEndOffset
