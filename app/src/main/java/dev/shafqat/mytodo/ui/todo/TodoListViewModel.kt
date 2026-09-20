package dev.shafqat.mytodo.ui.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.TodoRepository
import dev.shafqat.mytodo.data.settings.SettingsRepository
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.itemsFromText
import dev.shafqat.mytodo.model.visibleRowOf
import dev.shafqat.mytodo.todoApp
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

/**
 * What one paste added, kept only long enough for its undo snackbar.
 *
 * The items themselves and where they went, not their ids: a reload re-parses the files and gives
 * every item a new id, and the widget redraw that follows any edit is enough to cause one inside
 * the few seconds the snackbar is up. [rowCount] is carried because it is what the snackbar says,
 * and a pasted item may be a whole subtree.
 */
data class AddedItems(
    val items: List<TodoItem>,
    val atTop: Boolean,
    val rowCount: Int,
)

class TodoListViewModel(
    application: Application,
    private val listId: String,
) : AndroidViewModel(application) {

    private val repository: TodoRepository = application.todoRepository
    private val settings: SettingsRepository = application.todoApp.settings

    val list: StateFlow<TodoList?> = repository.lists
        .map { lists -> lists.firstOrNull { it.id == listId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.lists.value.firstOrNull { it.id == listId },
        )

    /** Whether a swipe on a row deletes it — a setting, off until the user turns it on. */
    val swipeToDeleteEnabled: StateFlow<Boolean> = settings.swipeToDeleteEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The item that should open for typing, set whenever one is created. */
    private val _focusItemId = MutableStateFlow<String?>(null)
    val focusItemId: StateFlow<String?> = _focusItemId.asStateFlow()

    /** Where "Add from text" last put a paste, so the dialog opens on the same choice. */
    val addFromTextAtTop: StateFlow<Boolean> = settings.addFromTextAtTop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The most recent paste, while the snackbar offering to undo it is still up. */
    private val _pendingAddUndo = MutableStateFlow<AddedItems?>(null)
    val pendingAddUndo: StateFlow<AddedItems?> = _pendingAddUndo.asStateFlow()

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

    /**
     * Enter with the caret at the start: a new item on the row above [beforeItemId], open for
     * typing. The item it went above is left alone, text and children both.
     */
    fun addItemBefore(beforeItemId: String) {
        viewModelScope.launch {
            _focusItemId.value = repository.addItemBefore(listId, beforeItemId).id
        }
    }

    /**
     * "Add from text": a pasted list, parsed and added in one go.
     *
     * The position is passed in as well as remembered, because the dialog is where the choice was
     * made and the setting is only there so the next paste starts from the same place.
     */
    fun addFromText(text: String, atTop: Boolean) {
        val added = itemsFromText(text)
        if (added.isEmpty()) return

        viewModelScope.launch {
            settings.setAddFromTextAtTop(atTop)
            repository.addItems(listId, added, atTop)
            _pendingAddUndo.value = AddedItems(added, atTop, added.rowCount())
        }
    }

    /** Undo: takes the pasted batch back off, leaving anything that arrived since. */
    fun undoAdd() {
        val added = _pendingAddUndo.value ?: return
        _pendingAddUndo.value = null
        viewModelScope.launch { repository.removeItems(listId, added.items, added.atTop) }
    }

    fun dismissAddUndo() {
        _pendingAddUndo.value = null
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

/** Every row a paste created, nested ones included — one item may be a whole subtree. */
private fun List<TodoItem>.rowCount(): Int = sumOf { 1 + it.children.rowCount() }
