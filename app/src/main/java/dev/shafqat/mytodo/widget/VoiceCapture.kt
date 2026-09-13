package dev.shafqat.mytodo.widget

import dev.shafqat.mytodo.data.MarkdownTodoRepository

/**
 * Filing what was dictated onto a list, and taking it back off again.
 *
 * Split out of [VoiceCaptureActivity] because it is the half worth testing: the activity around it
 * is the dictation sheet's plumbing, which needs a real recogniser and a real microphone, while
 * this is an ordinary edit against an ordinary repository.
 */

/**
 * Adds [texts] to the top of a list and reports the ids created, so an undo has something exact to
 * remove.
 *
 * The top, because something captured in passing is something you have not dealt with yet, and a
 * list you speak at is a list that grows — appending would file every new item below everything
 * already seen and settled. Within the batch each one goes below the last, so a sentence naming
 * three things reads top to bottom in the order it was spoken.
 *
 * Written through immediately rather than left on the autosave debounce, for the same reason a
 * widget's tick is: nothing keeps the process alive once the confirmation has gone.
 */
internal suspend fun MarkdownTodoRepository.addDictated(
    listId: String,
    texts: List<String>,
): List<String> {
    if (texts.isEmpty()) return emptyList()

    val added = texts.mapIndexed { index, text -> addItemAt(listId, text, index).id }
    flushPendingSaves()
    return added
}

/** Undo: removes exactly the items [addDictated] created, and nothing that arrived since. */
internal suspend fun MarkdownTodoRepository.removeDictated(listId: String, itemIds: List<String>) {
    if (itemIds.isEmpty()) return

    itemIds.forEach { deleteItem(listId, it) }
    flushPendingSaves()
}
