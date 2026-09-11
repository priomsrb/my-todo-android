package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.addItem
import dev.shafqat.mytodo.model.doneCount
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.removeItem
import dev.shafqat.mytodo.model.totalCount
import dev.shafqat.mytodo.model.updateItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoTreeTest {

    private val tree = listOf(
        TodoItem(
            id = "a",
            text = "A",
            children = listOf(
                TodoItem(
                    id = "a1",
                    text = "A1",
                    children = listOf(TodoItem(id = "a1a", text = "A1a", done = true)),
                ),
            ),
        ),
        TodoItem(id = "b", text = "B"),
    )

    @Test
    fun `flattenVisible reports depth for every level`() {
        val rows = tree.flattenVisible()

        assertEquals(listOf("a", "a1", "a1a", "b"), rows.map { it.item.id })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
    }

    @Test
    fun `flattenVisible hides the children of collapsed items`() {
        val collapsed = tree.updateItem("a1") { it.copy(collapsed = true) }

        assertEquals(listOf("a", "a1", "b"), collapsed.flattenVisible().map { it.item.id })
    }

    @Test
    fun `flattenVisible has no depth limit`() {
        // Nesting is unbounded by design, so a deep chain must flatten with increasing depth.
        var deep = TodoItem(id = "leaf", text = "leaf")
        repeat(50) { level -> deep = TodoItem(id = "n$level", text = "n$level", children = listOf(deep)) }

        val rows = listOf(deep).flattenVisible()

        assertEquals(51, rows.size)
        assertEquals(50, rows.last().depth)
    }

    @Test
    fun `updateItem rewrites a nested item and leaves siblings alone`() {
        val updated = tree.updateItem("a1a") { it.copy(done = false) }

        assertEquals(0, updated.doneCount())
        assertEquals(tree.totalCount(), updated.totalCount())
        assertEquals("B", updated.findItem("b")?.text)
    }

    @Test
    fun `removeItem drops the whole subtree`() {
        val pruned = tree.removeItem("a1")

        assertEquals(listOf("a", "b"), pruned.flattenVisible().map { it.item.id })
        assertNull(pruned.findItem("a1a"))
    }

    @Test
    fun `addItem appends under the given parent`() {
        val grown = tree.addItem(TodoItem(id = "new", text = "New"), parentId = "a1")

        assertEquals(listOf("a", "a1", "a1a", "new", "b"), grown.flattenVisible().map { it.item.id })
    }

    @Test
    fun `counts include every descendant`() {
        assertEquals(4, tree.totalCount())
        assertEquals(1, tree.doneCount())
    }
}
