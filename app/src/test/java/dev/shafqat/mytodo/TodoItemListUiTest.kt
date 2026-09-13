package dev.shafqat.mytodo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.updateItem
import dev.shafqat.mytodo.ui.theme.MyTodoTheme
import dev.shafqat.mytodo.ui.todo.IndentPerLevel
import dev.shafqat.mytodo.ui.todo.TodoItemList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

/**
 * UI-level tests for the row list, driven with plain state instead of a ViewModel.
 *
 * These exist because every drag bug so far lived here — in the wiring between the gesture, the
 * lazy list and the move maths — where the pure model tests cannot see them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class TodoItemListUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val initialItems = listOf(
        TodoItem(id = "alpha", text = "Alpha"),
        TodoItem(id = "bravo", text = "Bravo"),
        TodoItem(id = "charlie", text = "Charlie"),
        TodoItem(id = "delta", text = "Delta"),
    )

    /** The rendered rows, as the test sees them on screen: text and indent depth. */
    private var renderedRows: List<Pair<String, Int>> = emptyList()

    private val renderedOrder: List<String> get() = renderedRows.map { it.first }

    @Composable
    private fun Harness(start: List<TodoItem> = initialItems) {
        var items by remember { mutableStateOf(start) }
        renderedRows = items.flattenVisible().map { it.item.text to it.depth }

        MyTodoTheme {
            TodoItemList(
                items = items,
                onToggleDone = { id, done -> items = items.updateItem(id) { it.copy(done = done) } },
                onToggleCollapsed = { id, collapsed ->
                    items = items.updateItem(id) { it.copy(collapsed = collapsed) }
                },
                onDelete = { },
                onMove = { id, index, depth -> items = items.moveSubtree(id, index, depth) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    /**
     * Drags the handle of the row at [rowIndex] by [dy] pixels, with no hold first.
     *
     * The drag starts as soon as the movement passes touch slop, so the first slop-worth of
     * travel is swallowed by the gesture detector; [dx] and [dy] are padded by that much to
     * describe how far the row is actually asked to move.
     */
    private fun dragRow(rowIndex: Int, dy: Float = 0f, dx: Float = 0f) {
        composeRule.onAllNodesWithContentDescription("Reorder")[rowIndex].performTouchInput {
            down(center)
            advanceEventTime(16)
            if (dx != 0f || dy != 0f) {
                val slop = viewConfiguration.touchSlop
                val total = Offset(dx + slop * Math.signum(dx), dy + slop * Math.signum(dy))
                // Several small steps, the way a finger actually moves.
                repeat(4) {
                    moveBy(total / 4f)
                    advanceEventTime(16)
                }
            } else {
                // No movement at all: this never becomes a drag, which is the point of the test.
                advanceEventTime(200)
            }
            up()
        }
        composeRule.waitForIdle()
    }

    private fun rowHeightPx(): Float = with(composeRule.density) { 56.dp.toPx() }

    @Test
    fun `rows render in order`() {
        composeRule.setContent { Harness() }

        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), renderedOrder)
        composeRule.onNodeWithText("Charlie").assertExists()
    }

    @Test
    fun `dragging a row upwards reorders it`() {
        composeRule.setContent { Harness() }

        dragRow(rowIndex = 2, dy = -rowHeightPx())

        assertEquals(listOf("Alpha", "Charlie", "Bravo", "Delta"), renderedOrder)
    }

    @Test
    fun `a drag starts without holding the handle first`() {
        // Reordering used to wait for a long press, which made every move feel stuck. The handle
        // exists to be dragged, so the first movement on it is the drag — nothing to wait out.
        composeRule.setContent { Harness() }

        composeRule.onAllNodesWithContentDescription("Reorder")[2].performTouchInput {
            down(center)
            // Straight into the movement: no time passes between the touch and the drag.
            repeat(4) { moveBy(Offset(0f, -(rowHeightPx() + viewConfiguration.touchSlop) / 4f)) }
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf("Alpha", "Charlie", "Bravo", "Delta"), renderedOrder)
    }

    @Test
    fun `picking a row up without moving leaves the order alone`() {
        composeRule.setContent { Harness() }

        dragRow(rowIndex = 2)

        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), renderedOrder)
    }

    @Test
    fun `a row that has already been moved does not jump when picked up again`() {
        // The regression: the gesture used to capture the row's index when it was first composed,
        // so grabbing a row after moving it flung it back to where it started.
        composeRule.setContent { Harness() }

        dragRow(rowIndex = 2, dy = -rowHeightPx())
        assertEquals(listOf("Alpha", "Charlie", "Bravo", "Delta"), renderedOrder)

        // Charlie now sits at index 1. Pick it up there and let go without moving.
        dragRow(rowIndex = 1)

        assertEquals(listOf("Alpha", "Charlie", "Bravo", "Delta"), renderedOrder)
    }

    @Test
    fun `dragging sideways nests a row under the one above it`() {
        composeRule.setContent { Harness() }
        assertEquals(0, renderedRows[1].second)

        // Indent Bravo under Alpha: one indent step right, staying on its own row.
        dragRow(rowIndex = 1, dx = with(composeRule.density) { IndentPerLevel.toPx() })

        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), renderedOrder)
        assertEquals("Bravo should now be nested under Alpha", 1, renderedRows[1].second)
    }

    @Test
    fun `the dragged row follows the finger between slots`() {
        // The row used to be drawn only ever *in* a slot, so it hopped a whole row at a time and
        // spent most of a drag somewhere other than under the finger.
        composeRule.setContent { Harness() }

        fun handle() = composeRule.onAllNodesWithContentDescription("Reorder")[1]
        val before = composeRule.onNodeWithText("Bravo").getUnclippedBoundsInRoot().top

        var slop = 0f
        handle().performTouchInput {
            slop = viewConfiguration.touchSlop
            down(center)
        }

        // A quarter of a row: nowhere near far enough to change slots, so a row that only ever
        // draws in its slot would still be exactly where it started.
        val nudge = rowHeightPx() / 4f
        // The frame clock is driven by hand from here, because the drag's auto-scroll loop asks for
        // a frame every frame: an automatically advancing clock would never call the test idle.
        composeRule.mainClock.autoAdvance = false
        handle().performTouchInput { moveBy(Offset(0f, nudge + slop)) }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }

        val during = composeRule.onNodeWithText("Bravo").getUnclippedBoundsInRoot().top
        val followed = with(composeRule.density) { (during - before).toPx() }

        handle().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals("the row should have travelled with the finger", nudge, followed, 6f)
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), renderedOrder)
    }

    @Test
    fun `nudging the top row upward does not send it to the end of the list`() {
        // The regression: a finger above the first row but still inside the list's top padding hit
        // no row at all, and the fallback was "past the end of the list" — so a small upward nudge
        // on the top row dropped it at the bottom.
        composeRule.setContent { Harness() }

        listOf(0.25f, 0.5f, 0.55f, 0.6f, 0.75f, 1f).forEach { fraction ->
            dragRow(rowIndex = 0, dy = -rowHeightPx() * fraction)

            assertEquals(
                "nudging up by $fraction of a row should leave Alpha on top",
                listOf("Alpha", "Bravo", "Charlie", "Delta"),
                renderedOrder,
            )
        }
    }

    @Test
    fun `a parent drags its children along`() {
        val nested = listOf(
            TodoItem(
                id = "parent", text = "Parent",
                children = listOf(TodoItem(id = "child", text = "Child")),
            ),
            TodoItem(id = "last", text = "Last"),
        )
        composeRule.setContent { Harness(nested) }

        // Parent is row 0; drag it past Last.
        dragRow(rowIndex = 0, dy = rowHeightPx() * 2)

        assertEquals(listOf("Last", "Parent", "Child"), renderedOrder)
    }
}
