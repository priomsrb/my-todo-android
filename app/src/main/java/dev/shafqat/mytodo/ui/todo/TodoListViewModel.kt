package dev.shafqat.mytodo.ui.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.TodoRepository
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.visibleRowOf
import dev.shafqat.mytodo.todoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A deleted subtree, kept only long enough for the undo snackbar to offer it back.
 *
 * Where it sat is recorded as well as what it was, since putting an item back at the end of the
 * list is not undoing anything.
 */
data class DeletedItem(
    val item: TodoItem,
    val index: Int,
    val depth: Int,
)

class TodoListViewModel(
    application: Application,
    private val listId: String,
) : AndroidViewModel(application) {

    private val repository: TodoRepository = application.todoRepository

    val list: StateFlow<TodoList?> = repository.lists
        .map { lists -> lists.firstOrNull { it.id == listId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.lists.value.firstOrNull { it.id == listId },
        )

    /** The item that should open for typing, set whenever one is created. */
    private val _focusItemId = MutableStateFlow<String?>(null)
    val focusItemId: StateFlow<String?> = _focusItemId.asStateFlow()

    /** The most recent deletion, while the snackbar offering to undo it is still up. */
    private val _pendingUndo = MutableStateFlow<DeletedItem?>(null)
    val pendingUndo: StateFlow<DeletedItem?> = _pendingUndo.asStateFlow()

    /** Set once a rename has landed, so the screen can follow the list to its new id. */
    private val _renamedListId = MutableStateFlow<String?>(null)
    val renamedListId: StateFlow<String?> = _renamedListId.asStateFlow()

    private val items: List<TodoItem> get() = list.value?.items.orEmpty()

    // --- adding and editing -----------------------------------------------------------------

    /** Adds an empty item at the end of the list and opens it for typing. */
    fun addItem() {
        viewModelScope.launch { _focusItemId.value = repository.addItem(listId, "").id }
    }

    /**
     * The widget's "+": an empty item at the very top of the list, open for typing.
     *
     * The top rather than the end, for the same reason a dictated item goes there — something you
     * reached for the widget to jot down is something you have not dealt with yet, and appending
     * would file it below everything already seen and settled. The screen's own "Add item" still
     * appends, because there the list is in front of you and the end is where you are looking.
     */
    fun addItemAtTop() {
        viewModelScope.launch {
            // A cold start from the widget can arrive here before the folder has been read, and an
            // edit to a list that is not loaded yet is silently dropped. The timeout is a giving-up
            // point, not an expectation: past it there is nothing to add to and nothing to focus.
            withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { list.first { it != null } } ?: return@launch
            _focusItemId.value = repository.addItemAt(listId, "", index = 0).id
        }
    }

    /** Enter: a new item on the row after [afterItemId], open for typing. */
    fun addItemAfter(afterItemId: String) {
        viewModelScope.launch {
            _focusItemId.value = repository.addItemAfter(listId, afterItemId).id
        }
    }

    fun setText(itemId: String, text: String) {
        viewModelScope.launch { repository.setItemText(listId, itemId, text) }
    }

    /**
     * Editing stopped. An item left empty is removed rather than kept as a blank row: it is what
     * the user gets for pressing Enter one time too many, and it cannot be told apart on screen
     * from a row they meant to keep.
     */
    fun finishEditing(itemId: String) {
        if (_focusItemId.value == itemId) _focusItemId.value = null

        val item = items.findItem(itemId) ?: return
        if (item.text.isBlank() && item.children.isEmpty()) {
            viewModelScope.launch { repository.deleteItem(listId, itemId) }
        }
    }

    fun indent(itemId: String) {
        viewModelScope.launch { repository.indentItem(listId, itemId) }
    }

    fun outdent(itemId: String) {
        viewModelScope.launch { repository.outdentItem(listId, itemId) }
    }

    fun setDone(itemId: String, done: Boolean) {
        viewModelScope.launch { repository.setItemDone(listId, itemId, done) }
    }

    // --- deleting and undoing ----------------------------------------------------------------

    /** Deletes an item, remembering where it was so the snackbar can put it back. */
    fun deleteItem(itemId: String) {
        val item = items.findItem(itemId) ?: return
        val row = items.visibleRowOf(itemId)

        viewModelScope.launch {
            repository.deleteItem(listId, itemId)
            // An item deleted while still blank is one the user never finished typing; offering to
            // restore an empty row would be noise.
            if (item.text.isNotBlank() || item.children.isNotEmpty()) {
                _pendingUndo.value = row?.let { DeletedItem(item, it.index, it.depth) }
            }
        }
    }

    fun undoDelete() {
        val deleted = _pendingUndo.value ?: return
        _pendingUndo.value = null
        viewModelScope.launch {
            repository.restoreItem(listId, deleted.item, deleted.index, deleted.depth)
        }
    }

    fun dismissUndo() {
        _pendingUndo.value = null
    }

    // --- structure and view options ----------------------------------------------------------

    fun moveItem(itemId: String, targetIndex: Int, targetDepth: Int) {
        viewModelScope.launch { repository.moveItem(listId, itemId, targetIndex, targetDepth) }
    }

    fun setCollapsed(itemId: String, collapsed: Boolean) {
        viewModelScope.launch { repository.setItemCollapsed(listId, itemId, collapsed) }
    }

    fun setAllCollapsed(collapsed: Boolean) {
        viewModelScope.launch { repository.setAllCollapsed(listId, collapsed) }
    }

    fun setHideCompleted(hide: Boolean) {
        updatePrefs { it.copy(hideCompleted = hide) }
    }

    fun setColor(colorIndex: Int?) {
        updatePrefs { it.copy(colorIndex = colorIndex) }
    }

    fun moveCompletedToBottom() {
        viewModelScope.launch { repository.moveCompletedToBottom(listId) }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            val newId = repository.renameList(listId, name)
            if (newId != listId) _renamedListId.value = newId
        }
    }

    private fun updatePrefs(transform: (ListPrefs) -> ListPrefs) {
        val current = list.value?.prefs ?: ListPrefs.Default
        viewModelScope.launch { repository.setListPrefs(listId, transform(current)) }
    }

    private companion object {
        /** How long [addItemAtTop] waits for storage on a cold start before giving up. */
        const val LOAD_TIMEOUT_MILLIS = 5_000L
    }
}
