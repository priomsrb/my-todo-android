package dev.shafqat.mytodo.model

/**
 * What "done" does to a list beyond graying a row out.
 *
 * Two different things, deliberately kept apart:
 * - [withoutCompleted] is a *view*: it drops finished rows from what is rendered and never touches
 *   the file, so ticking something off and hiding it is still undoable by untoggling the filter.
 * - [completedLast] is an *edit*: it reorders the tree, and therefore the markdown file, exactly as
 *   dragging every finished item to the bottom by hand would.
 */

/**
 * The tree with finished work filtered out.
 *
 * A done item that still has unfinished descendants stays, grayed out, because its children have
 * nowhere else to hang: dropping it would either orphan them or silently promote them a level.
 */
fun List<TodoItem>.withoutCompleted(): List<TodoItem> =
    mapNotNull { item ->
        val children = item.children.withoutCompleted()
        if (item.done && children.isEmpty()) null else item.copy(children = children)
    }

/**
 * The tree with finished items moved after their unfinished siblings, at every level.
 *
 * Order within each group is preserved, and a subtree travels with its item — the same rule
 * dragging follows.
 */
fun List<TodoItem>.completedLast(): List<TodoItem> {
    val sorted = map { it.copy(children = it.children.completedLast()) }
    val (unfinished, finished) = sorted.partition { !it.done }
    return unfinished + finished
}

/** True when any item anywhere in the tree is ticked off. */
fun List<TodoItem>.hasCompleted(): Boolean = any { it.done || it.children.hasCompleted() }
