package dev.shafqat.mytodo.ui.todo

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.updateItem

/**
 * The rows of one list, with drag-to-reorder wired up.
 *
 * Separate from [TodoListScreen] so it can be driven straight from a UI test with plain state and
 * callbacks, no ViewModel involved.
 */
@Composable
fun TodoItemList(
    items: List<TodoItem>,
    onToggleDone: (itemId: String, done: Boolean) -> Unit,
    onToggleCollapsed: (itemId: String, collapsed: Boolean) -> Unit,
    onDelete: (itemId: String) -> Unit,
    onMove: (itemId: String, targetIndex: Int, targetDepth: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val dragState = rememberTodoDragState(listState)

    val draggedId = dragState.draggedItemId
    val previewItems = if (draggedId == null) {
        items
    } else {
        // Collapsing the dragged item keeps its descendants out of the way while it travels, and
        // moveSubtree shows it exactly where it would land, indentation included.
        items.updateItem(draggedId) { it.copy(collapsed = true) }
            .moveSubtree(draggedId, dragState.targetIndex, dragState.targetDepth)
    }
    val rows = previewItems.flattenVisible()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        itemsIndexed(rows, key = { _, row -> row.item.id }) { index, row ->
            // pointerInput is keyed on the item id alone, so that reordering the list never cancels
            // an in-flight drag. That also means its gesture block is not recreated when the row
            // moves, so it must not capture the index and depth directly — it would keep whichever
            // values the row had when it was first composed, and picking the row up later would
            // fling it back there.
            val currentIndex by rememberUpdatedState(index)
            val currentDepth by rememberUpdatedState(row.depth)

            TodoRow(
                item = row.item,
                depth = row.depth,
                isDragging = row.item.id == draggedId,
                dragHandleModifier = Modifier.pointerInput(row.item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragState.onDragStart(row.item.id, currentIndex, currentDepth)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragState.onDrag(amount.x, amount.y)
                        },
                        onDragEnd = { dragState.onDragEnd(onMove) },
                        onDragCancel = { dragState.onDragCancel() },
                    )
                },
                onToggleDone = { done -> onToggleDone(row.item.id, done) },
                onToggleCollapsed = { collapsed -> onToggleCollapsed(row.item.id, collapsed) },
                onDelete = { onDelete(row.item.id) },
            )
        }
    }
}
