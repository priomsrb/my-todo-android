package dev.shafqat.mytodo.widget

import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.doneCount
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.totalCount

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
    /**
     * How many unfinished items sit beneath this row without rows of their own — non-zero only when
     * the widget is showing top-level items alone. A done parent with a count still has work in it.
     */
    val hiddenOpenCount: Int = 0,
)

/**
 * The rows a widget shows for this list: the same rows the app shows, keyed for the outside world.
 *
 * Collapsed subtrees stay folded and hidden completed items stay hidden, because a widget that
 * disagreed with the app about what a list contains would be worse than no widget.
 *
 * With [topLevelOnly] the widget draws only the list's top-level items, each carrying a count of
 * the unfinished work folded beneath it; collapse state does not matter then, since nothing below
 * the top is drawn anyway.
 *
 * Keys come from the whole tree even though only the visible part is drawn: keys number same-named
 * siblings by position, so deriving them from an already-filtered tree would name the wrong items.
 */
fun TodoList.widgetRows(topLevelOnly: Boolean = false): List<WidgetRow> {
    val keys = CollapseKeys.keysById(items, fileName)

    if (topLevelOnly) {
        return visibleItems.mapNotNull { item ->
            keys[item.id]?.let { key ->
                WidgetRow(
                    key = key,
                    text = item.text,
                    done = item.done,
                    depth = 0,
                    hiddenOpenCount = item.children.openCount(),
                )
            }
        }
    }

    return visibleItems.flattenVisible().mapNotNull { row ->
        keys[row.item.id]?.let { key ->
            WidgetRow(key = key, text = row.item.text, done = row.item.done, depth = row.depth)
        }
    }
}

/**
 * The header's "done/total" for this list: every item, or only the top-level ones when that is all
 * the widget shows, so the count always describes the rows underneath it.
 */
fun TodoList.widgetProgress(topLevelOnly: Boolean): Pair<Int, Int> =
    if (topLevelOnly) {
        items.count { it.done } to items.size
    } else {
        items.doneCount() to items.totalCount()
    }

/**
 * Unfinished items anywhere in this subtree. Blank rows are gaps the user left, not work, so they
 * are not counted.
 */
private fun List<TodoItem>.openCount(): Int =
    sumOf { (if (!it.done && it.text.isNotBlank()) 1 else 0) + it.children.openCount() }
