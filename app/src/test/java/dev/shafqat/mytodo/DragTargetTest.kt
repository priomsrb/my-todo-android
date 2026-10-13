package dev.shafqat.mytodo

import dev.shafqat.mytodo.ui.todo.RowSlot
import dev.shafqat.mytodo.ui.todo.dragTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which slot a dragged row aims at, given the rows laid out around it and the finger.
 *
 * The behaviours this pins down: a row swaps with a neighbour only once the finger passes that
 * neighbour's midpoint, a swap never undoes itself on the next frame whatever the rows' heights,
 * and a finger past either end of the rows on screen never sends the row anywhere off screen.
 */
class DragTargetTest {

    /**
     * Rows [first] until [first] + [heights].size laid out top to bottom from offset 0, with the
     * row at [dragged] (an index, not a position) being the one under the finger.
     */
    private fun layout(heights: List<Int>, dragged: Int?, first: Int = 0): List<RowSlot> {
        var offset = 0
        return heights.mapIndexed { i, height ->
            val index = first + i
            RowSlot(index, offset, height, isDragged = index == dragged).also { offset += height }
        }
    }

    private val even = layout(List(10) { 100 }, dragged = 4)

    @Test
    fun `a finger inside its own slot keeps the target`() {
        listOf(400f, 450f, 499f).forEach { assertEquals(4, dragTarget(even, 4, it)) }
    }

    @Test
    fun `the row swaps with a neighbour only past that neighbour's midpoint`() {
        assertEquals(4, dragTarget(even, 4, 351f))
        assertEquals(3, dragTarget(even, 4, 349f))
        assertEquals(4, dragTarget(even, 4, 549f))
        assertEquals(5, dragTarget(even, 4, 551f))
    }

    @Test
    fun `a fast finger crosses several rows at once`() {
        assertEquals(1, dragTarget(even, 4, 120f))
        assertEquals(8, dragTarget(even, 4, 880f))
    }

    @Test
    fun `a finger past either end of the laid-out rows stops at the end row`() {
        // The regression: a finger below the last row on screen used to aim at the end of the
        // whole list, far off screen, where the row was disposed and the drag lost.
        assertEquals(0, dragTarget(even, 4, -500f))
        assertEquals(9, dragTarget(even, 4, 5000f))

        val scrolled = layout(List(10) { 100 }, dragged = 24, first = 20)
        assertEquals(20, dragTarget(scrolled, 24, -500f))
        assertEquals(29, dragTarget(scrolled, 24, 5000f))
    }

    @Test
    fun `a swap between rows of different heights does not swap straight back`() {
        // A short dragged row under a tall one, and a tall dragged row under a short one: after
        // each swap the finger must still be on the near side of the displaced row's midpoint.
        listOf(listOf(300, 50), listOf(50, 300)).forEach { (above, dragged) ->
            val before = layout(listOf(above, dragged), dragged = 1)
            val finger = above / 2f - 1f
            val target = dragTarget(before, 1, finger)
            assertEquals(0, target)

            val after = layout(listOf(dragged, above), dragged = 0)
            assertEquals("heights $above/$dragged", 0, dragTarget(after, 0, finger))
        }
    }

    @Test
    fun `a layout that has not caught up with the last move is not walked`() {
        // The target says 3, but the layout still has the dragged row at 4: its positions describe
        // the list before the move, and walking them would move the row twice.
        assertEquals(3, dragTarget(even, 3, 120f))
    }

    @Test
    fun `a dragged row scrolled out of the window comes back into it`() {
        // Dragged row below everything laid out: indices are already the ones the target uses.
        val above = layout(List(5) { 100 }, dragged = null, first = 10)
        assertEquals(12, dragTarget(above, current = 30, pointerY = 230f))
        assertEquals(15, dragTarget(above, current = 30, pointerY = 9000f))

        // Dragged row above everything laid out: every row on screen sits one index further on
        // than it will once the dragged row is lifted out.
        val below = layout(List(5) { 100 }, dragged = null, first = 10)
        assertEquals(11, dragTarget(below, current = 2, pointerY = 230f))
        assertEquals(9, dragTarget(below, current = 2, pointerY = -50f))
    }

    @Test
    fun `nothing laid out keeps the target`() {
        assertEquals(7, dragTarget(emptyList(), 7, 100f))
    }
}
