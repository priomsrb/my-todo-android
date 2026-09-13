package dev.shafqat.mytodo.model

/**
 * One spoken sentence, cut into the items it names.
 *
 * Dictation arrives as a single run of text, but people list several things in one breath — "milk
 * and then bread and then bin bags". Splitting on the words that join them turns one press of the
 * voice widget into as many items as were actually spoken.
 *
 * The separators are deliberately narrow. A bare "and" joins two halves of one item far more often
 * than it starts a new one ("milk and bread"), so it is not one; "and then", "then", "next",
 * "and next" and "after that" are. Getting it wrong is recoverable — the widget offers an undo —
 * but a split that nobody asked for is more annoying than a missed one, so the rules err towards
 * leaving the sentence alone.
 */

/**
 * Longest first, so "and then" is consumed whole rather than leaving a stranded "and" behind on the
 * item before it.
 */
private val SeparatorPattern = Regex(
    """\b(?:and then|and next|after that|then|next)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Words that turn a "next" into part of the item rather than a break between two.
 *
 * "Book the car in next week" is one todo, not "book the car in" followed by "week". "then" needs
 * no such list: it is almost never the start of a noun phrase.
 */
private val NextIsNotASeparatorBefore = setOf(
    "week", "weeks", "month", "months", "year", "years", "day", "days", "time", "morning",
    "afternoon", "evening", "night", "weekend", "monday", "tuesday", "wednesday", "thursday",
    "friday", "saturday", "sunday", "one", "ones", "door", "to",
)

/** Punctuation a recogniser leaves at the edges once a sentence has been cut up. */
private val EdgeCharacters = charArrayOf(' ', '\t', ',', '.', ';', ':', '!', '?', '-', '—')

/**
 * The items named by [transcript], in the order they were spoken.
 *
 * A transcript that names only one thing comes back as a single-element list. Nothing is ever
 * dropped silently: if the split leaves nothing usable the whole transcript is kept as one item,
 * and only genuinely empty speech gives an empty list.
 */
fun splitDictation(transcript: String): List<String> {
    val fragments = mutableListOf<String>()
    var start = 0

    for (match in SeparatorPattern.findAll(transcript)) {
        if (!isSeparator(transcript, match)) continue
        fragments += transcript.substring(start, match.range.first)
        start = match.range.last + 1
    }
    fragments += transcript.substring(start)

    val items = fragments.map { it.trim(*EdgeCharacters) }.filter { it.isNotBlank() }
    if (items.isNotEmpty()) return items

    // Every fragment was blank, which means the transcript was nothing but separators and
    // punctuation. Whatever the user said, they said something — keep it rather than swallow it.
    return listOfNotNull(transcript.trim(*EdgeCharacters).takeIf { it.isNotBlank() })
}

/** Whether this match actually breaks the sentence, or is just the start of the next few words. */
private fun isSeparator(transcript: String, match: MatchResult): Boolean {
    if (!match.value.endsWith("next", ignoreCase = true)) return true

    val following = transcript.substring(match.range.last + 1)
        .trimStart()
        .takeWhile { it.isLetter() }
        .lowercase()
    return following !in NextIsNotASeparatorBefore
}
