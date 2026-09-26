package dev.shafqat.mytodo

import dev.shafqat.mytodo.ui.todo.revealOverflow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keeping the row being edited whole above the toolbar and the keyboard.
 *
 * The regression behind it: the text field only scrolled its caret's line into view, so a row
 * edited near the bottom was left with its checkbox cut off behind the toolbar.
 */
class RevealEditedRowTest {

    private fun overflow(rowStart: Int, rowEnd: Int) =
        revealOverflow(rowStart, rowEnd, viewportStart = 0, viewportEnd = 1000)

    @Test
    fun `a row that fits is left where it is`() {
        assertEquals(0, overflow(rowStart = 400, rowEnd = 500))
        assertEquals(0, overflow(rowStart = 900, rowEnd = 1000))
    }

    @Test
    fun `a row running off the bottom is scrolled up by exactly what is hidden`() {
        assertEquals(40, overflow(rowStart = 914, rowEnd = 1040))
    }

    @Test
    fun `a row entirely below the viewport is brought up to its bottom edge`() {
        assertEquals(226, overflow(rowStart = 1100, rowEnd = 1226))
    }

    @Test
    fun `a row taller than the viewport keeps its top in view`() {
        assertEquals(200, overflow(rowStart = 200, rowEnd = 1500))
    }

    @Test
    fun `a row already partly off the top is never scrolled further up`() {
        assertEquals(0, overflow(rowStart = -50, rowEnd = 1100))
    }
}
