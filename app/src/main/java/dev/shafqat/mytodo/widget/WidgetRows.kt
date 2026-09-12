package dev.shafqat.mytodo.widget

import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.flattenVisible

/**
 * One row as a widget draws it.
 *
 * A widget outlives the process that drew it, so a row cannot carry a [dev.shafqat.mytodo.model
 * .TodoItem] id — those are fresh UUIDs on every parse. It carries a [CollapseKeys] key instead,
 * which is resolved back to an item at the moment the row is tapped.
 */
data class WidgetRow(
    val key: String,
    val text: String,
    val done: Boolean,
    val depth: Int,
)

/**
 * The rows a widget shows for this list: the same rows the app shows, keyed for the outside world.
 *
 * Collapsed subtrees stay folded and hidden completed items stay hidden, because a widget that
 * disagreed with the app about what a list contains would be worse than no widget.
 *
 * Keys come from the whole tree even though only the visible part is drawn: keys number same-named
 * siblings by position, so deriving them from an already-filtered tree would name the wrong items.
 */
fun TodoList.widgetRows(): List<WidgetRow> {
    val keys = CollapseKeys.keysById(items, fileName)

    return visibleItems.flattenVisible().mapNotNull { row ->
        keys[row.item.id]?.let { key ->
            WidgetRow(key = key, text = row.item.text, done = row.item.done, depth = row.depth)
        }
    }
}
