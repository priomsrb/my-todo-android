package dev.shafqat.mytodo.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.updateItem

/**
 * What a row can do to the item it shows while that item is being edited.
 *
 * The list only routes these; what they mean to the data is the ViewModel's business.
 */
data class ItemEditActions(
    val onTextChange: (itemId: String, text: String) -> Unit = { _, _ -> },
    /** Enter on a non-empty item: start a new one straight after it. */
    val onSplit: (itemId: String) -> Unit = {},
    val onIndent: (itemId: String) -> Unit = {},
    val onOutdent: (itemId: String) -> Unit = {},
    /** Editing stopped. An item still empty at this point never existed as far as the user cares. */
    val onEditFinished: (itemId: String) -> Unit = {},
)

/**
 * The rows of one list, with drag-to-reorder, inline editing and swipe-to-delete wired up.
 *
 * Swipe-to-delete is off unless [swipeToDeleteEnabled] says otherwise, matching the setting that
 * turns it on: the gesture is easy to trigger while scrolling, and the row's delete button is
 * always there.
 *
 * Separate from [TodoListScreen] so it can be driven straight from a UI test with plain state and
 * callbacks, no ViewModel involved.
 *
 * Which row is being edited — and where its caret starts — is state of this list rather than of the
 * screen: it has to survive a row moving, and it follows [focusItemId] so that a freshly created
 * item opens for typing without the screen having to reach down into the list.
 */
@Composable
fun TodoItemList(
    items: List<TodoItem>,
    onToggleDone: (itemId: String, done: Boolean) -> Unit,
    onToggleCollapsed: (itemId: String, collapsed: Boolean) -> Unit,
    onDelete: (itemId: String) -> Unit,
    onMove: (itemId: String, targetIndex: Int, targetDepth: Int) -> Unit,
    modifier: Modifier = Modifier,
    dragEnabled: Boolean = true,
    swipeToDeleteEnabled: Boolean = false,
    focusItemId: String? = null,
    editActions: ItemEditActions = ItemEditActions(),
) {
    val listState = rememberLazyListState()
    val dragState = rememberTodoDragState(listState)
    var editingItemId by remember { mutableStateOf<String?>(null) }
    // Where the caret goes when the editor opens: the character that was tapped, or null for the
    // end of the text, which is what a row opened any other way wants.
    var editingCaret by remember { mutableStateOf<Int?>(null) }
    val actions by rememberUpdatedState(editActions)

    // A new item arrives already open for typing, which is the whole point of Enter.
    LaunchedEffect(focusItemId) {
        if (focusItemId != null) {
            editingItemId = focusItemId
            editingCaret = null
        }
    }

    /** Ends an edit. Safe to call twice — a row can both lose focus and be dismissed by Enter. */
    fun stopEditing(itemId: String) {
        if (editingItemId == itemId) editingItemId = null
        actions.onEditFinished(itemId)
    }

    // Back closes the editor before it leaves the screen. Without this the only way out of an edit
    // is to start another one, and a row stuck in edit mode is a row that cannot be swiped away.
    editingItemId?.let { editing -> BackHandler { stopEditing(editing) } }

    // Leaving the app is also the end of an edit. Nothing tells a text field it lost focus when the
    // whole screen goes away, so without this an item the user never finished typing is left
    // behind as a blank row — and saved to their file as one.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, editingItemId) {
        val editing = editingItemId
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && editing != null) stopEditing(editing)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            val isEditing = row.item.id == editingItemId
            val isFloating = row.item.id == dragState.floatingItemId

            SwipeToDelete(
                modifier = when {
                    // The dragged row is drawn lifted out of the list and offset onto the finger,
                    // so it travels with it rather than hopping a whole slot at a time. The slot it
                    // has been given underneath is the gap it would drop into.
                    isFloating -> Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationY = dragState.floatingOffsetFor(row.item.id)
                        }
                    // Everything else slides out of its way instead of teleporting.
                    dragState.isDragging -> Modifier.animateItem()
                    else -> Modifier
                },
                // Swiping a row that is mid-edit would be an accident, not an intention.
                enabled = swipeToDeleteEnabled && !isEditing,
                onDelete = { onDelete(row.item.id) },
            ) {
                TodoRow(
                    item = row.item,
                    depth = row.depth,
                    isDragging = isFloating,
                    isEditing = isEditing,
                    onStartEdit = { caret ->
                        editingItemId = row.item.id
                        editingCaret = caret
                    },
                    initialCaret = editingCaret,
                    showDragHandle = dragEnabled,
                    editCallbacks = RowEditCallbacks(
                        onTextChange = { text -> actions.onTextChange(row.item.id, text) },
                        onSplit = {
                            // Enter on an item still empty means "I am done adding", so it closes
                            // the editor instead of spawning another empty row.
                            if (row.item.text.isBlank()) {
                                stopEditing(row.item.id)
                            } else {
                                actions.onSplit(row.item.id)
                            }
                        },
                        onIndent = { actions.onIndent(row.item.id) },
                        onOutdent = { actions.onOutdent(row.item.id) },
                        onDone = { stopEditing(row.item.id) },
                    ),
                    dragHandleModifier = if (!dragEnabled) {
                        Modifier
                    } else {
                        Modifier.pointerInput(row.item.id) {
                            // No long press: the handle exists to be dragged, so the drag
                            // starts as soon as the finger moves past touch slop. The handle
                            // consumes the gesture, which is what keeps the same movement
                            // from scrolling the list or arming swipe-to-delete instead.
                            detectDragGestures(
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
                        }
                    },
                    onToggleDone = { done -> onToggleDone(row.item.id, done) },
                    onToggleCollapsed = { collapsed -> onToggleCollapsed(row.item.id, collapsed) },
                    onDelete = { onDelete(row.item.id) },
                )
            }
        }
    }
}

/**
 * Wraps a row so that swiping it either way deletes it.
 *
 * The delete is committed from `confirmValueChange` rather than from a settled state, because the
 * row is expected to disappear from the list the moment it is deleted — there is no dismissed row
 * left to observe. Undo lives at the screen level, in a snackbar.
 */
@Composable
private fun SwipeToDelete(
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val delete by rememberUpdatedState(onDelete)
    // The box asks for confirmation more than once on its way to a dismissed state, so the row
    // would otherwise be deleted twice by a single swipe.
    val alreadyDeleted = remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when {
                value == SwipeToDismissBoxValue.Settled -> false
                alreadyDeleted.value -> true
                else -> {
                    alreadyDeleted.value = true
                    delete()
                    true
                }
            }
        },
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        gesturesEnabled = enabled,
        backgroundContent = {
            if (state.targetValue != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (state.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        content = { content() },
    )
}
