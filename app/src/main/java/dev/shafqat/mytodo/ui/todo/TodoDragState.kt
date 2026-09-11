package dev.shafqat.mytodo.ui.todo

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How close to an edge the finger must get before the list starts scrolling itself. */
private val AutoScrollEdge = 64.dp

/** Fastest auto-scroll, in pixels per frame, reached at the very edge of the list. */
private const val MaxAutoScrollPerFrame = 18f

/**
 * How far the finger must travel before auto-scroll is allowed to start.
 *
 * Without this, picking up a row that already sits inside the edge band scrolls the list while the
 * finger is perfectly still — and since each scroll re-reads which row is under the finger, the
 * item walks away up the list on its own.
 */
private val AutoScrollActivationDistance = 8.dp

/**
 * Pixels to scroll this frame: zero unless the finger has actually been dragged into the edge band
 * and the list has somewhere left to go in that direction. Pure so it can be tested directly.
 */
internal fun autoScrollSpeed(
    pointerY: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edge: Float,
    hasMoved: Boolean,
    canScrollUp: Boolean,
    canScrollDown: Boolean,
): Float {
    if (!hasMoved || edge <= 0f) return 0f

    val top = viewportStart + edge
    val bottom = viewportEnd - edge

    return when {
        pointerY < top && canScrollUp ->
            -((top - pointerY) / edge).coerceIn(0f, 1f) * MaxAutoScrollPerFrame
        pointerY > bottom && canScrollDown ->
            ((pointerY - bottom) / edge).coerceIn(0f, 1f) * MaxAutoScrollPerFrame
        else -> 0f
    }
}

/**
 * Tracks an in-progress drag of one TODO row.
 *
 * The drag is described entirely by two numbers — the row it should land on ([targetIndex]) and how
 * deep it should nest there ([targetDepth]) — which is exactly what
 * [dev.shafqat.mytodo.model.moveSubtree] consumes. The screen renders a live preview by applying
 * that pending move to the tree, so the row the finger is dragging is always shown where it would
 * actually land, indentation included.
 *
 * Vertical position is tracked as a pointer position in *viewport* coordinates rather than as an
 * offset from the row's start, so auto-scrolling (which moves content under a stationary finger)
 * stays correct.
 */
class TodoDragState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val indentPx: Float,
    private val autoScrollEdgePx: Float,
    private val activationDistancePx: Float,
) {

    var draggedItemId by mutableStateOf<String?>(null)
        private set

    var targetIndex by mutableIntStateOf(0)
        private set

    var targetDepth by mutableIntStateOf(0)
        private set

    private var pointerY = 0f
    private var startDepth = 0
    private var horizontalDrag = 0f
    private var travelled = 0f
    private var autoScrollJob: Job? = null

    /** Auto-scroll stays off until the finger has actually moved; see [AutoScrollActivationDistance]. */
    private val hasMoved: Boolean get() = travelled >= activationDistancePx

    val isDragging: Boolean get() = draggedItemId != null

    fun onDragStart(itemId: String, rowIndex: Int, depth: Int) {
        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == rowIndex }
        pointerY = row?.let { it.offset + it.size / 2f } ?: 0f
        startDepth = depth
        horizontalDrag = 0f
        travelled = 0f

        draggedItemId = itemId
        targetIndex = rowIndex
        targetDepth = depth

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        startAutoScroll()
    }

    fun onDrag(deltaX: Float, deltaY: Float) {
        if (!isDragging) return

        pointerY += deltaY
        horizontalDrag += deltaX
        travelled += kotlin.math.abs(deltaX) + kotlin.math.abs(deltaY)

        targetDepth = startDepth + (horizontalDrag / indentPx).roundToInt()
        updateTargetIndex()
    }

    /** Ends the drag and hands the caller the move to commit. */
    fun onDragEnd(onCommit: (itemId: String, targetIndex: Int, targetDepth: Int) -> Unit) {
        val itemId = draggedItemId ?: return
        val index = targetIndex
        val depth = targetDepth
        stop()

        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onCommit(itemId, index, depth)
    }

    fun onDragCancel() = stop()

    private fun stop() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        draggedItemId = null
        horizontalDrag = 0f
        travelled = 0f
    }

    /**
     * The row under the finger becomes the target. Because the preview puts the dragged row at
     * [targetIndex], the index under the finger is already the index to move to.
     */
    private fun updateTargetIndex() {
        val info = listState.layoutInfo
        val hovered = info.visibleItemsInfo.firstOrNull { item ->
            pointerY >= item.offset && pointerY < item.offset + item.size
        }

        targetIndex = when {
            hovered != null -> hovered.index
            // Past the ends of what is on screen, aim for the nearest end of the list.
            pointerY < info.viewportStartOffset -> info.visibleItemsInfo.firstOrNull()?.index ?: 0
            else -> info.totalItemsCount - 1
        }.coerceAtLeast(0)
    }

    /** Scrolls the list while the finger rests near an edge, accelerating closer to it. */
    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (isActive) {
                withFrameNanos { }
                val info = listState.layoutInfo

                val speed = autoScrollSpeed(
                    pointerY = pointerY,
                    viewportStart = info.viewportStartOffset.toFloat(),
                    viewportEnd = info.viewportEndOffset.toFloat(),
                    edge = autoScrollEdgePx,
                    hasMoved = hasMoved,
                    canScrollUp = listState.canScrollBackward,
                    canScrollDown = listState.canScrollForward,
                )

                if (speed != 0f) {
                    listState.scrollBy(speed)
                    updateTargetIndex()
                }
            }
        }
    }
}

@Composable
fun rememberTodoDragState(listState: LazyListState): TodoDragState {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val indentPx = with(density) { IndentPerLevel.toPx() }
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val activationPx = with(density) { AutoScrollActivationDistance.toPx() }

    return remember(listState, indentPx, edgePx, activationPx) {
        TodoDragState(listState, scope, haptics, indentPx, edgePx, activationPx)
    }
}
