package dev.shafqat.mytodo.model

/**
 * One item that matched a search, with enough context to show where it lives.
 *
 * [path] is the text of each ancestor, outermost first, so a nested match can be shown as
 * "Groceries › Weekend › Bread" without the result having to carry the tree around.
 */
data class SearchHit(
    val listId: String,
    val listName: String,
    val item: TodoItem,
    val path: List<String>,
)

/**
 * Every item across [this] whose text contains [query], case-insensitively.
 *
 * Collapsed items are searched too: a search that could not see into a folded subtree would be
 * worse than useless. Results come back in list order and then tree order, which is the order the
 * user would find them by scrolling.
 */
fun List<TodoList>.searchItems(query: String): List<SearchHit> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()

    return flatMap { list -> list.items.hitsIn(list, needle, emptyList()) }
}

private fun List<TodoItem>.hitsIn(
    list: TodoList,
    needle: String,
    path: List<String>,
): List<SearchHit> = flatMap { item ->
    val self = if (item.text.contains(needle, ignoreCase = true)) {
        listOf(SearchHit(listId = list.id, listName = list.name, item = item, path = path))
    } else {
        emptyList()
    }
    self + item.children.hitsIn(list, needle, path + item.text)
}
