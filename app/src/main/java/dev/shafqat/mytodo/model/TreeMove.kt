package dev.shafqat.mytodo.model

/**
 * Moving items around the tree.
 *
 * A drag is expressed as two numbers: where the item should land among the visible rows
 * ([moveSubtree]'s `targetIndex`) and how deeply it should nest there (`targetDepth`). Everything
 * else — which item becomes its parent, which items become its siblings — follows from those.
 *
 * The whole subtree always travels with the item, which is what makes a tree the right shape for
 * this data: the move is one detach and one re-attach, never a walk over descendants.
 */

/**
 * One row of a tree flattened for moving.
 *
 * An *expanded* item appears with its children stripped, because each child gets its own row.
 * A *collapsed* item keeps its children, because they have no rows of their own — that is what
 * lets a collapsed subtree be dragged as a unit and rebuilt losslessly.
 */
data class FlatRow(
    val item: TodoItem,
    val depth: Int,
)

/** Flattens the visible tree into rows that [rebuildTree] can turn back into a tree. */
fun List<TodoItem>.flattenForMove(depth: Int = 0): List<FlatRow> =
    flatMap { item ->
        if (item.collapsed && item.children.isNotEmpty()) {
            listOf(FlatRow(item, depth))
        } else {
            listOf(FlatRow(item.copy(children = emptyList()), depth)) +
                item.children.flattenForMove(depth + 1)
        }
    }

/**
 * Rebuilds a tree from rows, re-parenting by depth.
 *
 * A row deeper than the row above it becomes that row's child; the same depth makes it a sibling.
 * A depth that skips levels is clamped, so a malformed row list still produces a valid tree.
 */
fun List<FlatRow>.rebuildTree(): List<TodoItem> {
    val roots = mutableListOf<MutableNode>()
    val ancestors = mutableListOf<MutableNode>()

    for (row in this) {
        val node = MutableNode(row.item)
        val depth = row.depth.coerceIn(0, ancestors.size)

        while (ancestors.size > depth) ancestors.removeAt(ancestors.lastIndex)
        if (ancestors.isEmpty()) roots += node else ancestors.last().children += node
        ancestors += node
    }

    // Converted only once the whole list has been read, since a node keeps gaining children
    // for as long as deeper rows keep following it.
    return roots.map { it.toTodoItem() }
}

/**
 * The depths an item may take if inserted at [index] among [rows].
 *
 * The rules that keep the tree well-formed:
 * - it may go at most one level deeper than the row above it, so it can become that row's child
 *   but never its grandchild;
 * - it may not go shallower than the row below it, which would otherwise be re-parented under it;
 * - it may not become the child of a *collapsed* row, since it would vanish the moment it landed.
 */
fun allowedDepthRange(rows: List<FlatRow>, index: Int): IntRange {
    val previous = rows.getOrNull(index - 1)
    val next = rows.getOrNull(index)

    val maxDepth = when {
        previous == null -> 0
        previous.item.collapsed && previous.item.children.isNotEmpty() -> previous.depth
        else -> previous.depth + 1
    }
    val minDepth = (next?.depth ?: 0).coerceAtMost(maxDepth)

    return minDepth..maxDepth
}

/**
 * Moves the item with [itemId], and everything under it, to [targetIndex] at [targetDepth].
 *
 * [targetIndex] is an index among the visible rows of the tree **with the dragged subtree already
 * removed**, which is the coordinate space the drag UI works in. [targetDepth] is clamped to
 * [allowedDepthRange], so the caller can pass whatever the finger suggests.
 */
fun List<TodoItem>.moveSubtree(itemId: String, targetIndex: Int, targetDepth: Int): List<TodoItem> {
    val subtree = findItem(itemId) ?: return this
    val remaining = removeItem(itemId).flattenForMove()

    val index = targetIndex.coerceIn(0, remaining.size)
    val depth = targetDepth.coerceIn(allowedDepthRange(remaining, index))

    val moved = FlatRow(subtree, depth)
    return (remaining.take(index) + moved + remaining.drop(index)).rebuildTree()
}

/**
 * Where [itemId] currently sits among the visible rows — the drag's starting index, and the index
 * that puts it back exactly where it was.
 */
fun List<TodoItem>.visibleIndexOf(itemId: String): Int =
    flattenVisible().indexOfFirst { it.item.id == itemId }

private class MutableNode(private val item: TodoItem) {

    /** Children gathered from the rows below this one. */
    val children = mutableListOf<MutableNode>()

    /**
     * A collapsed row arrives with its hidden children still attached, so they come first and any
     * newly dropped children follow them.
     */
    fun toTodoItem(): TodoItem =
        item.copy(children = item.children + children.map { it.toTodoItem() })
}
