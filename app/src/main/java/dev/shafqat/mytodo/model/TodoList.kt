package dev.shafqat.mytodo.model

/**
 * One named list of TODOs, backed by a single markdown file inside the user's todo folder.
 *
 * Both [id] and [name] are derived from [fileName]: the filename is the only identity a list has.
 * That keeps ids stable across restarts (widget deep links and widget configs depend on it) and
 * enforces the rule that a list's name never comes from inside the file.
 */
data class TodoList(
    val fileName: String,
    val items: List<TodoItem> = emptyList(),
) {
    val id: String get() = fileName
    val name: String get() = displayNameFor(fileName)
}

/** Turns a list name into a safe markdown filename, e.g. "Shopping list" -> "shopping-list.md". */
fun fileNameFor(listName: String): String {
    val slug = listName.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "untitled" }
    return "$slug.md"
}

/** Turns a filename back into a display name, e.g. "shopping-list.md" -> "Shopping list". */
fun displayNameFor(fileName: String): String {
    val stem = fileName.removeSuffix(".md").removeSuffix(".markdown")
    val words = stem.replace(Regex("[-_]+"), " ").trim()
    if (words.isEmpty()) return "Untitled"
    return words.replaceFirstChar { it.uppercase() }
}

/** True when [fileName] is a markdown file this app should treat as a list. */
fun isTodoFile(fileName: String): Boolean =
    fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".markdown", ignoreCase = true)

/**
 * Makes [desired] unique against [existing] by appending a counter, e.g. "notes-2.md".
 * Comparison is case-insensitive because SAF folders may sit on case-insensitive storage.
 */
fun uniqueFileName(desired: String, existing: Collection<String>): String {
    val taken = existing.map { it.lowercase() }.toSet()
    if (desired.lowercase() !in taken) return desired

    val stem = desired.removeSuffix(".md")
    var counter = 2
    while ("$stem-$counter.md".lowercase() in taken) counter++
    return "$stem-$counter.md"
}
