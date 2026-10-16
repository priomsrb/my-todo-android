package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.indentItem
import dev.shafqat.mytodo.model.insertSubtree
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.outdentItem
import dev.shafqat.mytodo.model.placedAsIn
import dev.shafqat.mytodo.model.visibleRowOf
import dev.shafqat.mytodo.model.withoutCompleted
import org.junit.Assert.assertEquals
import org.junit.Test

/** Edits worked out among the rows on screen, carried over to a tree that has rows hidden. */
class HiddenMoveTest {

    private fun item(id: String, done: Boolean = false, collapsed: Boolean = false, vararg children: TodoItem) =
        TodoItem(id = id, text = id, done = done, collapsed = collapsed, children = children.toList())

    private fun open(id: String, vararg children: TodoItem) = item(id, children = children)
    private fun done(id: String, vararg children: TodoItem) = item(id, done = true, children = children)

    /** Every item, collapsed or not, as indented ids — the whole file, not what is on screen. */
    private fun List<TodoItem>.outline(depth: Int = 0): String =
        joinToString("") { "  ".repeat(depth) + it.id + "\n" + it.children.outline(depth + 1) }

    /** Each item's parent, which a move must leave alone for everything it did not pick up. */
    private fun List<TodoItem>.parents(parent: String? = null): Map<String, String?> =
        fold(emptyMap()) { map, item -> map + (item.id to parent) + item.children.parents(item.id) }

    private fun List<TodoItem>.moveAmongShown(id: String, index: Int, depth: Int): List<TodoItem> =
        placedAsIn(withoutCompleted().moveSubtree(id, index, depth), findItem(id)!!)

    //  A
    //    a1 (done)
    //    a2
    //    a3 (done)
    //  B (done)
    //  C (done, with an open child, so it stays)
    //    c1
    //  D
    //    d1 (done)
    //  E (done)
    private val tree = listOf(
        open("A", done("a1"), open("a2"), done("a3")),
        done("B"),
        done("C", open("c1")),
        open("D", done("d1")),
        done("E"),
    )

    private val withNothingHidden = listOf(
        open("A", open("A1", open("A1a")), open("A2")),
        item("P", collapsed = true, children = arrayOf(open("p1"))),
        open("B"),
        open("C"),
    )

    @Test
    fun `with nothing hidden it is exactly moveSubtree`() {
        val ids = withNothingHidden.flattenVisible().map { it.item.id }
        for (id in ids) {
            for (index in 0..ids.size) {
                for (depth in 0..4) {
                    val arranged = withNothingHidden.moveSubtree(id, index, depth)
                    assertEquals(
                        "$id -> $index/$depth",
                        arranged.outline(),
                        withNothingHidden.placedAsIn(arranged, withNothingHidden.findItem(id)!!).outline(),
                    )
                }
            }
        }
    }

    @Test
    fun `every move shows what the preview showed and leaves hidden items where they were`() {
        val shown = tree.withoutCompleted()
        val ids = shown.flattenVisible().map { it.item.id }
        val allIds = tree.parents().keys

        for (id in ids) {
            for (index in 0..ids.size) {
                for (depth in 0..4) {
                    val moved = tree.moveAmongShown(id, index, depth)
                    val case = "$id -> $index/$depth"

                    // Through the same filter, since a move can leave a done parent with nothing
                    // open under it, which then hides as it would after a tick.
                    assertEquals(
                        case,
                        shown.moveSubtree(id, index, depth).withoutCompleted().outline(),
                        moved.withoutCompleted().outline(),
                    )
                    assertEquals(case, allIds, moved.parents().keys)
                    assertEquals(case, tree.parents() - id, moved.parents() - id)
                }
            }
        }
    }

    @Test
    fun `a dropped item lands straight after the row above it, before hidden ones`() {
        // On screen: A, a2, C, c1, D. Drop D between a2 and C, nested under A.
        val moved = tree.moveAmongShown("D", 2, 1)

        assertEquals("A\n  a1\n  a2\n  D\n    d1\n  a3\nB\nC\n  c1\nE\n", moved.outline())
    }

    @Test
    fun `a dropped item with nothing above it goes first under its parent`() {
        val moved = tree.moveAmongShown("D", 1, 1)

        assertEquals("A\n  D\n    d1\n  a1\n  a2\n  a3\nB\nC\n  c1\nE\n", moved.outline())
    }

    @Test
    fun `picking any row up and putting it back changes nothing`() {
        val shown = tree.withoutCompleted()
        for (row in shown.flattenVisible()) {
            val at = shown.visibleRowOf(row.item.id)!!
            assertEquals(row.item.id, tree.outline(), tree.moveAmongShown(row.item.id, at.index, at.depth).outline())
        }
    }

    @Test
    fun `Tab does not nest an item under a hidden one`() {
        // In the file a2 follows a1, which is hidden; on screen it is the first child of A.
        val indented = tree.placedAsIn(tree.withoutCompleted().indentItem("a2"), tree.findItem("a2")!!)

        assertEquals(tree.outline(), indented.outline())
    }

    @Test
    fun `Tab nests under the row above on screen, skipping hidden ones in between`() {
        // On screen D sits straight below A; in the file B, hidden, is between them.
        val list = listOf(open("A"), done("B"), open("D"))
        val indented = list.placedAsIn(list.withoutCompleted().indentItem("D"), list.findItem("D")!!)

        assertEquals("A\n  D\nB\n", indented.outline())
    }

    @Test
    fun `Shift-Tab lands after the parent, not after its hidden children`() {
        val outdented = tree.placedAsIn(tree.withoutCompleted().outdentItem("a2"), tree.findItem("a2")!!)

        assertEquals("A\n  a1\n  a3\na2\nB\nC\n  c1\nD\n  d1\nE\n", outdented.outline())
    }

    @Test
    fun `Enter puts the new item straight below the row on screen`() {
        val shown = tree.withoutCompleted()
        val new = open("new")
        val row = shown.visibleRowOf("a2")!!
        val added = tree.placedAsIn(shown.insertSubtree(new, row.index + 1, row.depth), new)

        assertEquals("A\n  a1\n  a2\n  new\n  a3\nB\nC\n  c1\nD\n  d1\nE\n", added.outline())
    }

    @Test
    fun `a moved item takes its hidden children with it`() {
        val moved = tree.moveAmongShown("D", 0, 0)

        assertEquals("D\n  d1\nA\n  a1\n  a2\n  a3\nB\nC\n  c1\nE\n", moved.outline())
    }
}
