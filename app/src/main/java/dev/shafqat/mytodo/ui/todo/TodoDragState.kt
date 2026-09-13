package dev.shafqat.mytodo.ui.todo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/** How long a dropped row takes to slide the last few pixels from the finger into its slot. */
private const val SettleDurationMillis = 150

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
 * How far a dragged row is drawn from the slot it currently occupies, so that it sits under the
 * finger instead of snapping to whichever slot the finger is over.
 *
 * The row is centred on the finger, which is where it was when the drag began, and is held inside
 * the viewport: past the ends of a list that cannot scroll any further the finger keeps going and
 * the row must not follow it off screen. Pure so it can be tested directly.
 */
internal fun floatingOffset(
    pointerY: Float,
    slotOffset: Float,
    slotSize: Int,
    viewportStart: Float,
    viewportEnd: Float,
): Float {
    val half = slotSize / 2f
    val topMost = viewportStart + half
    val bottomMost = viewportEnd - half
    // A viewport shorter than one row leaves no legal range at all; take the ends in whatever
    // order they come out rather than throwing.
    val center = pointerY.coerceIn(minOf(topMost, bottomMost), maxOf(topMost, bottomMost))

    return center - (slotOffset + half)
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
 * On top of that preview the dragged row is drawn *floating*: offset from its slot by
 * [floatingOffsetFor] so it tracks the finger pixel by pixel, while the slot underneath it is the
 * gap it would drop into. Without that the row only ever moved a whole slot at a time.
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

    /**
     * The row that has been let go of and is sliding the last few pixels into its slot.
     *
     * The move is already committed by then; this is only the drop animation, which is why it is
     * separate from [draggedItemId] and never feeds the preview.
     */
    var settlingItemId by mutableStateOf<String?>(null)
        private set

    var targetIndex by mutableIntStateOf(0)
        private set

    var targetDepth by mutableIntStateOf(0)
        private set

    private var pointerY by mutableFloatStateOf(0f)
    private var startDepth = 0
    private var horizontalDrag = 0f
    private var startPointerY = 0f
    private var settleOffset by mutableFloatStateOf(0f)
    private var autoScrollJob: Job? = null
    private var settleJob: Job? = null

    /**
     * Auto-scroll stays off until the finger has actually moved away from where it was put down;
     * see [AutoScrollActivationDistance]. Measured as displacement rather than distance travelled,
     * so a tremor during a long hold never adds up to a move.
     */
    private val hasMoved: Boolean
        get() = kotlin.math.abs(horizontalDrag) + kotlin.math.abs(pointerY - startPointerY) >=
            activationDistancePx

    val isDragging: Boolean get() = draggedItemId != null

    /** The row drawn lifted off the list: the one under the finger, or the one settling after a drop. */
    val floatingItemId: String? get() = draggedItemId ?: settlingItemId

    fun onDragStart(itemId: String, rowIndex: Int, depth: Int) {
        endSettle()

        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == rowIndex }
        pointerY = row?.let { it.offset + it.size / 2f } ?: 0f
        startPointerY = pointerY
        startDepth = depth
        horizontalDrag = 0f

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

        targetDepth = startDepth + (horizontalDrag / indentPx).roundToInt()
        updateTargetIndex()
    }

    /** Ends the drag and hands the caller the move to commit. */
    fun onDragEnd(onCommit: (itemId: String, targetIndex: Int, targetDepth: Int) -> Unit) {
        val itemId = draggedItemId ?: return
        val index = targetIndex
        val depth = targetDepth
        // Where the row was left hanging, measured before the drag state is torn down.
        val released = floatingOffsetFor(itemId)
        stop()

        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // The committed move is the move the preview was already showing, so the row keeps the very
        // slot it floated over and only has to slide the offset away.
        onCommit(itemId, index, depth)
        startSettle(itemId, released)
    }

    fun onDragCancel() = stop()

    /**
     * How far the row for [itemId] should be drawn from its slot, or zero for any row that is
     * neither being dragged nor settling.
     *
     * Read from a `graphicsLayer` block, so the layout it depends on is sampled at draw time.
     */
    fun floatingOffsetFor(itemId: String): Float = when (itemId) {
        draggedItemId -> {
            val info = listState.layoutInfo
            val slot = info.visibleItemsInfo.firstOrNull { it.key == itemId }
            if (slot == null) {
                // Scrolled out from under itself; nothing sensible to offset from.
                0f
            } else {
                floatingOffset(
                    pointerY = pointerY,
                    slotOffset = slot.offset.toFloat(),
                    slotSize = slot.size,
                    viewportStart = info.viewportStartOffset.toFloat(),
                    viewportEnd = info.viewportEndOffset.toFloat(),
                )
            }
        }
        settlingItemId -> settleOffset
        else -> 0f
    }

    private fun stop() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        draggedItemId = null
        horizontalDrag = 0f
    }

    /** Slides a dropped row from where the finger left it down to zero. */
    private fun startSettle(itemId: String, from: Float) {
        endSettle()
        if (from == 0f) return

        settlingItemId = itemId
        settleOffset = from
        settleJob = scope.launch {
            animate(
                initialValue = from,
                targetValue = 0f,
                animationSpec = tween(SettleDurationMillis, easing = FastOutSlowInEasing),
            ) { value, _ -> settleOffset = value }
            settlingItemId = null
            settleOffset = 0f
        }
    }

    /** Cuts a settle short — picking a row up again must not fight its own drop animation. */
    private fun endSettle() {
        settleJob?.cancel()
        settleJob = null
        settlingItemId = null
        settleOffset = 0f
    }

    /**
     * The row under the finger becomes the target. Because the preview puts the dragged row at
     * [targetIndex], the index under the finger is already the index to move to.
     */
    private fun updateTargetIndex() {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        val first = visible.firstOrNull()
        val last = visible.lastOrNull()

        // The dragged row's own slot wins outright. Its neighbours animate into the places it
        // vacates, and a plain hit test against a row that is still sliding can put the target back
        // where it came from, one frame after it left — which reads as the row flickering between
        // two slots.
        val own = visible.firstOrNull { it.key == draggedItemId }
        if (own != null && pointerY >= own.offset && pointerY < own.offset + own.size) return

        val hovered = visible.firstOrNull { item ->
            pointerY >= item.offset && pointerY < item.offset + item.size
        }

        targetIndex = when {
            hovered != null -> hovered.index
            // Above everything on screen — including the list's top padding, which is inside the
            // viewport but above the first row.
            first != null && pointerY < first.offset -> first.index
            // Below everything on screen, which is mostly the padding under the last row.
            last != null && pointerY >= last.offset + last.size -> info.totalItemsCount - 1
            // Between two rows while one of them is still sliding: keep the target we have rather
            // than guessing at an end of the list.
            else -> return
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
