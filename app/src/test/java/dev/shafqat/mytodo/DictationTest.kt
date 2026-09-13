package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.splitDictation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cutting a dictated sentence into items.
 *
 * The failures worth guarding are the two directions of wrong: a sentence chopped where the user
 * meant one item, and several items left glued together. The first is the worse of the two, so
 * most of these pin down where a split must *not* happen.
 */
class DictationTest {

    @Test
    fun `a plain sentence is one item`() {
        assertEquals(listOf("buy oat milk"), splitDictation("buy oat milk"))
    }

    @Test
    fun `and then starts a new item and leaves no stranded and`() {
        assertEquals(
            listOf("buy milk", "bread", "bin bags"),
            splitDictation("buy milk and then bread and then bin bags"),
        )
    }

    @Test
    fun `then and next and after that all separate`() {
        assertEquals(listOf("call mum", "book the car"), splitDictation("call mum then book the car"))
        assertEquals(listOf("call mum", "book the car"), splitDictation("call mum next book the car"))
        assertEquals(
            listOf("call mum", "book the car"),
            splitDictation("call mum after that book the car"),
        )
        assertEquals(
            listOf("call mum", "book the car"),
            splitDictation("call mum and next book the car"),
        )
    }

    @Test
    fun `a bare and joins one item rather than starting another`() {
        assertEquals(listOf("milk and bread"), splitDictation("milk and bread"))
    }

    @Test
    fun `next followed by a time word is part of the item`() {
        assertEquals(listOf("book the car in next week"), splitDictation("book the car in next week"))
        assertEquals(listOf("call the dentist next Tuesday"), splitDictation("call the dentist next Tuesday"))
        assertEquals(listOf("pay rent next month"), splitDictation("pay rent next month"))
        assertEquals(listOf("return the parcel next door"), splitDictation("return the parcel next door"))
    }

    @Test
    fun `separators inside longer words are left alone`() {
        assertEquals(listOf("sharpen the kitchen knives"), splitDictation("sharpen the kitchen knives"))
        assertEquals(listOf("check the thenar splint"), splitDictation("check the thenar splint"))
    }

    @Test
    fun `case and punctuation around a separator are cleaned up`() {
        assertEquals(
            listOf("Buy milk", "get bread"),
            splitDictation("Buy milk, And Then get bread."),
        )
    }

    @Test
    fun `a trailing separator does not leave an empty item`() {
        assertEquals(listOf("buy milk"), splitDictation("buy milk and then"))
        assertEquals(listOf("buy milk"), splitDictation("then buy milk"))
    }

    @Test
    fun `speech that is only a separator is kept rather than swallowed`() {
        assertEquals(listOf("next"), splitDictation("next"))
    }

    @Test
    fun `nothing spoken gives nothing to add`() {
        assertEquals(emptyList<String>(), splitDictation("   "))
        assertEquals(emptyList<String>(), splitDictation(""))
    }
}
