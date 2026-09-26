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
 *
 * Items whose ids are in [keep] stay too, finished or not — the item just ticked off, left in view
 * for a moment so a tick on the wrong row can be taken back where it was made.
 */
fun List<TodoItem>.withoutCompleted(keep: Set<String> = emptySet()): List<TodoItem> =
    mapNotNull { item ->
        val children = item.children.withoutCompleted(keep)
        if (item.done && children.isEmpty() && item.id !in keep) null else item.copy(children = children)
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

/**
 * This item and everything beneath it, ticked off (or not) as one.
 *
 * What a widget showing top-level items alone does with a tick: the row stands for the whole
 * subtree, since nothing under it has a row of its own to be ticked from.
 */
fun TodoItem.withSubtreeDone(done: Boolean): TodoItem =
    copy(done = done, children = children.map { it.withSubtreeDone(done) })

/**
 * This item with the ticks it had in [before] put back, at every depth, keeping the ids it has now.
 *
 * The undo for [withSubtreeDone]: unticking a parent that was just ticked as a whole returns each
 * child to what it was, rather than leaving them all done or clearing them all. Children are
 * matched by position and text; where they no longer line up with [before] — something added,
 * removed or renamed in between — that level keeps the ticks it has, since a snapshot that no longer
 * describes the list would put ticks on the wrong items.
 */
fun TodoItem.withDoneFrom(before: TodoItem): TodoItem {
    val sameShape = children.size == before.children.size &&
        children.zip(before.children).all { (now, then) -> now.text == then.text }
    return copy(
        done = before.done,
        children = if (sameShape) {
            children.zip(before.children) { now, then -> now.withDoneFrom(then) }
        } else {
            children
        },
    )
}
