package dev.shafqat.mytodo

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.widget.openListIntent
import dev.shafqat.mytodo.widget.widgetRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the home-screen widgets are made of, short of the drawing itself.
 *
 * A widget hands out row keys and gets taps back long after the process that drew it is gone, so
 * the interesting failures are all about identity: naming the wrong item, or two rows that the
 * system cannot tell apart.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WidgetRowsTest {

    //  Shop
    //    Milk        done
    //    Bread
    //  Chores        done
    private val list = TodoList(
        fileName = "groceries.md",
        items = listOf(
            TodoItem(
                id = "shop", text = "Shop",
                children = listOf(
                    TodoItem(id = "milk", text = "Milk", done = true),
                    TodoItem(id = "bread", text = "Bread"),
                ),
            ),
            TodoItem(id = "chores", text = "Chores", done = true),
        ),
    )

    @Test
    fun `rows carry text, done and depth in the order they are drawn`() {
        assertEquals(
            listOf("Shop" to 0, "Milk" to 1, "Bread" to 1, "Chores" to 0),
            list.widgetRows().map { it.text to it.depth },
        )
        assertEquals(listOf(false, true, false, true), list.widgetRows().map { it.done })
    }

    @Test
    fun `a collapsed subtree stays folded, as it is in the app`() {
        val collapsed = list.copy(
            items = list.items.map { if (it.id == "shop") it.copy(collapsed = true) else it },
        )

        assertEquals(listOf("Shop", "Chores"), collapsed.widgetRows().map { it.text })
    }

    @Test
    fun `hidden completed items stay hidden, as they are in the app`() {
        val hiding = list.copy(prefs = ListPrefs(hideCompleted = true))

        assertEquals(listOf("Shop", "Bread"), hiding.widgetRows().map { it.text })
    }

    // --- identity ---------------------------------------------------------------------------

    @Test
    fun `every row's key resolves back to the item it was drawn for`() {
        list.widgetRows().forEach { row ->
            val id = CollapseKeys.idOf(list.items, list.fileName, row.key)
            val item = list.items.flatMap { listOf(it) + it.children }.first { it.id == id }

            assertEquals(row.text, item.text)
        }
    }

    /**
     * The trap this guards: keys number same-named siblings by position, so keys taken from a tree
     * that has already been filtered name whichever item is now in that position.
     */
    @Test
    fun `keys survive a filtered view, so hiding a twin does not re-point its sibling`() {
        val twins = TodoList(
            fileName = "shop.md",
            items = listOf(
                TodoItem(id = "first", text = "Milk", done = true),
                TodoItem(id = "second", text = "Milk"),
            ),
            prefs = ListPrefs(hideCompleted = true),
        )

        val row = twins.widgetRows().single()

        assertEquals("second", CollapseKeys.idOf(twins.items, twins.fileName, row.key))
    }

    @Test
    fun `a key from before an edit resolves to nothing rather than to the wrong item`() {
        val row = list.widgetRows().first { it.text == "Bread" }
        val edited = list.items.map { item ->
            item.copy(children = item.children.map {
                if (it.id == "bread") it.copy(text = "Sourdough") else it
            })
        }

        assertNull(CollapseKeys.idOf(edited, list.fileName, row.key))
    }

    // --- opening the app --------------------------------------------------------------------

    /**
     * `Intent` equality ignores extras. Two rows whose intents differed only by extra would share a
     * `PendingIntent`, and every row in the launcher widget would open the same list.
     */
    @Test
    fun `intents for different lists are not interchangeable`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val groceries = openListIntent(context, "groceries.md")
        val chores = openListIntent(context, "chores.md")

        assertNotEquals(groceries.filterEquals(chores), true)
        assertEquals("groceries.md", groceries.getStringExtra(MainActivity.EXTRA_LIST_ID))
    }

    @Test
    fun `an intent with no list just opens the app`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = openListIntent(context, null)

        assertNull(intent.getStringExtra(MainActivity.EXTRA_LIST_ID))
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }
}
