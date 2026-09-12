package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.searchItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private val lists = listOf(
        TodoList(
            fileName = "groceries.md",
            items = listOf(
                TodoItem(id = "weekend", text = "Weekend", children = listOf(
                    TodoItem(id = "bread", text = "Bread"),
                    TodoItem(id = "milk", text = "Oat milk"),
                )),
                TodoItem(id = "bin", text = "Bin bags"),
            ),
        ),
        TodoList(
            fileName = "house-work.md",
            items = listOf(
                TodoItem(id = "paint", text = "Paint the shed", collapsed = true, children = listOf(
                    TodoItem(id = "brush", text = "Buy a brush"),
                )),
            ),
        ),
    )

    @Test
    fun `matching is case-insensitive and matches anywhere in the text`() {
        val hits = lists.searchItems("bre")

        assertEquals(listOf("bread"), hits.map { it.item.id })
        assertEquals("Groceries", hits.single().listName)
        assertEquals("groceries.md", hits.single().listId)
    }

    @Test
    fun `a hit carries the path of its ancestors`() {
        val hits = lists.searchItems("oat milk")

        assertEquals(listOf("Weekend"), hits.single().path)
    }

    @Test
    fun `collapsed subtrees are searched too`() {
        val hits = lists.searchItems("brush")

        assertEquals(listOf("brush"), hits.map { it.item.id })
        assertEquals(listOf("Paint the shed"), hits.single().path)
    }

    @Test
    fun `results come back in list order and then tree order`() {
        val hits = lists.searchItems("b")

        assertEquals(listOf("bread", "bin", "brush"), hits.map { it.item.id })
    }

    @Test
    fun `a blank query matches nothing rather than everything`() {
        assertTrue(lists.searchItems("").isEmpty())
        assertTrue(lists.searchItems("   ").isEmpty())
    }
}
