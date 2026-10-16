package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.TextSplit
import dev.shafqat.mytodo.model.splitText
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTextTest {

    @Test
    fun `a caret in the middle splits the text there`() {
        assertEquals(TextSplit("buy", "milk"), splitText("buymilk", 3))
    }

    @Test
    fun `the whitespace either side of the split is trimmed`() {
        assertEquals(TextSplit("buy milk", "and eggs"), splitText("buy milk and eggs", 9))
        assertEquals(TextSplit("buy milk", "and eggs"), splitText("buy milk   and eggs", 10))
    }

    @Test
    fun `whitespace away from the split is left alone`() {
        assertEquals(TextSplit("a  b", "c  d"), splitText("a  b c  d", 4))
    }

    @Test
    fun `a caret at the end carries nothing`() {
        assertEquals(TextSplit("milk", ""), splitText("milk", 4))
        assertEquals(TextSplit("milk", ""), splitText("milk  ", 6))
    }

    @Test
    fun `an empty item splits into two empty ones`() {
        assertEquals(TextSplit("", ""), splitText("", 0))
    }

    @Test
    fun `a selection is deleted before the split`() {
        assertEquals(TextSplit("buy", "eggs"), splitText("buy milk eggs", 4, 9))
    }

    @Test
    fun `a selection made backwards splits the same way`() {
        assertEquals(TextSplit("buy", "eggs"), splitText("buy milk eggs", 9, 4))
    }

    @Test
    fun `selecting everything leaves both halves empty`() {
        assertEquals(TextSplit("", ""), splitText("milk", 0, 4))
    }

    @Test
    fun `offsets past the text are clamped`() {
        assertEquals(TextSplit("milk", ""), splitText("milk", 10))
    }
}
