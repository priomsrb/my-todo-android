package dev.shafqat.mytodo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.withKeyDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.addItem
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.indentItem
import dev.shafqat.mytodo.model.insertSubtree
import dev.shafqat.mytodo.model.outdentItem
import dev.shafqat.mytodo.model.removeItem
import dev.shafqat.mytodo.model.updateItem
import dev.shafqat.mytodo.model.visibleRowOf
import dev.shafqat.mytodo.ui.theme.MyTodoTheme
import dev.shafqat.mytodo.ui.todo.ItemEditActions
import dev.shafqat.mytodo.ui.todo.ItemEditorTag
import dev.shafqat.mytodo.ui.todo.TodoItemList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Inline entry and swipe-to-delete, driven through the real row list.
 *
 * The interesting part of this feature is the wiring — which row has the editor, what a key does to
 * the tree, when a still-empty item is thrown away — and none of that is visible to the pure model
 * tests. The harness stands in for the ViewModel, applying the same tree helpers it does — and it
 * turns swipe-to-delete on, since the setting behind it ships off.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class TodoItemEditingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val initialItems = listOf(
        TodoItem(id = "alpha", text = "Alpha"),
        TodoItem(id = "bravo", text = "Bravo"),
        TodoItem(id = "charlie", text = "Charlie"),
    )

    private var renderedRows: List<Pair<String, Int>> = emptyList()
    private val renderedOrder: List<String> get() = renderedRows.map { it.first }

    /** Ids of every item that was deleted, in order — including the blank ones tidied away. */
    private val deleted = mutableListOf<String>()

    /** A lifecycle the test owns, so it can send the screen to the background on demand. */
    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Composable
    private fun Harness(
        start: List<TodoItem> = initialItems,
        swipeToDeleteEnabled: Boolean = true,
    ) {
        var items by remember { mutableStateOf(start) }
        var focusItemId by remember { mutableStateOf<String?>(null) }
        renderedRows = items.flattenVisible().map { it.item.text to it.depth }

        MyTodoTheme {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
            TodoItemList(
                items = items,
                swipeToDeleteEnabled = swipeToDeleteEnabled,
                focusItemId = focusItemId,
                onToggleDone = { id, done -> items = items.updateItem(id) { it.copy(done = done) } },
                onToggleCollapsed = { id, collapsed ->
                    items = items.updateItem(id) { it.copy(collapsed = collapsed) }
                },
                onDelete = { id ->
                    deleted += id
                    items = items.removeItem(id)
                },
                onMove = { _, _, _ -> },
                editActions = ItemEditActions(
                    onTextChange = { id, text ->
                        items = items.updateItem(id) { it.copy(text = text) }
                    },
                    onSplit = { id ->
                        // What the repository does: a new item on the row after this one.
                        val item = TodoItem(text = "")
                        val row = items.visibleRowOf(id)
                        items = if (row == null) {
                            items.addItem(item)
                        } else {
                            items.insertSubtree(item, row.index + 1, row.depth)
                        }
                        focusItemId = item.id
                    },
                    onIndent = { id -> items = items.indentItem(id) },
                    onOutdent = { id -> items = items.outdentItem(id) },
                    onEditFinished = { id ->
                        val item = items.findItem(id)
                        if (item != null && item.text.isBlank() && item.children.isEmpty()) {
                            deleted += id
                            items = items.removeItem(id)
                        }
                    },
                ),
                modifier = Modifier.fillMaxSize(),
            )
            }
        }
    }

    private fun startEditing(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun editor() = composeRule.onNodeWithTag(ItemEditorTag)

    // --- editing in place -------------------------------------------------------------------

    @Test
    fun `tapping an item's text opens an editor on that row`() {
        composeRule.setContent { Harness() }

        startEditing("Bravo")

        editor().assertIsDisplayed()
    }

    @Test
    fun `typing changes the item`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")

        editor().performTextInput("!")
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "Bravo!", "Charlie"), renderedOrder)
    }

    // --- fast entry -------------------------------------------------------------------------

    @Test
    fun `Enter starts a new item on the next row and moves the editor to it`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")

        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals(4, renderedRows.size)
        assertEquals(listOf("Alpha", "", "Bravo", "Charlie"), renderedOrder)

        // The new row is the one now being edited, so typing lands in it.
        editor().performTextInput("New")
        composeRule.waitForIdle()
        assertEquals(listOf("Alpha", "New", "Bravo", "Charlie"), renderedOrder)
    }

    @Test
    fun `Enter on an item left empty closes the editor and throws the empty item away`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        // A second Enter, with nothing typed into the new row.
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), renderedOrder)
        assertEquals(1, deleted.size)
        composeRule.onNodeWithTag(ItemEditorTag).assertDoesNotExist()
    }

    @Test
    fun `Tab nests the item being edited under the row above it`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")

        editor().performKeyInput { pressKey(Key.Tab) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha" to 0, "Bravo" to 1, "Charlie" to 0), renderedRows)
    }

    @Test
    fun `Shift-Tab lifts the item being edited back out`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")
        editor().performKeyInput { pressKey(Key.Tab) }
        composeRule.waitForIdle()

        editor().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha" to 0, "Bravo" to 0, "Charlie" to 0), renderedRows)
    }

    @Test
    fun `the editor stays on the item after it has been re-nested`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")

        editor().performKeyInput { pressKey(Key.Tab) }
        composeRule.waitForIdle()
        editor().performTextInput("?")
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha" to 0, "Bravo?" to 1, "Charlie" to 0), renderedRows)
    }

    /**
     * Found in the wild: an item typed into and then abandoned by switching away from the app stayed
     * behind as a blank row, and was saved to the markdown file as one. Nothing tells a text field
     * it lost focus when the whole screen goes away.
     */
    @Test
    fun `leaving the app ends the edit, so an unfinished item is not left behind`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        assertEquals(4, renderedRows.size)

        composeRule.runOnUiThread {
            lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), renderedOrder)
        assertEquals(1, deleted.size)
    }

    @Test
    fun `leaving the app keeps an item that was actually typed into`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")
        editor().performTextInput("!")
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "Bravo!", "Charlie"), renderedOrder)
        assertEquals(emptyList<String>(), deleted)
    }

    // --- swipe to delete --------------------------------------------------------------------

    @Test
    fun `swiping a row deletes it`() {
        composeRule.setContent { Harness() }

        composeRule.onNodeWithText("Bravo").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf("bravo"), deleted)
        assertEquals(listOf("Alpha", "Charlie"), renderedOrder)
    }

    @Test
    fun `swiping does nothing while the setting is off`() {
        composeRule.setContent { Harness(swipeToDeleteEnabled = false) }

        composeRule.onNodeWithText("Bravo").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), deleted)
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), renderedOrder)
    }

    @Test
    fun `swiping takes the whole subtree, as every other move does`() {
        val nested = listOf(
            TodoItem(
                id = "alpha", text = "Alpha",
                children = listOf(TodoItem(id = "alpha1", text = "Alpha 1")),
            ),
            TodoItem(id = "bravo", text = "Bravo"),
        )
        composeRule.setContent { Harness(nested) }

        composeRule.onNodeWithText("Alpha").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf("Bravo"), renderedOrder)
    }
}
