package dev.shafqat.mytodo.data.collapse

import dev.shafqat.mytodo.model.TodoItem

/**
 * Stable identity for an item, used to remember which items are collapsed and to name an item to
 * anything living outside the app's memory — a home-screen widget, chiefly.
 *
 * [TodoItem.id] is a fresh UUID every time a file is parsed, so it cannot be persisted or handed
 * out. What does survive a reload is where an item sits in the tree, so a key is the file name
 * followed by the text of each item on the path down to it. Siblings sharing the same text are
 * disambiguated by their position (`Buy milk#2`).
 *
 * The trade-off: editing an item's text forgets that it was collapsed, and a widget tapped after
 * such an edit ticks nothing rather than the wrong thing. Both are better failures than acting on
 * the wrong item.
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

    /**
     * The id of the item [key] names, or null when nothing in this tree answers to it.
     *
     * The inverse of [keyOf], and what turns a tap on a widget — which can only carry a key — back
     * into an item the repository can act on. A key that no longer resolves means the item was
     * edited or removed since the widget was drawn, and the tap is dropped.
     */
    fun idOf(items: List<TodoItem>, fileName: String, key: String): String? {
        var found: String? = null
        mapWithKeys(items, fileName) { item, itemKey ->
            if (itemKey == key) found = item.id
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

    /**
     * Every item's key, by id — one walk instead of one walk per item.
     *
     * Always derive these from the *whole* tree. Keys number same-named siblings by position, so a
     * tree that has already had rows filtered out of it produces keys that name different items.
     */
    fun keysById(items: List<TodoItem>, fileName: String): Map<String, String> {
        val keys = mutableMapOf<String, String>()
        mapWithKeys(items, fileName) { item, itemKey ->
            keys[item.id] = itemKey
            item
        }
        return keys
    }

    /** Keys of the items that are collapsed right now — used to re-key state after a move. */
    fun collapsedKeys(items: List<TodoItem>, fileName: String): Set<String> {
        val keys = mutableSetOf<String>()
        mapWithKeys(items, fileName) { item, itemKey ->
            if (item.collapsed && item.children.isNotEmpty()) keys += itemKey
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
