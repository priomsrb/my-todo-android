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

    /**
     * Each list's archived items, by list id: finished subtrees moved out of the list into a
     * sibling `.archive.md` file. A list with nothing archived has no entry.
     */
    val archives: StateFlow<Map<String, List<TodoItem>>>

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
     * Adds [text] as a top-level row at [index] among the tree's rows, pushing everything from
     * there down. Index 0 puts it at the very top; the index is into the whole tree, not the
     * filtered view, so hiding finished items cannot move where it lands.
     */
    suspend fun addItemAt(listId: String, text: String, index: Int): TodoItem

    /**
     * Adds [text] on the row straight after [afterItemId], at the same depth — what pressing Enter
     * while editing does. An item with children showing gets the new item as its first child,
     * which is where the row after it actually is.
     */
    suspend fun addItemAfter(listId: String, afterItemId: String, text: String = ""): TodoItem

    /**
     * Adds [text] on the row straight before [beforeItemId], at the same depth — what pressing
     * Enter with the caret at the start of an item does. The item it lands above keeps its
     * children: the new row is its sibling, not its parent.
     */
    suspend fun addItemBefore(listId: String, beforeItemId: String, text: String = ""): TodoItem

    /**
     * Adds whole subtrees as top-level rows, at the top of the list when [atTop] or after
     * everything already there otherwise. Used by "Add from text", where one paste is many items.
     */
    suspend fun addItems(listId: String, items: List<TodoItem>, atTop: Boolean = false)

    /**
     * Undo for [addItems]: takes that batch back off the list.
     *
     * Matched by where it landed and what it said, never by id — a reload re-parses the files and
     * hands every item a fresh id, and a widget redraw is enough to trigger one while the undo
     * snackbar is still up. A no-op if the list no longer starts (or ends) with what was added, so
     * an undo arriving after the user has moved things cannot take the wrong rows.
     */
    suspend fun removeItems(listId: String, items: List<TodoItem>, atTop: Boolean = false)

    suspend fun setItemDone(listId: String, itemId: String, done: Boolean)

    /** Ticks (or unticks) an item together with every item beneath it. */
    suspend fun setSubtreeDone(listId: String, itemId: String, done: Boolean)

    /**
     * Puts back the ticks an item and its descendants had in [before], a copy of it taken earlier —
     * the undo for [setSubtreeDone]. Parts of the subtree that have changed shape since are left.
     */
    suspend fun restoreSubtreeDone(listId: String, itemId: String, before: TodoItem)

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

    /**
     * Moves every finished subtree out of the list and onto the end of its archive, returning what
     * was archived (empty, and nothing changed, when there was nothing to archive).
     */
    suspend fun archiveCompleted(listId: String): List<TodoItem>

    /**
     * Undo for [archiveCompleted]: takes [archived] back off the archive and puts the list back as
     * it was in [before]. When the list has been edited since, [before] would throw that edit away,
     * so the archived items are returned to the end of the list instead.
     */
    suspend fun undoArchive(listId: String, before: List<TodoItem>, archived: List<TodoItem>)

    /** Moves archived items back onto the end of the list, unticked, and out of the archive. */
    suspend fun restoreArchived(listId: String, itemIds: Set<String>)

    /** Removes archived items for good. */
    suspend fun deleteArchived(listId: String, itemIds: Set<String>)
}
