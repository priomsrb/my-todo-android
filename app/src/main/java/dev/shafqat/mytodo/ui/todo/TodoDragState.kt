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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How close to an edge the finger must get before the list starts scrolling itself. */
private val AutoScrollEdge = 64.dp

/**
 * Fastest auto-scroll, in pixels per 60Hz frame, reached at the very edge of the list. The scroll
 * loop scales it by how long each frame actually took, so a 120Hz screen scrolls no faster.
 */
private const val MaxAutoScrollPerFrame = 18f

/** One frame at 60Hz, the rate [MaxAutoScrollPerFrame] is expressed in. */
private const val ReferenceFrameNanos = 16_666_667f

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

/** One laid-out row, as [dragTarget] needs it: its index among the rows and where it sits. */
internal data class RowSlot(val index: Int, val offset: Int, val size: Int, val isDragged: Boolean)

/**
 * Where the dragged row should go, given the rows currently laid out ([slots], top to bottom), the
 * index it is at now ([current]) and where the finger is ([pointerY]).
 *
 * The row trades places with a neighbour once the finger passes that neighbour's midpoint — the
 * rule `ItemTouchHelper` uses. Measured against laid-out positions rather than whatever is drawn
 * mid-animation, a swap can never undo itself on the next frame: after it, the finger sits on the
 * near side of the displaced row's new midpoint whatever the two rows' heights.
 *
 * The target only ever walks across rows that are laid out. A finger past either end of them —
 * over the status bar, the navigation bar, the list's padding — leaves the row at the last one it
 * reached, and auto-scroll brings more in. Pure so it can be tested directly.
 */
internal fun dragTarget(slots: List<RowSlot>, current: Int, pointerY: Float): Int {
    if (slots.isEmpty()) return current
    val own = slots.indexOfFirst { it.isDragged }

    if (own == -1) {
        // The dragged row has been scrolled out of the laid-out window. Put it back into it, in
        // front of the first row whose midpoint is below the finger. Rows below the dragged one
        // sit one index further on than they will once it is lifted out, which is the coordinate
        // space the target is in, so a dragged row above the window shifts every index by one.
        val shift = if (current < slots.first().index) 1 else 0
        val before = slots.firstOrNull { pointerY < it.offset + it.size / 2f }
        val index = before?.index ?: (slots.last().index + 1)
        return (index - shift).coerceAtLeast(0)
    }

    // The layout has not caught up with the last move yet; its positions describe a list the
    // target has already left, and walking them would move the row twice.
    if (slots[own].index != current) return current

    var target = current
    for (i in own - 1 downTo 0) {
        val above = slots[i]
        if (pointerY < above.offset + above.size / 2f) target = above.index else break
    }
    if (target != current) return target
    for (i in own + 1 until slots.size) {
        val below = slots[i]
        // A row below the dragged one is at its own index plus one in the list the dragged row has
        // been lifted out of, so its index here is exactly the target that drops in after it.
        if (pointerY > below.offset + below.size / 2f) target = below.index else break
    }
    return target
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
 * The gesture belongs to the *list*, not to the row. A row's handle only notices the drag begin
 * and hands the pointer over ([onDragStart]); from then on the list's own pointer input feeds every
 * movement to [onPointerEvent] until the finger lifts. A lazy list disposes rows it has no room
 * for, and while the gesture lived on the handle, a row pushed out of the laid-out window took its
 * drag with it — the item snapped back to where it started, mid-drag.
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

    /**
     * The depths a dragged row may take at a given target index. Supplied by the list, which has
     * the tree; used to keep sideways overshoot from piling up (see [onDrag]).
     */
    var depthRange: (itemId: String, targetIndex: Int) -> IntRange = { _, _ -> 0..Int.MAX_VALUE }

    /** The pointer the drag follows; any other finger on the screen is ignored. */
    private var pointerId: PointerId? = null
    private var pointerY by mutableFloatStateOf(0f)
    private var startPointerY = 0f

    /**
     * The depth the finger is asking for, in levels, before rounding. Sideways movement adds to it;
     * see [onDrag] for why it is clamped as it goes rather than at the end.
     */
    private var depth = 0f
    private var horizontalDrag = 0f
    private var settleOffset by mutableFloatStateOf(0f)
    private var autoScrollJob: Job? = null
    private var settleJob: Job? = null

    /**
     * Auto-scroll stays off until the finger has actually moved away from where it was put down;
     * see [AutoScrollActivationDistance]. Measured as displacement rather than distance travelled,
     * so a tremor during a long hold never adds up to a move.
     */
    private val hasMoved: Boolean
        get() = abs(horizontalDrag) + abs(pointerY - startPointerY) >= activationDistancePx

    val isDragging: Boolean get() = draggedItemId != null

    /** The row drawn lifted off the list: the one under the finger, or the one settling after a drop. */
    val floatingItemId: String? get() = draggedItemId ?: settlingItemId

    /**
     * A row's handle has seen [pointer] move past touch slop. The drag follows that pointer from
     * here on, wherever it goes, and [overSlop] is the movement the slop check already used up.
     */
    fun onDragStart(itemId: String, rowIndex: Int, depth: Int, pointer: PointerId, overSlop: Offset) {
        endSettle()

        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == rowIndex }
        pointerY = row?.let { it.offset + it.size / 2f } ?: 0f
        startPointerY = pointerY
        this.depth = depth.toFloat()
        horizontalDrag = 0f
        pointerId = pointer

        draggedItemId = itemId
        targetIndex = rowIndex
        targetDepth = depth

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onDrag(overSlop.x, overSlop.y)
        startAutoScroll()
    }

    /**
     * Every pointer event the list sees, on the initial pass, so a drag in progress can claim its
     * pointer's movement before anything else — the list's own scrolling, swipe-to-delete — reads
     * it. The drag ends when its pointer lifts, or when the gesture is cancelled, which Compose
     * reports as that pointer going up; either way the row stays where the preview has it.
     */
    fun onPointerEvent(
        changes: List<PointerInputChange>,
        onCommit: (itemId: String, targetIndex: Int, targetDepth: Int) -> Unit,
    ) {
        if (!isDragging) return
        val change = changes.firstOrNull { it.id == pointerId }
        if (change == null || !change.pressed) {
            change?.consume()
            onDragEnd(onCommit)
            return
        }
        val delta = change.positionChangeIgnoreConsumed()
        change.consume()
        onDrag(delta.x, delta.y)
    }

    /**
     * Moves the drag by a finger movement.
     *
     * Sideways travel adjusts the requested depth, and that is clamped to what the current slot
     * allows *as it accumulates*. Clamped only at the end, dragging four levels right where one
     * was allowed meant dragging three levels back before the row would come out again. Vertical
     * travel leaves the request alone, so a depth the finger asked for survives crossing rows
     * that cannot take it, and comes back where it can.
     */
    fun onDrag(deltaX: Float, deltaY: Float) {
        pointerY += deltaY
        horizontalDrag += deltaX

        val itemId = draggedItemId ?: return
        if (deltaX != 0f) {
            val range = depthRange(itemId, targetIndex)
            depth = (depth + deltaX / indentPx)
                .coerceIn(range.first - DepthSlack, range.last + DepthSlack)
        }
        targetDepth = depth.roundToInt()
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
        pointerId = null
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
     * Re-aims the drag at the slot under the finger; see [dragTarget] for the rule.
     *
     * Moving the dragged row also pins the scroll position to the first visible *index*. A lazy
     * list otherwise holds on to the *key* of its first visible row, so moving the dragged row
     * into or out of the top slot scrolled the list to chase whichever row had been there — off
     * by a row each time, and taking the dragged row off the top of the screen.
     */
    private fun updateTargetIndex() {
        val slots = listState.layoutInfo.visibleItemsInfo.map {
            RowSlot(it.index, it.offset, it.size, isDragged = it.key == draggedItemId)
        }
        val target = dragTarget(slots, targetIndex, pointerY)
        if (target == targetIndex) return

        listState.requestScrollToItem(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
        )
        targetIndex = target
    }

    /** Scrolls the list while the finger rests near an edge, accelerating closer to it. */
    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            var lastFrame = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                // Scaled by the frame's real length, so the list scrolls at the same speed on a
                // 60Hz and a 120Hz screen. The first frame has nothing to measure against.
                val frameScale = if (lastFrame == 0L) {
                    1f
                } else {
                    ((frame - lastFrame) / ReferenceFrameNanos).coerceIn(0f, MaxFrameScale)
                }
                lastFrame = frame
                val info = listState.layoutInfo

                val speed = autoScrollSpeed(
                    pointerY = pointerY,
                    viewportStart = info.viewportStartOffset.toFloat(),
                    viewportEnd = info.viewportEndOffset.toFloat(),
                    edge = autoScrollEdgePx,
                    hasMoved = hasMoved,
                    canScrollUp = listState.canScrollBackward,
                    canScrollDown = listState.canScrollForward,
                ) * frameScale

                if (speed != 0f) {
                    try {
                        listState.scrollBy(speed)
                    } catch (e: CancellationException) {
                        // Another scroll took over the list for a moment — pinning the scroll
                        // position does that. Only a cancelled drag ends this loop.
                        if (!isActive) throw e
                    }
                    updateTargetIndex()
                }
            }
        }
    }
}

/**
 * How far past the deepest (or shallowest) allowed level a sideways drag may wander before it is
 * held. Just under half a level, so a finger at the limit still rounds to the limit while the first
 * movement back the other way reaches the next level down within half a step.
 */
private const val DepthSlack = 0.49f

/** A stalled frame scrolls at most this many frames' worth, rather than lurching. */
private const val MaxFrameScale = 3f

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
