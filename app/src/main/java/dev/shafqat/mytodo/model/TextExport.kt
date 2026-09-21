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
 * Blank rows go out as blank lines in both, and [itemsFromText] reads them back as blank rows: the
 * gaps a list is grouped by survive the round trip, the same as its nesting does.
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
        // A blank row between two groups copies as a blank line: a bullet with nothing after it
        // would only read as a mistake in a message, and the gap is the whole point of the row.
        // Read back in, an empty line is a blank row again.
        if (item.text.isBlank() && item.children.isEmpty() && depth == 0) {
            appendLine()
            continue
        }

        // Everything else takes a marker, a blank row that is nested or has children included: an
        // empty line says nothing about which group it belongs to, and both of those depend on it.
        // Read back in, a marker with nothing written after it is a blank row again.
        val marker = when (format) {
            CopyFormat.Checkboxes -> if (item.done) "- [X]" else "- [ ]"
            CopyFormat.Bullets -> "-"
        }
        append("\t".repeat(depth))
        appendLine("$marker ${item.text.trim()}".trimEnd())

        appendItems(item.children, depth + 1, format)
    }
}
