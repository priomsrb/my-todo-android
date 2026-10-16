package dev.shafqat.mytodo.model

/**
 * What Enter makes of an item's text: [keep] stays on the item, [carry] goes onto the new one
 * below it.
 */
data class TextSplit(val keep: String, val carry: String)

/**
 * Splits [text] at the caret, or around a selection from [start] to [end] — which, like a text
 * editor's, is deleted first. Either end may come first, since a selection dragged backwards
 * arrives that way round.
 *
 * Both halves are trimmed where they meet: "buy milk | and eggs" gives "buy milk" and "and eggs",
 * not an item ending in a space and another starting with one. A caret at the end carries nothing,
 * which is plain Enter — a blank item below.
 */
fun splitText(text: String, start: Int, end: Int = start): TextSplit {
    val from = minOf(start, end).coerceIn(0, text.length)
    val to = maxOf(start, end).coerceIn(0, text.length)
    return TextSplit(
        keep = text.substring(0, from).trimEnd(),
        carry = text.substring(to).trimStart(),
    )
}
