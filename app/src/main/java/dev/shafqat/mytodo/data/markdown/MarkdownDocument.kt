package dev.shafqat.mytodo.data.markdown

import dev.shafqat.mytodo.model.TodoItem

/**
 * The parsed contents of one markdown file.
 *
 * [extraLines] holds lines the app does not understand so a hand-edited file survives a round trip.
 * Each entry is keyed by the id of the item the lines follow; [PREAMBLE] keys the lines that come
 * before the first item. Blank lines are not preserved — they are normalized away on write.
 */
data class MarkdownDocument(
    val items: List<TodoItem> = emptyList(),
    val extraLines: Map<String, List<String>> = emptyMap(),
) {
    companion object {
        /** Key under which lines preceding the first TODO item are stored. */
        const val PREAMBLE = ""
    }
}
