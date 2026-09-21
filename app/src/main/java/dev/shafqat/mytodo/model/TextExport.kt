package dev.shafqat.mytodo.model

/**
 * A tree of items written back out as text, for the clipboard.
 *
 * The inverse of [itemsFromText]: a list that belongs somewhere else — in a message, a note, a
 * shopping list read out to someone else — is a list the user should not have to retype out of the
 * app any more than into it.
 *
 * Two formats, because a copy is made for two different reasons. [CopyFormat.Checkboxes] writes the
 * app's own markdown, so what comes back through "Add from text" is what left, ticks included.
 * [CopyFormat.Bullets] drops the checkbox syntax for somewhere it would only read as clutter.
 *
 * This is deliberately *not*
 * [dev.shafqat.mytodo.data.markdown.MarkdownSerializer], for the same reason [itemsFromText] is not
 * the parser: that one writes the user's file and must carry the lines the app does not understand
 * back out with it, while this one writes a fragment for somewhere else entirely and carries
 * nothing but the items.
 */

/** How a copied list is written out. */
enum class CopyFormat {
    /** `- [ ]` and `- [X]`, the app's own file format — pastes back in with its ticks intact. */
    Checkboxes,

    /** Plain `-` bullets, for somewhere checkbox syntax would only be noise. Ticks are dropped. */
    Bullets,
    ;

    companion object {
        /** What a copy is in whatever format, until the user picks the other one in settings. */
        val Default = Checkboxes

        /** The format [name] names, falling back to [Default] for anything unrecognised. */
        fun fromName(name: String?): CopyFormat = entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * [items] as text, one line per item, one tab per level of nesting.
 *
 * No trailing newline: the text goes straight into a message box or a note, where a blank line at
 * the end is something the user then has to delete.
 */
fun textFromItems(items: List<TodoItem>, format: CopyFormat = CopyFormat.Default): String =
    buildString { appendItems(items, depth = 0, format = format) }.trimEnd('\n')

private fun StringBuilder.appendItems(items: List<TodoItem>, depth: Int, format: CopyFormat) {
    for (item in items) {
        // A row left blank is one the user is still typing into, or has just pressed Enter one time
        // too many on; it names nothing to copy. One with children keeps its line anyway, since
        // dropping it would promote them a level.
        if (item.text.isBlank() && item.children.isEmpty()) continue

        val marker = when (format) {
            CopyFormat.Checkboxes -> if (item.done) "- [X]" else "- [ ]"
            CopyFormat.Bullets -> "-"
        }
        append("\t".repeat(depth))
        appendLine("$marker ${item.text.trim()}".trimEnd())

        appendItems(item.children, depth + 1, format)
    }
}
