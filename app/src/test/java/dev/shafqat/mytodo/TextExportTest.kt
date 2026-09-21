package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.CopyFormat
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.itemsFromText
import dev.shafqat.mytodo.model.textFromItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Writing items out as text, for the clipboard.
 *
 * The round trip is the point of most of these: what a copy puts on the clipboard is what "Add
 * from text" reads back, so anything the pair loses in between — a tick, a level of nesting — is a
 * list that comes home changed.
 */
class TextExportTest {

    private val items = listOf(
        TodoItem(
            text = "Item 1",
            children = listOf(
                TodoItem(text = "Sub item 1"),
                TodoItem(text = "Sub item 2", done = true),
            ),
        ),
        TodoItem(text = "Item 2", done = true),
    )

    @Test
    fun `checkboxes are the app's own format`() {
        assertEquals(
            "- [ ] Item 1\n\t- [ ] Sub item 1\n\t- [X] Sub item 2\n- [X] Item 2",
            textFromItems(items, CopyFormat.Checkboxes),
        )
    }

    @Test
    fun `bullets drop the checkbox syntax`() {
        assertEquals(
            "- Item 1\n\t- Sub item 1\n\t- Sub item 2\n- Item 2",
            textFromItems(items, CopyFormat.Bullets),
        )
    }

    @Test
    fun `checkboxes are what the app copies until told otherwise`() {
        assertEquals(textFromItems(items, CopyFormat.Checkboxes), textFromItems(items))
    }

    @Test
    fun `a copy in checkboxes pastes back in unchanged`() {
        val copied = textFromItems(items, CopyFormat.Checkboxes)
        assertEquals(copied, textFromItems(itemsFromText(copied), CopyFormat.Checkboxes))
    }

    @Test
    fun `a copy in bullets pastes back in with its nesting`() {
        val copied = textFromItems(items, CopyFormat.Bullets)
        assertEquals(copied, textFromItems(itemsFromText(copied), CopyFormat.Bullets))
    }

    @Test
    fun `nesting goes as deep as the tree does`() {
        val deep = listOf(
            TodoItem(
                text = "One",
                children = listOf(
                    TodoItem(
                        text = "Two",
                        children = listOf(TodoItem(text = "Three")),
                    ),
                ),
            ),
        )
        assertEquals("- [ ] One\n\t- [ ] Two\n\t\t- [ ] Three", textFromItems(deep))
    }

    @Test
    fun `a list hiding finished items copies only what is on screen`() {
        val list = TodoList("groceries.md", items, ListPrefs(hideCompleted = true))
        assertEquals(
            "- [ ] Item 1\n\t- [ ] Sub item 1",
            textFromItems(list.visibleItems),
        )
    }

    @Test
    fun `a list showing finished items copies them too`() {
        val list = TodoList("groceries.md", items)
        assertEquals(textFromItems(items), textFromItems(list.visibleItems))
    }

    @Test
    fun `nothing to copy is an empty string`() {
        assertEquals("", textFromItems(emptyList()))
    }

    @Test
    fun `a row still being typed into is left out`() {
        val withBlank = listOf(TodoItem(text = "Item 1"), TodoItem(text = "  "))
        assertEquals("- [ ] Item 1", textFromItems(withBlank))
    }

    @Test
    fun `a blank item with children keeps its line so they keep their level`() {
        val blankParent = listOf(
            TodoItem(text = "", children = listOf(TodoItem(text = "Sub item 1"))),
        )
        assertEquals("- [ ]\n\t- [ ] Sub item 1", textFromItems(blankParent))
    }

    @Test
    fun `there is no trailing newline to delete`() {
        assertFalse(textFromItems(items).endsWith("\n"))
    }
}
