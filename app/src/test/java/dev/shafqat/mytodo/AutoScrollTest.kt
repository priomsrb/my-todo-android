package dev.shafqat.mytodo

import dev.shafqat.mytodo.ui.todo.autoScrollSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-scroll while dragging.
 *
 * The regression these guard: picking up a row that already sat inside the edge band used to
 * scroll the list with the finger completely still, and because each scroll re-reads which row is
 * under the finger, the dragged item walked away up the list on its own.
 */
class AutoScrollTest {

    private val viewportStart = 0f
    private val viewportEnd = 1000f
    private val edge = 100f

    private fun speedAt(
        pointerY: Float,
        hasMoved: Boolean = true,
        canScrollUp: Boolean = true,
        canScrollDown: Boolean = true,
    ) = autoScrollSpeed(
        pointerY = pointerY,
        viewportStart = viewportStart,
        viewportEnd = viewportEnd,
        edge = edge,
        hasMoved = hasMoved,
        canScrollUp = canScrollUp,
        canScrollDown = canScrollDown,
    )

    @Test
    fun `a stationary finger never scrolls, however close to the edge it is`() {
        assertEquals(0f, speedAt(0f, hasMoved = false), 0f)
        assertEquals(0f, speedAt(10f, hasMoved = false), 0f)
        assertEquals(0f, speedAt(viewportEnd, hasMoved = false), 0f)
    }

    @Test
    fun `the middle of the list never scrolls`() {
        assertEquals(0f, speedAt(500f), 0f)
    }

    @Test
    fun `the band edges are exactly where scrolling starts`() {
        assertEquals(0f, speedAt(viewportStart + edge), 0f)
        assertEquals(0f, speedAt(viewportEnd - edge), 0f)
        assertTrue(speedAt(viewportStart + edge - 1f) < 0f)
        assertTrue(speedAt(viewportEnd - edge + 1f) > 0f)
    }

    @Test
    fun `nearing the top scrolls backward, nearing the bottom scrolls forward`() {
        assertTrue(speedAt(20f) < 0f)
        assertTrue(speedAt(980f) > 0f)
    }

    @Test
    fun `scrolling accelerates toward the edge`() {
        val outer = speedAt(10f)
        val inner = speedAt(90f)

        assertTrue("nearer the edge should be faster", outer < inner)
    }

    @Test
    fun `a list that cannot scroll further stays put`() {
        assertEquals(0f, speedAt(10f, canScrollUp = false), 0f)
        assertEquals(0f, speedAt(990f, canScrollDown = false), 0f)
    }

    @Test
    fun `a finger past the edge does not scroll faster than the cap`() {
        // Overscrolling past the viewport must not produce a runaway speed.
        val atEdge = speedAt(viewportStart)
        val wayPast = speedAt(-5000f)

        assertEquals(atEdge, wayPast, 0f)
    }

    @Test
    fun `a zero-width band disables auto-scroll rather than dividing by zero`() {
        val speed = autoScrollSpeed(
            pointerY = 0f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            edge = 0f,
            hasMoved = true,
            canScrollUp = true,
            canScrollDown = true,
        )

        assertEquals(0f, speed, 0f)
    }
}
