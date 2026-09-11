package dev.shafqat.mytodo.data.markdown

import dev.shafqat.mytodo.model.TodoItem

/**
 * Reads the app's markdown format into a tree of [TodoItem]s.
 *
 * The canonical format is one tab per level with `- [ ]` / `- [X]` markers, but files are often
 * hand-edited or written by another tool, so parsing is deliberately lenient: space indentation
 * (any consistent width), `*` and `+` bullets, and lowercase `x` all work. Lines that are not
 * TODO items are kept in [MarkdownDocument.extraLines] rather than dropped.
 */
object MarkdownParser {

    private val ITEM_REGEX = Regex("""^([\t ]*)([-*+])\s+\[([ xX])]\s?(.*)$""")

    fun parse(text: String): MarkdownDocument {
        val lines = text.lines()

        // Indent width varies by file (one tab, two spaces, four spaces...). Derive the unit from
        // the smallest non-zero indent actually present instead of assuming one.
        val indentWidths = lines.mapNotNull { line ->
            ITEM_REGEX.matchEntire(line)?.groupValues?.get(1)?.let { indentWidth(it) }
        }
        val unit = indentWidths.filter { it > 0 }.minOrNull() ?: 1

        val roots = mutableListOf<MutableNode>()
        val stack = mutableListOf<MutableNode>()
        val extras = mutableMapOf<String, MutableList<String>>()
        var lastItemId = MarkdownDocument.PREAMBLE

        for (line in lines) {
            val match = ITEM_REGEX.matchEntire(line)
            if (match == null) {
                if (line.isNotBlank()) {
                    extras.getOrPut(lastItemId) { mutableListOf() }.add(line)
                }
                continue
            }

            val (indent, _, mark, text) = match.destructured
            // A line may only ever be one level deeper than the line above it, however far it is
            // indented; anything deeper is clamped so stray whitespace cannot create phantom levels.
            val level = (indentWidth(indent) / unit).coerceAtMost(stack.size)

            val node = MutableNode(
                item = TodoItem(text = text.trim(), done = mark.equals("x", ignoreCase = true)),
            )

            while (stack.size > level) stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) roots += node else stack.last().children += node
            stack += node

            lastItemId = node.item.id
        }

        return MarkdownDocument(
            items = roots.map { it.toTodoItem() },
            extraLines = extras.mapValues { (_, value) -> value.toList() },
        )
    }

    /** Tabs count as one level each; spaces count individually and are scaled by the file's unit. */
    private fun indentWidth(indent: String): Int =
        indent.sumOf { char -> if (char == '\t') TAB_WIDTH else 1 }

    private class MutableNode(
        val item: TodoItem,
        val children: MutableList<MutableNode> = mutableListOf(),
    ) {
        fun toTodoItem(): TodoItem = item.copy(children = children.map { it.toTodoItem() })
    }
}

/**
 * How many space-equivalents a tab counts for. Tabs are compared against space indentation using
 * this width, so a file mixing both still nests predictably.
 */
private const val TAB_WIDTH = 4
