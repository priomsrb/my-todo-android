package dev.shafqat.mytodo.model

import java.util.UUID

/**
 * A single TODO, which may itself contain any number of children at any depth.
 *
 * The tree shape (rather than a flat list with depth markers) is deliberate: moving a subtree
 * during drag-and-drop becomes a single node move, and markdown serialization is a recursive walk.
 *
 * [collapsed] is UI-only state and is never written to the markdown file.
 */
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
    val collapsed: Boolean = false,
    val children: List<TodoItem> = emptyList(),
)

/** A TODO item paired with the depth it is rendered at. Depth 0 is a top-level item. */
data class VisibleRow(
    val item: TodoItem,
    val depth: Int,
)

/**
 * Flattens a tree into the rows to render, skipping the children of collapsed items.
 *
 * Everything that renders or reorders the tree goes through this, so `LazyColumn` stays flat
 * while the underlying data keeps its nesting.
 */
fun List<TodoItem>.flattenVisible(depth: Int = 0): List<VisibleRow> =
    flatMap { item ->
        val row = VisibleRow(item, depth)
        if (item.collapsed || item.children.isEmpty()) {
            listOf(row)
        } else {
            listOf(row) + item.children.flattenVisible(depth + 1)
        }
    }

/** Applies [transform] to the item with [id] anywhere in the tree, leaving the rest untouched. */
fun List<TodoItem>.updateItem(id: String, transform: (TodoItem) -> TodoItem): List<TodoItem> =
    map { item ->
        if (item.id == id) {
            transform(item)
        } else {
            item.copy(children = item.children.updateItem(id, transform))
        }
    }

/** Appends [item] as the last child of [parentId], or at the top level when [parentId] is null. */
fun List<TodoItem>.addItem(item: TodoItem, parentId: String? = null): List<TodoItem> =
    if (parentId == null) {
        this + item
    } else {
        updateItem(parentId) { parent -> parent.copy(children = parent.children + item) }
    }

/** Removes the item with [id] and, with it, its entire subtree. */
fun List<TodoItem>.removeItem(id: String): List<TodoItem> =
    filterNot { it.id == id }.map { it.copy(children = it.children.removeItem(id)) }

/** Finds the item with [id] anywhere in the tree. */
fun List<TodoItem>.findItem(id: String): TodoItem? {
    forEach { item ->
        if (item.id == id) return item
        item.children.findItem(id)?.let { return it }
    }
    return null
}

/** Total number of items in the tree, including every descendant. */
fun List<TodoItem>.totalCount(): Int = sumOf { 1 + it.children.totalCount() }

/** Number of items in the tree that are ticked off, including every descendant. */
fun List<TodoItem>.doneCount(): Int = sumOf { (if (it.done) 1 else 0) + it.children.doneCount() }
