package dev.shafqat.mytodo.data

import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of truth for every TODO list.
 *
 * Implemented by [MarkdownTodoRepository], which keeps the tree in step with one markdown file
 * per list.
 */
interface TodoRepository {

    val lists: StateFlow<List<TodoList>>

    /** Whether the configured storage location can currently be read and written. */
    val storageState: StateFlow<StorageState>

    /** Re-reads the files, picking up changes made outside the app. */
    suspend fun refresh()

    suspend fun createList(name: String): TodoList

    /** Renames a list and its file, returning the id it now has — the file name may have changed. */
    suspend fun renameList(listId: String, name: String): String

    suspend fun deleteList(listId: String)

    /** Adds [text] as a child of [parentId], or at the top level when [parentId] is null. */
    suspend fun addItem(listId: String, text: String, parentId: String? = null): TodoItem

    /**
     * Adds [text] on the row straight after [afterItemId], at the same depth — what pressing Enter
     * while editing does. An item with children showing gets the new item as its first child,
     * which is where the row after it actually is.
     */
    suspend fun addItemAfter(listId: String, afterItemId: String, text: String = ""): TodoItem

    suspend fun setItemDone(listId: String, itemId: String, done: Boolean)

    suspend fun setItemText(listId: String, itemId: String, text: String)

    /** Removes an item together with its entire subtree. */
    suspend fun deleteItem(listId: String, itemId: String)

    /**
     * Moves an item, and everything under it, to [targetIndex] among the visible rows at
     * [targetDepth]. Both are interpreted as described on [dev.shafqat.mytodo.model.moveSubtree].
     */
    suspend fun moveItem(listId: String, itemId: String, targetIndex: Int, targetDepth: Int)

    /** Nests an item one level deeper, under the sibling above it. A no-op where that is illegal. */
    suspend fun indentItem(listId: String, itemId: String)

    /** Lifts an item one level out of its parent, landing it after its former siblings. */
    suspend fun outdentItem(listId: String, itemId: String)

    /**
     * Puts a deleted subtree back where it was, for undo. [targetIndex] and [targetDepth] are in
     * the coordinates of [dev.shafqat.mytodo.model.insertSubtree].
     */
    suspend fun restoreItem(listId: String, item: TodoItem, targetIndex: Int, targetDepth: Int)

    /** Reorders the list so ticked items follow their unticked siblings, at every level. */
    suspend fun moveCompletedToBottom(listId: String)

    /** Stores this list's colour and view options locally; they never reach the markdown file. */
    suspend fun setListPrefs(listId: String, prefs: ListPrefs)

    suspend fun setItemCollapsed(listId: String, itemId: String, collapsed: Boolean)

    /** Collapses or expands every item in the list that has children. */
    suspend fun setAllCollapsed(listId: String, collapsed: Boolean)
}
