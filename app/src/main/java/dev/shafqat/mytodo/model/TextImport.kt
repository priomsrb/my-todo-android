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
 * A blank line between two items is kept, as the blank row it looks like: the gaps someone put in
 * a list are part of how they read it, and a paste that closed them up would come back changed.
 *
 * This is deliberately *not* [dev.shafqat.mytodo.data.markdown.MarkdownParser]. That one reads the
 * app's own files, where a line it does not recognise is someone's hand-written content and must
 * be preserved untouched; here every line is something the user meant to add, so the job is the
 * opposite one — turn all of it into items and drop nothing.
 */

/** One line's markers stripped off, ready to be placed in the tree. */
private sealed interface ParsedLine {

    /** Where the line sits, in space-equivalents, or null for a line with no indent to trust. */
    val indent: Int?

    /** A line naming an item: whether it was ticked, and what is left to show. */
    data class Item(
        override val indent: Int,
        val done: Boolean,
        val text: String,
    ) : ParsedLine

    /**
     * A blank row: an empty line, whose indent is null because whatever whitespace it holds is
     * accidental, or a marker with nothing written after it, which is indented like any line.
     */
    data class Gap(override val indent: Int?) : ParsedLine
}

/**
 * The bullet, the checkbox and the text, all but the last optional.
 *
 * A bullet must be followed by whitespace — so "3.5 kg flour" and "e-mail Sam" keep their text —
 * or by the end of the line, which is a bullet with nothing written after it. A checkbox is
 * matched with or without a bullet in front of it, since both are pasted in the wild.
 */
private val LineRegex = Regex(
    """^([\t ]*)(?:([-*+•‣▪◦·]|\d+[.)])(?:[ \t]+|$))?(?:\[([ xX])][ \t]*)?(.*)$""",
)

/**
 * A line that is nothing but a run of punctuation — `---`, `***`, `___`, `- - -` — is a horizontal
 * rule, which names nothing to add. Two characters at least: a lone bullet is a bullet with nothing
 * written after it, which is a blank row rather than a rule.
 */
private val RuleRegex = Regex("""^[-*+•‣▪◦·_=~]{2,}$""")

/** How many space-equivalents a tab counts for, so a paste mixing both still nests predictably. */
private const val TabWidth = 4

/**
 * The items named by [text], nested as its indentation nests them.
 *
 * Horizontal rules are skipped and so are the blank lines at either end; every other line becomes
 * an item, blank ones included. A line may only ever be one level deeper than the line above it,
 * however far it is indented, so an odd paste cannot conjure levels nobody typed.
 */
fun itemsFromText(text: String): List<TodoItem> {
    val lines = text.lines().mapNotNull(::parseLine)
    if (lines.none { it is ParsedLine.Item }) return emptyList()

    val indents = resolveIndents(lines)
    // Text copied out of a code block or a quoted reply arrives indented as a whole. The shallowest
    // line that knows its own indent is what "not indented" means for this paste; without that
    // baseline every line after the first would read as a child of the one before it.
    val baseline = lines.mapNotNull { it.indent }.min()
    val steps = indents.map { (it - baseline).coerceAtLeast(0) }
    // Indent width varies by source (one tab, two spaces, four spaces...), so derive the unit from
    // the smallest step actually present rather than assuming one.
    val unit = steps.filter { it > 0 }.minOrNull() ?: 1

    val roots = mutableListOf<Node>()
    val stack = mutableListOf<Node>()

    lines.forEachIndexed { index, line ->
        val level = (steps[index] / unit).coerceAtMost(stack.size)
        val node = when (line) {
            is ParsedLine.Item -> Node(TodoItem(text = line.text, done = line.done))
            is ParsedLine.Gap -> Node(TodoItem(text = ""))
        }

        while (stack.size > level) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) roots += node else stack.last().children += node
        stack += node
    }

    // A gap at either end of the paste separates nothing — and a paste ends in one whenever the
    // text ends in a newline, which most does. Trimmed here, once the tree is built, rather than
    // line by line: a blank row that turned out to have children is a row, not a gap, and the
    // level it holds for them has to survive being the first line of the paste.
    return roots
        .dropWhile { it.isGap }
        .dropLastWhile { it.isGap }
        .map { it.toTodoItem() }
}

/**
 * Every line's indent, with an empty line's taken from the first line below it that has one.
 *
 * A gap therefore opens the group under it rather than closing the one above: that is how a blank
 * line reads in a list pasted from anywhere else, and a copy made here puts a marker on a blank row
 * that needs to say otherwise. A gap with nothing below it at all is the trailing newline every
 * paste ends with; it lands at the top level and is trimmed off once the tree is built.
 */
private fun resolveIndents(lines: List<ParsedLine>): List<Int> {
    val indents = IntArray(lines.size)
    var below = 0
    for (index in lines.indices.reversed()) {
        val own = lines[index].indent
        if (own != null) below = own
        indents[index] = own ?: below
    }
    return indents.toList()
}

/** Strips one line's markers, or returns null for a line that names nothing at all. */
private fun parseLine(line: String): ParsedLine? {
    // An empty line is taken at its word and not at its whitespace: trailing spaces on a blank
    // line are leftovers, not a level, and the line's place is read off the item below it instead.
    if (line.isBlank()) return ParsedLine.Gap(indent = null)
    if (RuleRegex.matches(line.filterNot(Char::isWhitespace))) return null

    val match = LineRegex.matchEntire(line) ?: return null
    val (indent, _, mark, rest) = match.destructured

    val text = rest.trim()
    // A marker with nothing written after it is a blank row too — a `- [ ]` copied out of the app,
    // or a bullet someone left empty. This one knows its own indent.
    if (text.isEmpty()) return ParsedLine.Gap(indentWidth(indent))

    return ParsedLine.Item(
        indent = indentWidth(indent),
        done = mark.equals("x", ignoreCase = true),
        text = text,
    )
}

/** How far one line is pushed in, counting a tab as [TabWidth] spaces. */
private fun indentWidth(indent: String): Int =
    indent.sumOf { char -> if (char == '\t') TabWidth else 1 }

private class Node(
    val item: TodoItem,
    val children: MutableList<Node> = mutableListOf(),
) {
    /** A blank row with nothing under it: a gap, rather than a row that happens to be empty. */
    val isGap: Boolean get() = item.text.isEmpty() && children.isEmpty()

    fun toTodoItem(): TodoItem = item.copy(children = children.map { it.toTodoItem() })
}
