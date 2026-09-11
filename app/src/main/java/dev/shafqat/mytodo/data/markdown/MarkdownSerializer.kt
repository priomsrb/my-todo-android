package dev.shafqat.mytodo.data.markdown

import dev.shafqat.mytodo.model.TodoItem

/**
 * Writes a tree of [TodoItem]s back out in the canonical format: one tab per level, `- [ ]` for
 * open items and `- [X]` for done ones, regardless of how the file was originally indented.
 *
 * Lines the parser could not understand are written back out after the item they followed, so
 * hand-edited files keep their extra content.
 */
object MarkdownSerializer {

    fun serialize(document: MarkdownDocument): String = buildString {
        document.extraLines[MarkdownDocument.PREAMBLE]?.forEach { appendLine(it) }
        appendItems(document.items, depth = 0, extraLines = document.extraLines)
    }

    fun serialize(items: List<TodoItem>): String = serialize(MarkdownDocument(items))

    private fun StringBuilder.appendItems(
        items: List<TodoItem>,
        depth: Int,
        extraLines: Map<String, List<String>>,
    ) {
        for (item in items) {
            append("\t".repeat(depth))
            append("- [")
            append(if (item.done) "X" else " ")
            append("] ")
            appendLine(item.text)

            extraLines[item.id]?.forEach { appendLine(it) }
            appendItems(item.children, depth + 1, extraLines)
        }
    }
}
