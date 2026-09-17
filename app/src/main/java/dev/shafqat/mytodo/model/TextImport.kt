package dev.shafqat.mytodo.model

/**
 * Pasted or typed text, read as the items it lists.
 *
 * A list that already exists somewhere else — in a note, a message, another todo app — is a list
 * the user should not have to retype a row at a time. Whatever marks its lines up, the shape is the
 * same: one item per line, nested by how far the line is indented.
 *
 * Parsing is therefore as lenient as the formats people actually paste. `-`, `*`, `+` and `•`
 * bullets, numbered lines, `- [ ]` / `- [x]` checkboxes and bare lines with no marker at all are
 * all accepted, mixed within one paste, indented with tabs or with any number of spaces.
 *
 * This is deliberately *not* [dev.shafqat.mytodo.data.markdown.MarkdownParser]. That one reads the
 * app's own files, where a line it does not recognise is someone's hand-written content and must
 * be preserved untouched; here every non-blank line is something the user meant to add, so the
 * job is the opposite one — turn all of it into items and drop nothing.
 */

/**
 * One line's markers stripped off: its indent in space-equivalents, whether it was ticked, and
 * what is left to show.
 */
private data class ParsedLine(
    val indent: Int,
    val done: Boolean,
    val text: String,
)

/**
 * The bullet, the checkbox and the text, all but the last optional.
 *
 * A bullet must be followed by whitespace, so "3.5 kg flour" and "e-mail Sam" keep their text; a
 * checkbox is matched with or without a bullet in front of it, since both are pasted in the wild.
 */
private val LineRegex = Regex(
    """^([\t ]*)(?:([-*+•‣▪◦·]|\d+[.)])[ \t]+)?(?:\[([ xX])][ \t]*)?(.*)$""",
)

/** A line that is only punctuation — a `---` rule, a stray bullet — names nothing to add. */
private val RuleRegex = Regex("""^[-*+•‣▪◦·_=~\s]*$""")

/** How many space-equivalents a tab counts for, so a paste mixing both still nests predictably. */
private const val TabWidth = 4

/**
 * The items named by [text], nested as its indentation nests them.
 *
 * Blank lines and horizontal rules are skipped; everything else becomes an item. A line may only
 * ever be one level deeper than the line above it, however far it is indented, so an odd paste
 * cannot conjure levels nobody typed.
 */
fun itemsFromText(text: String): List<TodoItem> {
    val lines = text.lines().mapNotNull(::parseLine)
    if (lines.isEmpty()) return emptyList()

    // Text copied out of a code block or a quoted reply arrives indented as a whole. The shallowest
    // line is what "not indented" means for this paste; without that baseline every line after the
    // first would read as a child of the one before it.
    val baseline = lines.minOf { it.indent }
    val indents = lines.map { it.indent - baseline }
    // Indent width varies by source (one tab, two spaces, four spaces...), so derive the unit from
    // the smallest step actually present rather than assuming one.
    val unit = indents.filter { it > 0 }.minOrNull() ?: 1

    val roots = mutableListOf<Node>()
    val stack = mutableListOf<Node>()

    lines.forEachIndexed { index, line ->
        val level = (indents[index] / unit).coerceAtMost(stack.size)
        val node = Node(TodoItem(text = line.text, done = line.done))

        while (stack.size > level) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) roots += node else stack.last().children += node
        stack += node
    }

    return roots.map { it.toTodoItem() }
}

/** Strips one line's markers, or returns null for a line that names no item. */
private fun parseLine(line: String): ParsedLine? {
    if (line.isBlank() || RuleRegex.matches(line)) return null

    val match = LineRegex.matchEntire(line) ?: return null
    val (indent, _, mark, rest) = match.destructured

    val text = rest.trim()
    if (text.isEmpty()) return null

    return ParsedLine(
        indent = indent.sumOf { char -> if (char == '\t') TabWidth else 1 },
        done = mark.equals("x", ignoreCase = true),
        text = text,
    )
}

private class Node(
    val item: TodoItem,
    val children: MutableList<Node> = mutableListOf(),
) {
    fun toTodoItem(): TodoItem = item.copy(children = children.map { it.toTodoItem() })
}
