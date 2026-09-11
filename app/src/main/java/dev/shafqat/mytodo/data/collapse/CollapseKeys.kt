package dev.shafqat.mytodo.data.collapse

import dev.shafqat.mytodo.model.TodoItem

/**
 * Stable identity for an item, used to remember which items are collapsed.
 *
 * [TodoItem.id] is a fresh UUID every time a file is parsed, so it cannot be persisted. What does
 * survive a reload is where an item sits in the tree, so a key is the file name followed by the
 * text of each item on the path down to it. Siblings sharing the same text are disambiguated by
 * their position (`Buy milk#2`).
 *
 * The trade-off: editing an item's text forgets that it was collapsed. That is a better failure
 * than remembering the wrong item, and re-collapsing costs one tap.
 */
object CollapseKeys {

    /** Separator that cannot appear in a filename or in item text. */
    private const val SEPARATOR = "\u0000"

    fun key(fileName: String, path: List<String>): String =
        (listOf(fileName) + path).joinToString(SEPARATOR)

    fun belongsToFile(key: String, fileName: String): Boolean =
        key.startsWith(fileName + SEPARATOR)

    /** Re-points a key of [oldFileName] at [newFileName], for when a list is renamed. */
    fun reparent(key: String, oldFileName: String, newFileName: String): String =
        newFileName + key.removePrefix(oldFileName)

    /** Marks items collapsed wherever their key is in [keys]. Only items with children collapse. */
    fun applyTo(items: List<TodoItem>, fileName: String, keys: Set<String>): List<TodoItem> =
        mapWithKeys(items, fileName) { item, itemKey ->
            if (item.children.isEmpty()) item else item.copy(collapsed = itemKey in keys)
        }

    /** The key of the item with [itemId], or null if it is not in this tree. */
    fun keyOf(items: List<TodoItem>, fileName: String, itemId: String): String? {
        var found: String? = null
        mapWithKeys(items, fileName) { item, itemKey ->
            if (item.id == itemId) found = itemKey
            item
        }
        return found
    }

    /** Keys for every item that has children — what "collapse all" writes. */
    fun collapsibleKeys(items: List<TodoItem>, fileName: String): Set<String> {
        val keys = mutableSetOf<String>()
        mapWithKeys(items, fileName) { item, itemKey ->
            if (item.children.isNotEmpty()) keys += itemKey
            item
        }
        return keys
    }

    /** Walks the whole tree, handing [transform] each item together with its key. */
    private fun mapWithKeys(
        items: List<TodoItem>,
        fileName: String,
        transform: (TodoItem, String) -> TodoItem,
    ): List<TodoItem> = mapLevel(items, fileName, emptyList(), transform)

    private fun mapLevel(
        items: List<TodoItem>,
        fileName: String,
        parentPath: List<String>,
        transform: (TodoItem, String) -> TodoItem,
    ): List<TodoItem> {
        val occurrences = mutableMapOf<String, Int>()
        return items.map { item ->
            val occurrence = occurrences.merge(item.text, 1, Int::plus) ?: 1
            val segment = if (occurrence == 1) item.text else "${item.text}#$occurrence"
            val path = parentPath + segment
            transform(item, key(fileName, path))
                .copy(children = mapLevel(item.children, fileName, path, transform))
        }
    }
}
