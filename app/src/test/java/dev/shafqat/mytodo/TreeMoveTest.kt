package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.allowedDepthRange
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.flattenForMove
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.rebuildTree
import dev.shafqat.mytodo.model.removeItem
import dev.shafqat.mytodo.model.totalCount
import dev.shafqat.mytodo.model.visibleIndexOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeMoveTest {

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

    /** Renders a tree as indented ids, which makes a failed assertion readable. */
    private fun List<TodoItem>.outline(): String =
        flattenVisible().joinToString("\n") { "  ".repeat(it.depth) + it.item.id }

    // --- flatten / rebuild ------------------------------------------------------------------

    @Test
    fun `flatten then rebuild is the identity`() {
        assertEquals(tree.outline(), tree.flattenForMove().rebuildTree().outline())
    }

    @Test
    fun `a collapsed subtree flattens to a single row that keeps its children`() {
        val collapsed = tree.map { if (it.id == "A") it.copy(collapsed = true) else it }

        val rows = collapsed.flattenForMove()

        assertEquals(listOf("A", "B", "C"), rows.map { it.item.id })
        assertEquals(2, rows.first().item.children.size)
        assertEquals(collapsed.outline(), rows.rebuildTree().outline())
    }

    // --- legal depths -----------------------------------------------------------------------

    @Test
    fun `an item may go at most one level deeper than the row above it`() {
        val rows = tree.removeAnd("C").flattenForMove()
        // Rows are A, A1, A1a, A2, B — dropping right after A1a (index 3).
        assertEquals(3, allowedDepthRange(rows, 3).last)
    }

    @Test
    fun `an item may not go shallower than the row below it`() {
        val rows = tree.removeAnd("B").flattenForMove()
        // Dropping between A1 and A1a: A1a would be orphaned by anything shallower than depth 2.
        assertEquals(2, allowedDepthRange(rows, 2).first)
    }

    @Test
    fun `the first position is always top level`() {
        assertEquals(0..0, allowedDepthRange(tree.flattenForMove(), 0))
    }

    @Test
    fun `nothing may be dropped into a collapsed parent`() {
        val collapsed = listOf(
            TodoItem(id = "P", text = "P", collapsed = true, children = listOf(TodoItem(id = "c", text = "c"))),
        )

        // Right after P, depth 1 would put the item inside a collapsed parent, out of sight.
        assertEquals(0..0, allowedDepthRange(collapsed.flattenForMove(), 1))
    }

    // --- moving -----------------------------------------------------------------------------

    @Test
    fun `moving an item to the end reorders it`() {
        val moved = tree.moveSubtree("B", targetIndex = 5, targetDepth = 0)

        assertEquals("A\n  A1\n    A1a\n  A2\nC\nB", moved.outline())
    }

    @Test
    fun `moving a parent takes its whole subtree along`() {
        val moved = tree.moveSubtree("A", targetIndex = 2, targetDepth = 0)

        assertEquals("B\nC\nA\n  A1\n    A1a\n  A2", moved.outline())
        assertEquals(tree.totalCount(), moved.totalCount())
    }

    @Test
    fun `dropping one level deeper nests an item under the row above`() {
        // C dropped just after B, one level in, becomes B's child.
        val moved = tree.moveSubtree("C", targetIndex = 5, targetDepth = 1)

        assertEquals("A\n  A1\n    A1a\n  A2\nB\n  C", moved.outline())
    }

    @Test
    fun `dragging out to a shallower depth outdents`() {
        // After A2, nothing below needs A1a as a parent, so it may come all the way out.
        val moved = tree.moveSubtree("A1a", targetIndex = 3, targetDepth = 0)

        assertEquals("A\n  A1\n  A2\nA1a\nB\nC", moved.outline())
    }

    @Test
    fun `outdenting is refused where it would re-parent the row below`() {
        // Between A1 and A2: dropping A1a at depth 0 would swallow A2, so it clamps to depth 1.
        val moved = tree.moveSubtree("A1a", targetIndex = 2, targetDepth = 0)

        assertEquals("A\n  A1\n  A1a\n  A2\nB\nC", moved.outline())
    }

    @Test
    fun `a subtree moved under another parent keeps its own children`() {
        val moved = tree.moveSubtree("A1", targetIndex = 2, targetDepth = 1)

        assertEquals("A\n  A2\n  A1\n    A1a\nB\nC", moved.outline())
        assertEquals(1, moved.findItem("A1")?.children?.size)
    }

    @Test
    fun `too deep a drop is clamped instead of rejected`() {
        // The finger says depth 9; only depth 1 is legal directly under B.
        val moved = tree.moveSubtree("C", targetIndex = 5, targetDepth = 9)

        assertEquals("A\n  A1\n    A1a\n  A2\nB\n  C", moved.outline())
    }

    @Test
    fun `a negative depth is clamped to the shallowest legal depth`() {
        val moved = tree.moveSubtree("A2", targetIndex = 2, targetDepth = -5)

        // Between A1 and A1a, depth 2 is the shallowest that leaves A1a parented.
        assertEquals("A\n  A1\n    A2\n    A1a\nB\nC", moved.outline())
    }

    @Test
    fun `an out-of-range index lands at the end rather than throwing`() {
        val moved = tree.moveSubtree("B", targetIndex = 99, targetDepth = 0)

        assertEquals("A\n  A1\n    A1a\n  A2\nC\nB", moved.outline())
    }

    @Test
    fun `an item cannot be dropped inside itself`() {
        // A1a sits inside A; the drop index is expressed against the tree with A already removed,
        // so there is no position that could nest A under its own descendant.
        val moved = tree.moveSubtree("A", targetIndex = 0, targetDepth = 3)

        assertEquals(tree.totalCount(), moved.totalCount())
        assertNotNull(moved.findItem("A1a"))
        assertTrue(moved.outline().startsWith("A"))
    }

    @Test
    fun `moving an unknown item changes nothing`() {
        assertEquals(tree.outline(), tree.moveSubtree("nope", 0, 0).outline())
    }

    @Test
    fun `putting an item back at its own index is a no-op`() {
        val index = tree.visibleIndexOf("B")

        assertEquals(tree.outline(), tree.moveSubtree("B", index, 0).outline())
    }

    @Test
    fun `no item is ever lost or duplicated by a move`() {
        val ids = tree.flattenVisible().map { it.item.id }

        for (id in ids) {
            for (index in 0..ids.size) {
                for (depth in 0..4) {
                    val moved = tree.moveSubtree(id, index, depth)
                    val movedIds = moved.flattenVisible().map { it.item.id }

                    assertEquals("moving $id to $index at depth $depth", ids.size, movedIds.size)
                    assertEquals(ids.toSet(), movedIds.toSet())
                }
            }
        }
    }

    @Test
    fun `every move produces a well-formed tree`() {
        val ids = tree.flattenVisible().map { it.item.id }

        for (id in ids) {
            for (index in 0..ids.size) {
                for (depth in 0..4) {
                    val rows = tree.moveSubtree(id, index, depth).flattenVisible()

                    // Depth starts at 0 and never jumps by more than one level at a time.
                    assertEquals("first row of $id -> $index/$depth", 0, rows.first().depth)
                    rows.zipWithNext { above, below ->
                        assertTrue(
                            "$id -> $index/$depth produced a depth jump",
                            below.depth <= above.depth + 1,
                        )
                    }
                }
            }
        }
    }

    /** The tree as the drag code sees it: with the dragged subtree already taken out. */
    private fun List<TodoItem>.removeAnd(id: String): List<TodoItem> = removeItem(id)
}
