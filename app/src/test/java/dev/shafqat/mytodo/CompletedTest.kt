package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.completedLast
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.hasCompleted
import dev.shafqat.mytodo.model.totalCount
import dev.shafqat.mytodo.model.withDoneFrom
import dev.shafqat.mytodo.model.withSubtreeDone
import dev.shafqat.mytodo.model.withoutCompleted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedTest {

    //  Shop           done
    //    Milk         done
    //    Bread
    //  Chores
    //    Dishes       done
    //  Call mum
    private val tree = listOf(
        TodoItem(
            id = "shop", text = "Shop", done = true,
            children = listOf(
                TodoItem(id = "milk", text = "Milk", done = true),
                TodoItem(id = "bread", text = "Bread"),
            ),
        ),
        TodoItem(
            id = "chores", text = "Chores",
            children = listOf(TodoItem(id = "dishes", text = "Dishes", done = true)),
        ),
        TodoItem(id = "mum", text = "Call mum"),
    )

    private fun List<TodoItem>.outline(): String =
        flattenVisible().joinToString("\n") { "  ".repeat(it.depth) + it.item.id }

    // --- hiding -----------------------------------------------------------------------------

    @Test
    fun `hiding drops finished items`() {
        val visible = tree.withoutCompleted()

        assertEquals(
            """
            shop
              bread
            chores
            mum
            """.trimIndent(),
            visible.outline(),
        )
    }

    @Test
    fun `a finished item with unfinished work under it stays, because its children need it`() {
        assertTrue(tree.withoutCompleted().any { it.id == "shop" })
    }

    @Test
    fun `a finished subtree goes entirely`() {
        val allDone = listOf(
            TodoItem(
                id = "shop", text = "Shop", done = true,
                children = listOf(TodoItem(id = "milk", text = "Milk", done = true)),
            ),
        )

        assertEquals(emptyList<TodoItem>(), allDone.withoutCompleted())
    }

    @Test
    fun `a kept item stays even though it is finished, and nothing else does`() {
        assertEquals(
            """
            shop
              milk
              bread
            chores
            mum
            """.trimIndent(),
            tree.withoutCompleted(keep = setOf("milk")).outline(),
        )
    }

    @Test
    fun `keeping a finished child keeps the finished parent it hangs from`() {
        val allDone = listOf(
            TodoItem(
                id = "shop", text = "Shop", done = true,
                children = listOf(TodoItem(id = "milk", text = "Milk", done = true)),
            ),
        )

        assertEquals("shop\n  milk", allDone.withoutCompleted(keep = setOf("milk")).outline())
    }

    @Test
    fun `hiding never touches the underlying tree`() {
        val before = tree.outline()
        tree.withoutCompleted()

        assertEquals(before, tree.outline())
    }

    // --- reordering -------------------------------------------------------------------------

    @Test
    fun `moving completed to the bottom sorts every level and keeps subtrees together`() {
        assertEquals(
            """
            chores
              dishes
            mum
            shop
              bread
              milk
            """.trimIndent(),
            tree.completedLast().outline(),
        )
    }

    @Test
    fun `moving completed to the bottom loses nothing`() {
        assertEquals(tree.totalCount(), tree.completedLast().totalCount())
    }

    @Test
    fun `moving completed to the bottom is stable and idempotent`() {
        val once = tree.completedLast()

        assertEquals(once.outline(), once.completedLast().outline())
    }

    @Test
    fun `hasCompleted sees a tick at any depth`() {
        assertTrue(tree.hasCompleted())
        assertFalse(tree.withoutCompleted().let { listOf(it[2]) }.hasCompleted())
    }

    // --- ticking a subtree as one ----------------------------------------------------------

    private fun TodoItem.ticks(): String =
        listOf(this).flattenVisible().joinToString(" ") { "${it.item.id}=${if (it.item.done) "x" else "-"}" }

    @Test
    fun `ticking a subtree ticks every item in it, at any depth`() {
        val nested = tree[0].copy(
            done = false,
            children = tree[0].children + TodoItem(
                id = "eggs", text = "Eggs",
                children = listOf(TodoItem(id = "free", text = "Free range")),
            ),
        )

        assertEquals("shop=x milk=x bread=x eggs=x free=x", nested.withSubtreeDone(true).ticks())
    }

    @Test
    fun `restoring puts back each tick the subtree had, keeping the ids it has now`() {
        val before = tree[0].copy(done = false)
        val ticked = before.withSubtreeDone(true)

        val restored = ticked.withDoneFrom(before)

        assertEquals("shop=- milk=x bread=-", restored.ticks())
        assertEquals(before, restored)
    }

    @Test
    fun `a level that changed shape since keeps the ticks it has`() {
        val before = tree[0].copy(done = false)
        val ticked = before.withSubtreeDone(true)
        val edited = ticked.copy(children = ticked.children + TodoItem(id = "jam", text = "Jam", done = true))

        assertEquals("shop=- milk=x bread=x jam=x", edited.withDoneFrom(before).ticks())
    }
}
