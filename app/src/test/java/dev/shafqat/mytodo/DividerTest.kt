package dev.shafqat.mytodo

import dev.shafqat.mytodo.model.Divider
import dev.shafqat.mytodo.model.DividerStyle
import dev.shafqat.mytodo.model.dividerOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DividerTest {
    @Test
    fun singleAndDouble() {
        assertEquals(Divider(DividerStyle.Single, "Fruit"), dividerOf("- Fruit -"))
        assertEquals(Divider(DividerStyle.Double, "Weekend"), dividerOf("=Weekend="))
    }

    @Test
    fun noLabel() {
        assertEquals(Divider(DividerStyle.Single, ""), dividerOf("---"))
        assertEquals(Divider(DividerStyle.Double, ""), dividerOf("=="))
    }

    @Test
    fun plainTextIsNotADivider() {
        assertNull(dividerOf("-"))
        assertNull(dividerOf("="))
        assertNull(dividerOf("- milk"))
        assertNull(dividerOf("milk -"))
        assertNull(dividerOf("-milk="))
        assertNull(dividerOf(""))
    }
}
