package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.widget.RecentlyTicked
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How long a widget keeps a just-ticked item in view, and what lets it go early. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyTickedTest {

    private var released = 0

    private fun TestScope.ticked() = RecentlyTicked(backgroundScope, holdMillis = 4_000) { released++ }

    @Test
    fun `a ticked item is held, then let go after the hold, with a redraw`() = runTest {
        val held = ticked()

        held.ticked("a", done = true)
        advanceTimeBy(3_999)
        assertEquals("a", held.key.value)

        advanceTimeBy(2)
        assertNull(held.key.value)
        assertEquals(1, released)
    }

    @Test
    fun `ticking another item hands the hold over, and restarts the clock`() = runTest {
        val held = ticked()

        held.ticked("a", done = true)
        advanceTimeBy(3_000)
        held.ticked("b", done = true)
        advanceTimeBy(3_000)

        assertEquals("b", held.key.value)
        assertEquals(0, released)

        advanceTimeBy(1_001)
        assertNull(held.key.value)
        assertEquals(1, released)
    }

    @Test
    fun `unticking the held item lets it go at once`() = runTest {
        val held = ticked()

        held.ticked("a", done = true)
        held.ticked("a", done = false)
        assertNull(held.key.value)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, released)
    }

    @Test
    fun `unticking some other item leaves the hold alone`() = runTest {
        val held = ticked()

        held.ticked("a", done = true)
        held.ticked("b", done = false)

        assertEquals("a", held.key.value)
    }

    @Test
    fun `the subtree as it was is handed back only while its item is held`() = runTest {
        val held = ticked()
        val before = TodoItem(id = "a", text = "A", children = listOf(TodoItem(text = "A1", done = true)))

        held.ticked("a", done = true, before = before)
        assertEquals(before, held.before("a"))
        assertNull(held.before("b"))

        advanceTimeBy(4_001)
        assertNull(held.before("a"))
    }

    @Test
    fun `ticking something else drops the earlier snapshot`() = runTest {
        val held = ticked()

        held.ticked("a", done = true, before = TodoItem(id = "a", text = "A"))
        held.ticked("b", done = true)
        held.ticked("a", done = true)

        assertNull(held.before("a"))
    }
}
