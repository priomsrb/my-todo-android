package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.markdown.MarkdownDocument
import dev.shafqat.mytodo.data.markdown.MarkdownParser
import dev.shafqat.mytodo.data.markdown.MarkdownSerializer
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    private val canonical = """
        - [ ] Item 1
        ${'\t'}- [ ] Sub item 1
        ${'\t'}- [ ] Sub item 2
        - [X] Checked off item
    """.trimIndent() + "\n"

    @Test
    fun `parses the canonical format`() {
        val document = MarkdownParser.parse(canonical)
        val rows = document.items.flattenVisible()

        assertEquals(listOf("Item 1", "Sub item 1", "Sub item 2", "Checked off item"), rows.map { it.item.text })
        assertEquals(listOf(0, 1, 1, 0), rows.map { it.depth })
        assertEquals(listOf(false, false, false, true), rows.map { it.item.done })
    }

    @Test
    fun `canonical format round-trips byte for byte`() {
        assertEquals(canonical, MarkdownSerializer.serialize(MarkdownParser.parse(canonical)))
    }

    @Test
    fun `space indentation and lowercase x are accepted and normalized to tabs`() {
        val messy = """
            - [ ] Top
              - [x] Two-space child
              - [ ] Sibling
                - [X] Grandchild
        """.trimIndent()

        val normalized = MarkdownSerializer.serialize(MarkdownParser.parse(messy))

        assertEquals(
            "- [ ] Top\n\t- [X] Two-space child\n\t- [ ] Sibling\n\t\t- [X] Grandchild\n",
            normalized,
        )
    }

    @Test
    fun `four-space indentation is understood as one level`() {
        val fourSpace = "- [ ] Top\n    - [ ] Child\n        - [ ] Grandchild\n"

        val rows = MarkdownParser.parse(fourSpace).items.flattenVisible()

        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
    }

    @Test
    fun `star and plus bullets are accepted`() {
        val bullets = "* [ ] Star\n+ [X] Plus\n"

        val items = MarkdownParser.parse(bullets).items

        assertEquals(listOf("Star", "Plus"), items.map { it.text })
        assertEquals(listOf(false, true), items.map { it.done })
    }

    @Test
    fun `over-indentation cannot create phantom levels`() {
        // A child indented four levels below its parent is still only one level deeper.
        val jumpy = "- [ ] Top\n\t\t\t\t- [ ] Far too deep\n"

        val rows = MarkdownParser.parse(jumpy).items.flattenVisible()

        assertEquals(listOf(0, 1), rows.map { it.depth })
    }

    @Test
    fun `unknown lines survive a round trip in place`() {
        val handEdited = """
            # My notes
            - [ ] Real item
            some stray prose
            ${'\t'}- [X] Child
        """.trimIndent()

        val document = MarkdownParser.parse(handEdited)
        val output = MarkdownSerializer.serialize(document)

        assertEquals("# My notes\n- [ ] Real item\nsome stray prose\n\t- [X] Child\n", output)
    }

    @Test
    fun `blank lines are normalized away`() {
        val spaced = "- [ ] One\n\n\n- [ ] Two\n"

        assertEquals("- [ ] One\n- [ ] Two\n", MarkdownSerializer.serialize(MarkdownParser.parse(spaced)))
    }

    @Test
    fun `an empty file parses to an empty document`() {
        val document = MarkdownParser.parse("")

        assertTrue(document.items.isEmpty())
        assertEquals("", MarkdownSerializer.serialize(document))
    }

    @Test
    fun `deep nesting round-trips at fifty levels`() {
        var deepest = TodoItem(text = "level-50")
        repeat(50) { level -> deepest = TodoItem(text = "level-${49 - level}", children = listOf(deepest)) }
        val original = listOf(deepest)

        val reparsed = MarkdownParser.parse(MarkdownSerializer.serialize(MarkdownDocument(original)))
        val rows = reparsed.items.flattenVisible()

        assertEquals(51, rows.size)
        assertEquals(50, rows.last().depth)
        assertEquals("level-50", rows.last().item.text)
    }

    @Test
    fun `text is preserved verbatim apart from surrounding space`() {
        val tricky = "- [ ]   Buy milk [2L] - urgent  \n"

        val items = MarkdownParser.parse(tricky).items

        assertEquals("Buy milk [2L] - urgent", items.single().text)
    }

    // --- blank rows ---------------------------------------------------------------------------

    @Test
    fun `a blank item is a line of its own, with no trailing space`() {
        val text = MarkdownSerializer.serialize(
            listOf(
                TodoItem(text = "Milk"),
                TodoItem(text = ""),
                TodoItem(text = "Screwdriver"),
            ),
        )

        assertEquals("- [ ] Milk\n- [ ]\n- [ ] Screwdriver\n", text)
    }

    @Test
    fun `a blank item survives the round trip as a blank item`() {
        // The gaps a list is grouped with are the user's, so they have to come back out of the
        // file the same way — not as a dropped line, and not as an unparsed one kept aside.
        val parsed = MarkdownParser.parse("- [ ] Milk\n- [ ]\n- [ ] Screwdriver\n")

        assertEquals(listOf("Milk", "", "Screwdriver"), parsed.items.map { it.text })
        assertEquals(emptyMap<String, List<String>>(), parsed.extraLines)
    }

    @Test
    fun `a blank item written with a trailing space still reads as blank`() {
        // What earlier versions of the app wrote, and what a hand-edit can easily leave behind.
        val parsed = MarkdownParser.parse("- [ ] Milk\n- [ ] \n")

        assertEquals(listOf("Milk", ""), parsed.items.map { it.text })
    }
}
