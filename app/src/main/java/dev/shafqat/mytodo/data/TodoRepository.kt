package dev.shafqat.mytodo.data

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of truth for every TODO list.
 *
 * Phase 0 ships [InMemoryTodoRepository]; Phase 1 swaps in a markdown/SAF-backed implementation
 * behind this same interface, so no UI code has to change.
 */
interface TodoRepository {

    val lists: StateFlow<List<TodoList>>

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
}
