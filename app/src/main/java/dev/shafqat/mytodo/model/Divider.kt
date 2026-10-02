package dev.shafqat.mytodo.model

/** How a divider row is ruled: one line for `-`, two for `=`. */
enum class DividerStyle { Single, Double }

/** An item read as a divider: [label] is the text between the rules, possibly empty. */
data class Divider(val style: DividerStyle, val label: String)

/**
 * Reads [text] as a divider if it starts and ends with the same `-` or `=` — `-- Fruit --`,
 * `=Weekend=`, or just `---`. A lone `-` or `=` is plain text. This is a view only: the file keeps
 * the text exactly as typed, and tapping the row edits that text.
 */
fun dividerOf(text: String): Divider? {
    val t = text.trim()
    if (t.length < 2) return null
    val c = t.first()
    if (c != '-' && c != '=') return null
    if (t.last() != c) return null
    return Divider(
        style = if (c == '-') DividerStyle.Single else DividerStyle.Double,
        label = t.trim(c).trim(),
    )
}
