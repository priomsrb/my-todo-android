package dev.shafqat.mytodo.model

import java.util.UUID

/**
 * One named list of TODOs.
 *
 * Each list is backed by its own markdown file ([fileName]) inside the folder the user picks in
 * settings. File IO arrives in Phase 1; for now lists live only in memory.
 */
data class TodoList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fileName: String,
    val items: List<TodoItem> = emptyList(),
)

/** Turns a list name into a safe markdown filename, e.g. "Shopping list" -> "shopping-list.md". */
fun fileNameFor(listName: String): String {
    val slug = listName.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "untitled" }
    return "$slug.md"
}
