package zed.rainxch.core.presentation.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

private const val ARROW_STEP_PX = 120f
private const val PAGE_STEP_FRACTION = 0.9f

@Composable
fun Modifier.arrowKeyScroll(
    listState: LazyListState,
    autoFocus: Boolean = false,
): Modifier =
    arrowKeyScrollInternal(
        autoFocus = autoFocus,
        scrollBy = { delta -> listState.animateScrollBy(delta) },
        pageSize = {
            listState.layoutInfo.viewportSize.height
                .toFloat()
        },
        scrollToTop = { listState.animateScrollToItem(0) },
        scrollToBottom = {
            val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.animateScrollToItem(last)

            listState.animateScrollBy(Float.MAX_VALUE)
        },
    )

@Composable
fun Modifier.arrowKeyScroll(
    gridState: LazyStaggeredGridState,
    autoFocus: Boolean = false,
): Modifier =
    arrowKeyScrollInternal(
        autoFocus = autoFocus,
        scrollBy = { delta -> gridState.animateScrollBy(delta) },
        pageSize = {
            gridState.layoutInfo.viewportSize.height
                .toFloat()
        },
        scrollToTop = { gridState.animateScrollToItem(0) },
        scrollToBottom = {
            val last = (gridState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            gridState.animateScrollToItem(last)
            gridState.animateScrollBy(Float.MAX_VALUE)
        },
    )

@Composable
fun Modifier.arrowKeyScroll(
    gridState: LazyGridState,
    autoFocus: Boolean = false,
): Modifier =
    arrowKeyScrollInternal(
        autoFocus = autoFocus,
        scrollBy = { delta -> gridState.animateScrollBy(delta) },
        pageSize = {
            gridState.layoutInfo.viewportSize.height
                .toFloat()
        },
        scrollToTop = { gridState.animateScrollToItem(0) },
        scrollToBottom = {
            val last = (gridState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            gridState.animateScrollToItem(last)
            gridState.animateScrollBy(Float.MAX_VALUE)
        },
    )

@Composable
fun Modifier.arrowKeyScroll(
    scrollState: ScrollState,
    autoFocus: Boolean = false,
): Modifier =
    arrowKeyScrollInternal(
        autoFocus = autoFocus,
        scrollBy = { delta -> scrollState.animateScrollBy(delta) },
        pageSize = { scrollState.viewportSize.toFloat() },
        scrollToTop = { scrollState.animateScrollTo(0) },
        scrollToBottom = { scrollState.animateScrollTo(scrollState.maxValue) },
    )

@Composable
private fun Modifier.arrowKeyScrollInternal(
    autoFocus: Boolean,
    scrollBy: suspend (Float) -> Unit,
    pageSize: () -> Float,
    scrollToTop: suspend () -> Unit,
    scrollToBottom: suspend () -> Unit,
): Modifier {
    val requester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    if (autoFocus) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
        }
    }

    return this
        .focusRequester(requester)
        .focusable()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.DirectionDown -> {
                    scope.launch { scrollBy(ARROW_STEP_PX) }
                    true
                }

                Key.DirectionUp -> {
                    scope.launch { scrollBy(-ARROW_STEP_PX) }
                    true
                }

                Key.PageDown -> {
                    scope.launch { scrollBy(pageSize() * PAGE_STEP_FRACTION) }
                    true
                }

                Key.PageUp -> {
                    scope.launch { scrollBy(-pageSize() * PAGE_STEP_FRACTION) }
                    true
                }

                Key.MoveHome -> {
                    scope.launch { scrollToTop() }
                    true
                }

                Key.MoveEnd -> {
                    scope.launch { scrollToBottom() }
                    true
                }

                else -> {
                    false
                }
            }
        }
}
