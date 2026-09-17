package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.itemsFromText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading pasted text as items.
 *
 * The formats here are the ones people actually paste — bullets of every flavour, checkboxes,
 * numbered lines, and bare text with no markup at all — plus the two ways a paste goes wrong:
 * nesting invented where the source had none, and markers left in the text as characters.
 */
class TextImportTest {

    /** The parsed tree as one string per row, two spaces per level, so nesting is readable. */
    private fun outline(text: String): String = itemsFromText(text).rows().joinToString("\n")

    private fun List<TodoItem>.rows(depth: Int = 0): List<String> = flatMap { item ->
        val tick = if (item.done) "[x] " else ""
        listOf("  ".repeat(depth) + tick + item.text) + item.children.rows(depth + 1)
    }

    @Test
    fun `bullet items become one item each`() {
        assertEquals("Item 1\nItem 2", outline("• Item 1\n• Item 2"))
    }

    @Test
    fun `dashes nest by indentation`() {
        assertEquals(
            "Item 1\nItem 2\n  Sub item 1",
            outline("- Item 1\n- Item 2\n  - Sub item 1"),
        )
    }

    @Test
    fun `stars nest by tab`() {
        assertEquals(
            "Item 1\nItem 2\n  Sub item 1",
            outline("* Item 1\n* Item 2\n\t* Sub item 1"),
        )
    }

    @Test
    fun `plain lines are items`() {
        assertEquals("Item 1\nItem 2\nItem 3", outline("Item 1\nItem 2\nItem 3"))
    }

    @Test
    fun `checkboxes carry their ticks`() {
        assertEquals(
            "Item 1\n[x] Completed item 2\n  Sub item 1",
            outline("- [ ] Item 1\n- [x] Completed item 2\n\t- [ ] Sub item 1"),
        )
    }

    @Test
    fun `an uppercase X is ticked too`() {
        assertTrue(itemsFromText("- [X] Done").single().done)
    }

    @Test
    fun `a checkbox without a bullet still counts`() {
        val item = itemsFromText("[x] Done").single()
        assertEquals("Done", item.text)
        assertTrue(item.done)
    }

    @Test
    fun `numbered lines are items, numbers and all removed`() {
        assertEquals("First\nSecond\n  Third", outline("1. First\n2) Second\n   1. Third"))
    }

    @Test
    fun `formats may be mixed within one paste`() {
        assertEquals(
            "Groceries\n  Oat milk\n  [x] Bread\nCall the dentist",
            outline("• Groceries\n  - Oat milk\n  - [x] Bread\nCall the dentist"),
        )
    }

    // --- what must not happen --------------------------------------------------------------

    @Test
    fun `text indented as a whole keeps its own levels`() {
        // Pasted out of a code block or a quoted reply: every line carries four spaces that mean
        // nothing. Without a baseline each line would nest under the one above it.
        assertEquals("Item 1\nItem 2\n  Sub item", outline("    Item 1\n    Item 2\n        Sub item"))
    }

    @Test
    fun `a line cannot skip a level`() {
        assertEquals("Item\n  Sub", outline("Item\n\t\t\t\tSub"))
    }

    @Test
    fun `blank lines and rules are dropped`() {
        assertEquals("Item 1\nItem 2", outline("- Item 1\n\n---\n\n- Item 2\n-\n"))
    }

    @Test
    fun `a dash inside the text is not a bullet`() {
        assertEquals("e-mail Sam", outline("e-mail Sam"))
    }

    @Test
    fun `a decimal is not a numbered item`() {
        assertEquals("3.5 kg of flour", outline("3.5 kg of flour"))
    }

    @Test
    fun `nothing usable gives no items`() {
        assertEquals(emptyList<TodoItem>(), itemsFromText("   \n\n\t\n"))
        assertEquals(emptyList<TodoItem>(), itemsFromText(""))
    }

    @Test
    fun `an empty checkbox line adds no blank row`() {
        assertEquals(emptyList<TodoItem>(), itemsFromText("- [ ] "))
    }

    @Test
    fun `deep nesting is not capped`() {
        val text = (0..9).joinToString("\n") { "\t".repeat(it) + "- Level $it" }
        val depths = generateSequence(itemsFromText(text).single()) { it.children.singleOrNull() }
        assertEquals(10, depths.count())
    }
}
