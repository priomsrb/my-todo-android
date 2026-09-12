package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.indentItem
import dev.shafqat.mytodo.model.insertSubtree
import dev.shafqat.mytodo.model.outdentItem
import dev.shafqat.mytodo.model.removeItem
import dev.shafqat.mytodo.model.totalCount
import dev.shafqat.mytodo.model.visibleRowOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The keyboard-driven edits: Tab, Shift-Tab, and putting a deleted subtree back.
 *
 * All three are expressed in terms of the same move maths the drag uses, so these tests are as much
 * about the coordinates agreeing as about the edits themselves.
 */
class OutlineEditTest {

    //  A
    //    A1
    //      A1a
    //    A2
    //  B
    //  C
    private val tree = listOf(
        TodoItem(
            id = "A", text = "A",
            children = listOf(
                TodoItem(id = "A1", text = "A1", children = listOf(TodoItem(id = "A1a", text = "A1a"))),
                TodoItem(id = "A2", text = "A2"),
            ),
        ),
        TodoItem(id = "B", text = "B"),
        TodoItem(id = "C", text = "C"),
    )

    private fun List<TodoItem>.outline(): String =
        flattenVisible().joinToString("\n") { "  ".repeat(it.depth) + it.item.id }

    // --- indent -----------------------------------------------------------------------------

    @Test
    fun `indenting nests an item under the sibling above it`() {
        assertEquals(
            """
            A
              A1
                A1a
              A2
              B
            C
            """.trimIndent(),
            tree.indentItem("B").outline(),
        )
    }

    @Test
    fun `indenting carries the whole subtree`() {
        assertEquals(
            """
            A
              A1
                A1a
                A2
            B
            C
            """.trimIndent(),
            tree.indentItem("A2").outline(),
        )
    }

    @Test
    fun `the first item of a level has nothing to nest under`() {
        assertEquals(tree.outline(), tree.indentItem("A").outline())
        assertEquals(tree.outline(), tree.indentItem("A1").outline())
    }

    @Test
    fun `indenting into a collapsed sibling is refused, since the item would vanish`() {
        val collapsed = tree.map { if (it.id == "A") it.copy(collapsed = true) else it }

        assertEquals(collapsed.outline(), collapsed.indentItem("B").outline())
    }

    // --- outdent ----------------------------------------------------------------------------

    @Test
    fun `outdenting lifts an item out of its parent`() {
        assertEquals(
            """
            A
              A1
                A1a
            A2
            B
            C
            """.trimIndent(),
            tree.outdentItem("A2").outline(),
        )
    }

    @Test
    fun `an outdented item lands after the siblings that followed it, which keep their parent`() {
        assertEquals(
            """
            A
              A2
            A1
              A1a
            B
            C
            """.trimIndent(),
            tree.outdentItem("A1").outline(),
        )
    }

    @Test
    fun `a top-level item cannot outdent`() {
        assertEquals(tree.outline(), tree.outdentItem("B").outline())
    }

    @Test
    fun `indent then outdent returns an item to where it started`() {
        assertEquals(tree.outline(), tree.indentItem("B").outdentItem("B").outline())
    }

    // --- insert -----------------------------------------------------------------------------

    @Test
    fun `deleting and re-inserting at the same position restores the tree exactly`() {
        listOf("A", "A1", "A1a", "A2", "B", "C").forEach { id ->
            val row = requireNotNull(tree.visibleRowOf(id))
            val subtree = requireNotNull(tree.find { it.id == id } ?: tree.deepFind(id))

            val restored = tree.removeItem(id).insertSubtree(subtree, row.index, row.depth)

            assertEquals("restoring $id", tree.outline(), restored.outline())
        }
    }

    @Test
    fun `inserting after an expanded item makes the new row its first child`() {
        // The row after an expanded item *is* its first child, so the same depth lands there.
        val row = requireNotNull(tree.visibleRowOf("A"))
        val added = tree.insertSubtree(TodoItem(id = "N", text = "N"), row.index + 1, row.depth)

        assertEquals(
            """
            A
              N
              A1
                A1a
              A2
            B
            C
            """.trimIndent(),
            added.outline(),
        )
        assertEquals(tree.totalCount() + 1, added.totalCount())
    }

    @Test
    fun `inserting after a childless item makes it a sibling`() {
        val row = requireNotNull(tree.visibleRowOf("B"))
        val added = tree.insertSubtree(TodoItem(id = "N", text = "N"), row.index + 1, row.depth)

        assertEquals(
            """
            A
              A1
                A1a
              A2
            B
            N
            C
            """.trimIndent(),
            added.outline(),
        )
    }

    private fun List<TodoItem>.deepFind(id: String): TodoItem? {
        forEach { item ->
            if (item.id == id) return item
            item.children.deepFind(id)?.let { return it }
        }
        return null
    }
}
