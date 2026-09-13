package dev.shafqat.mytodo

import dev.shafqat.mytodo.ui.todo.floatingOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the dragged row is drawn relative to the slot the preview gave it.
 *
 * The behaviour this pins down: a dragged row follows the finger pixel by pixel, so it never jumps
 * a whole slot at a time, and it never follows the finger off the screen.
 */
class DragOverlayTest {

    private val rowHeight = 168
    private val viewportStart = -24f
    private val viewportEnd = 1000f

    /** Slot n sits directly below slot n-1, first one flush with the top of the content. */
    private fun slot(index: Int) = (index * rowHeight).toFloat()

    private fun offsetAt(pointerY: Float, slotIndex: Int) = floatingOffset(
        pointerY = pointerY,
        slotOffset = slot(slotIndex),
        slotSize = rowHeight,
        viewportStart = viewportStart,
        viewportEnd = viewportEnd,
    )

    @Test
    fun `a finger at the middle of its own slot leaves the row where it is`() {
        assertEquals(0f, offsetAt(slot(2) + rowHeight / 2f, slotIndex = 2), 0f)
    }

    @Test
    fun `the row tracks the finger one pixel at a time`() {
        val center = slot(2) + rowHeight / 2f

        // Well short of a whole slot: the old behaviour drew the row at exactly one of these two
        // slots and nowhere in between.
        assertEquals(1f, offsetAt(center + 1f, slotIndex = 2), 0f)
        assertEquals(17f, offsetAt(center + 17f, slotIndex = 2), 0f)
        assertEquals(-40f, offsetAt(center - 40f, slotIndex = 2), 0f)
    }

    @Test
    fun `crossing into the next slot keeps the row under the finger`() {
        // The moment the target index changes, the row is given the slot below and the finger has
        // moved down by exactly one row. The drawn position has to be continuous across that.
        val center = slot(2) + rowHeight / 2f
        val justBefore = offsetAt(center + rowHeight / 2f - 1f, slotIndex = 2)
        val justAfter = offsetAt(center + rowHeight / 2f + 1f, slotIndex = 3)

        assertEquals(
            "the row must not visibly jump when its slot changes underneath it",
            rowHeight.toFloat(),
            justBefore - justAfter,
            2f,
        )
    }

    @Test
    fun `a finger dragged past the bottom of the list does not take the row off screen`() {
        val offset = offsetAt(viewportEnd + 5000f, slotIndex = 2)
        val drawnBottom = slot(2) + rowHeight + offset

        assertEquals(viewportEnd, drawnBottom, 0.5f)
    }

    @Test
    fun `a finger dragged past the top of the list does not take the row off screen`() {
        val offset = offsetAt(viewportStart - 5000f, slotIndex = 0)
        val drawnTop = slot(0) + offset

        assertEquals(viewportStart, drawnTop, 0.5f)
    }

    @Test
    fun `a viewport shorter than a row still produces an offset rather than throwing`() {
        val offset = floatingOffset(
            pointerY = 500f,
            slotOffset = 0f,
            slotSize = rowHeight,
            viewportStart = 0f,
            viewportEnd = 40f,
        )

        assertTrue(offset.isFinite())
    }
}
