package dev.shafqat.mytodo.data

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

    suspend fun renameList(listId: String, name: String)

    suspend fun deleteList(listId: String)

    /** Adds [text] as a child of [parentId], or at the top level when [parentId] is null. */
    suspend fun addItem(listId: String, text: String, parentId: String? = null): TodoItem

    suspend fun setItemDone(listId: String, itemId: String, done: Boolean)

    suspend fun setItemText(listId: String, itemId: String, text: String)

    /** Removes an item together with its entire subtree. */
    suspend fun deleteItem(listId: String, itemId: String)

    suspend fun setItemCollapsed(listId: String, itemId: String, collapsed: Boolean)

    /** Collapses or expands every item in the list that has children. */
    suspend fun setAllCollapsed(listId: String, collapsed: Boolean)
}
