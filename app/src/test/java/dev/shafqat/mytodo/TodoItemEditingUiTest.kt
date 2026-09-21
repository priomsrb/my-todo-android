package dev.shafqat.mytodo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
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
import androidx.compose.ui.unit.height
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
import dev.shafqat.mytodo.ui.todo.EditToolbarTag
import dev.shafqat.mytodo.ui.todo.IndentButtonTag
import dev.shafqat.mytodo.ui.todo.ItemEditActions
import dev.shafqat.mytodo.ui.todo.ItemEditorTag
import dev.shafqat.mytodo.ui.todo.OutdentButtonTag
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
 * the tree, which row a blank one leaves behind — and none of that is visible to the pure model
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

    /** Ids of every item that was deleted, in order. */
    private val deleted = mutableListOf<String>()

    /** The composition's input mode, so a test can put it in the mode a keyboard user is in. */
    private lateinit var inputMode: InputModeManager

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
        inputMode = LocalInputModeManager.current
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
                    onSplitAbove = { id ->
                        // And on the row above it, at the same depth: the item that was there
                        // moves down, children and all.
                        val item = TodoItem(text = "")
                        val row = items.visibleRowOf(id)
                        items = if (row == null) {
                            listOf(item) + items
                        } else {
                            items.insertSubtree(item, row.index, row.depth)
                        }
                        focusItemId = item.id
                    },
                    onBackspaceOnEmpty = { id ->
                        // What the ViewModel does: the row goes, unless it has children under it
                        // or nothing above it, and the editor carries on at the end of the row
                        // above.
                        val item = items.findItem(id)
                        val rows = items.flattenVisible()
                        val above = rows.getOrNull(rows.indexOfFirst { it.item.id == id } - 1)
                        if (item != null && item.children.isEmpty() && above != null) {
                            deleted += id
                            items = items.removeItem(id)
                            focusItemId = above.item.id
                        }
                    },
                    onIndent = { id -> items = items.indentItem(id) },
                    onOutdent = { id -> items = items.outdentItem(id) },
                    // What the ViewModel does: the row is left exactly as it is, blank or not.
                    onEditFinished = { },
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

    /** Opens an item with the caret before its first character, as tapping its left edge does. */
    private fun startEditingAtStart(text: String) {
        composeRule.onNodeWithText(text).performTouchInput { click(Offset(1f, centerY)) }
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
    fun `tapping an item's text puts the caret on the character that was tapped`() {
        composeRule.setContent { Harness() }

        // Hard against the left edge of the text, which is before its first character.
        composeRule.onNodeWithText("Bravo").performTouchInput { click(Offset(1f, centerY)) }
        composeRule.waitForIdle()

        editor().performTextInput("X")
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "XBravo", "Charlie"), renderedOrder)
    }

    @Test
    fun `tapping the empty space beside an item leaves the caret at the end`() {
        composeRule.setContent { Harness() }

        // A short item leaves most of its row blank; tapping there is how the rest of the app's
        // tests start an edit, and it has to still mean "carry on typing at the end".
        startEditing("Bravo")

        editor().performTextInput("X")
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "BravoX", "Charlie"), renderedOrder)
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
    fun `Enter with the caret at the start starts a new item on the row above`() {
        composeRule.setContent { Harness() }
        startEditingAtStart("Bravo")

        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "", "Bravo", "Charlie"), renderedOrder)

        // The editor moved up to the new row, and the item it came from is untouched.
        editor().performTextInput("New")
        composeRule.waitForIdle()
        assertEquals(listOf("Alpha", "New", "Bravo", "Charlie"), renderedOrder)
    }

    @Test
    fun `the new item above a parent is its sibling, and the children stay put`() {
        val nested = listOf(
            TodoItem(
                id = "alpha", text = "Alpha",
                children = listOf(TodoItem(id = "alpha1", text = "Alpha 1")),
            ),
            TodoItem(id = "bravo", text = "Bravo"),
        )
        composeRule.setContent { Harness(nested) }
        startEditingAtStart("Alpha")

        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals(
            listOf("" to 0, "Alpha" to 0, "Alpha 1" to 1, "Bravo" to 0),
            renderedRows,
        )
    }

    @Test
    fun `Enter on an item still empty adds a line under it and keeps the blank one`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        // A second Enter, with nothing typed into the new row. The blank row stays — it is the
        // gap the user is putting between two groups — and another row opens below it.
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "", "", "Bravo", "Charlie"), renderedOrder)
        assertEquals(emptyList<String>(), deleted)
        editor().assertIsDisplayed()

        // The editor is on the lower of the two, so the gap is left above what comes next.
        editor().performTextInput("New")
        composeRule.waitForIdle()
        assertEquals(listOf("Alpha", "", "New", "Bravo", "Charlie"), renderedOrder)
    }

    @Test
    fun `Enter on a blank row keeps stacking gaps rather than ending the edit`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")

        repeat(3) {
            editor().performKeyInput { pressKey(Key.Enter) }
            composeRule.waitForIdle()
        }

        assertEquals(listOf("Alpha", "", "", "", "Bravo", "Charlie"), renderedOrder)
        editor().assertIsDisplayed()
    }

    @Test
    fun `Enter at the start of an item that has text still inserts above it`() {
        composeRule.setContent { Harness() }
        startEditingAtStart("Bravo")

        // The blank-item rule reads the text, not just the caret: this item has something to
        // carry on below the new row, so the new row goes above it.
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "", "Bravo", "Charlie"), renderedOrder)
    }

    @Test
    fun `a blank row is a row like any other - as tall as one, and tappable into`() {
        val withGap = listOf(
            TodoItem(id = "alpha", text = "Alpha"),
            TodoItem(id = "gap", text = ""),
            TodoItem(id = "bravo", text = "Bravo"),
        )
        composeRule.setContent { Harness(withGap) }

        // Nothing is drawn in it, so the only thing giving it a tap target is the height of the
        // empty line itself. A gap the user cannot get the caret back into is a gap they cannot
        // undo by typing, and one they cannot see coming.
        val alpha = composeRule.onNodeWithText("Alpha").getBoundsInRoot()
        val gap = composeRule.onNodeWithText("").getBoundsInRoot()
        assertEquals(alpha.height, gap.height)

        composeRule.onNodeWithText("").performClick()
        composeRule.waitForIdle()

        editor().performTextInput("Typed")
        composeRule.waitForIdle()
        assertEquals(listOf("Alpha", "Typed", "Bravo"), renderedOrder)
    }

    @Test
    fun `Backspace on an empty item takes the row off and carries on above it`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        assertEquals(listOf("Alpha", "", "Bravo", "Charlie"), renderedOrder)

        editor().performKeyInput { pressKey(Key.Backspace) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), renderedOrder)
        // The caret is at the end of the row above, so typing carries on where it left off.
        editor().performTextInput("!")
        composeRule.waitForIdle()
        assertEquals(listOf("Alpha!", "Bravo", "Charlie"), renderedOrder)
    }

    @Test
    fun `Backspace walks back up through a run of blank rows`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")
        repeat(3) {
            editor().performKeyInput { pressKey(Key.Enter) }
            composeRule.waitForIdle()
        }
        assertEquals(6, renderedRows.size)

        repeat(3) {
            editor().performKeyInput { pressKey(Key.Backspace) }
            composeRule.waitForIdle()
        }

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), renderedOrder)
        editor().assertIsDisplayed()
    }

    @Test
    fun `Backspace on an item with text deletes a character, not the row`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")

        editor().performKeyInput { pressKey(Key.Backspace) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "Brav", "Charlie"), renderedOrder)
        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun `Backspace on the first row of the list does nothing`() {
        val leadingGap = listOf(TodoItem(id = "gap", text = ""), TodoItem(id = "alpha", text = "Alpha"))
        composeRule.setContent { Harness(leadingGap) }
        composeRule.onNodeWithText("").performClick()
        composeRule.waitForIdle()

        // There is no row above to carry the caret to, so the keyboard stays where it is rather
        // than the row vanishing under it.
        editor().performKeyInput { pressKey(Key.Backspace) }
        composeRule.waitForIdle()

        assertEquals(listOf("", "Alpha"), renderedOrder)
        assertEquals(emptyList<String>(), deleted)
        editor().assertIsDisplayed()
    }

    @Test
    fun `Backspace leaves an empty item that has children alone`() {
        val blankParent = listOf(
            TodoItem(id = "alpha", text = "Alpha"),
            TodoItem(id = "gap", text = "", children = listOf(TodoItem(id = "sub", text = "Sub"))),
        )
        composeRule.setContent { Harness(blankParent) }
        composeRule.onNodeWithText("").performClick()
        composeRule.waitForIdle()

        // Taking the row would take the subtree with it, which is not what a keystroke should do.
        editor().performKeyInput { pressKey(Key.Backspace) }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha" to 0, "" to 0, "Sub" to 1), renderedRows)
        assertEquals(emptyList<String>(), deleted)
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
     * Nothing tells a text field it lost focus when the whole screen goes away, so without the
     * lifecycle observer the row stays in edit mode — and a row mid-edit is one that cannot be
     * swiped away. The blank row itself is kept now, like every other blank row.
     */
    @Test
    fun `leaving the app ends the edit and keeps the blank row it was on`() {
        composeRule.setContent { Harness() }
        startEditing("Alpha")
        editor().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        assertEquals(4, renderedRows.size)

        composeRule.runOnUiThread {
            lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.waitForIdle()

        editor().assertDoesNotExist()
        assertEquals(listOf("Alpha", "", "Bravo", "Charlie"), renderedOrder)
        assertEquals(emptyList<String>(), deleted)
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

    // --- the edit toolbar -------------------------------------------------------------------

    @Test
    fun `the toolbar is there only while an item is being edited`() {
        composeRule.setContent { Harness() }
        composeRule.onNodeWithTag(EditToolbarTag).assertDoesNotExist()

        startEditing("Bravo")
        composeRule.onNodeWithTag(EditToolbarTag).assertIsDisplayed()

        // Leaving the app is one of the ways an edit ends; the toolbar goes with it.
        composeRule.runOnUiThread {
            lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EditToolbarTag).assertDoesNotExist()
    }

    @Test
    fun `the toolbar's indent button nests the item, as Tab does`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")

        composeRule.onNodeWithTag(IndentButtonTag).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha" to 0, "Bravo" to 1, "Charlie" to 0), renderedRows)
    }

    @Test
    fun `the toolbar's outdent button lifts it back out`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")
        composeRule.onNodeWithTag(IndentButtonTag).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OutdentButtonTag).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha" to 0, "Bravo" to 0, "Charlie" to 0), renderedRows)
    }

    @Test
    fun `a button whose move is impossible is greyed out rather than gone`() {
        composeRule.setContent { Harness() }

        // The first row has nothing above to nest under, and nothing to be lifted out of.
        startEditing("Alpha")
        composeRule.onNodeWithTag(IndentButtonTag).assertIsNotEnabled()
        composeRule.onNodeWithTag(OutdentButtonTag).assertIsNotEnabled()

        // The second has a sibling above it, so it can go in — and only then come back out.
        startEditing("Bravo")
        composeRule.onNodeWithTag(IndentButtonTag).assertIsEnabled()
        composeRule.onNodeWithTag(OutdentButtonTag).assertIsNotEnabled()

        composeRule.onNodeWithTag(IndentButtonTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(OutdentButtonTag).assertIsEnabled()
    }

    @Test
    fun `a toolbar button pressed with a keyboard attached does not end the edit`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")

        // Out of touch mode — where a keyboard user is — a clickable is focusable, and focus
        // leaving the field is what ends an edit. Checked here as well as by hand on a device,
        // since a toolbar that closed the editor it belongs to would be useless.
        composeRule.runOnUiThread { inputMode.requestInputMode(InputMode.Keyboard) }
        composeRule.onNodeWithTag(IndentButtonTag).performClick()
        composeRule.waitForIdle()

        editor().assertIsDisplayed()
        assertEquals(listOf("Alpha" to 0, "Bravo" to 1, "Charlie" to 0), renderedRows)
    }

    @Test
    fun `pressing a toolbar button leaves the editor open on the same item`() {
        composeRule.setContent { Harness() }
        startEditing("Bravo")

        composeRule.onNodeWithTag(IndentButtonTag).performClick()
        composeRule.waitForIdle()

        // The field must not have lost focus: that ends the edit, and a brand new item would be
        // thrown away by the time the next button press landed.
        editor().assertIsDisplayed()
        editor().performTextInput("!")
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha" to 0, "Bravo!" to 1, "Charlie" to 0), renderedRows)
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
