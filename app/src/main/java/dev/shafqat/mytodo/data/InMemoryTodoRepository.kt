package dev.shafqat.mytodo.data

import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.addItem
import dev.shafqat.mytodo.model.fileNameFor
import dev.shafqat.mytodo.model.removeItem
import dev.shafqat.mytodo.model.updateItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Skeleton repository holding everything in memory, seeded with sample data.
 *
 * Nothing is persisted: this exists so the UI is real and clickable before markdown file IO
 * lands in Phase 1.
 */
class InMemoryTodoRepository(
    seed: List<TodoList> = sampleLists(),
) : TodoRepository {

    private val _lists = MutableStateFlow(seed)
    override val lists: StateFlow<List<TodoList>> = _lists.asStateFlow()

    override suspend fun createList(name: String): TodoList {
        val list = TodoList(name = name, fileName = fileNameFor(name))
        _lists.update { it + list }
        return list
    }

    override suspend fun renameList(listId: String, name: String) {
        updateList(listId) { it.copy(name = name) }
    }

    override suspend fun deleteList(listId: String) {
        _lists.update { lists -> lists.filterNot { it.id == listId } }
    }

    override suspend fun addItem(listId: String, text: String, parentId: String?): TodoItem {
        val item = TodoItem(text = text)
        updateList(listId) { list -> list.copy(items = list.items.addItem(item, parentId)) }
        return item
    }

    override suspend fun setItemDone(listId: String, itemId: String, done: Boolean) {
        updateItemIn(listId, itemId) { it.copy(done = done) }
    }

    override suspend fun setItemText(listId: String, itemId: String, text: String) {
        updateItemIn(listId, itemId) { it.copy(text = text) }
    }

    override suspend fun deleteItem(listId: String, itemId: String) {
        updateList(listId) { list -> list.copy(items = list.items.removeItem(itemId)) }
    }

    override suspend fun setItemCollapsed(listId: String, itemId: String, collapsed: Boolean) {
        updateItemIn(listId, itemId) { it.copy(collapsed = collapsed) }
    }

    private fun updateItemIn(listId: String, itemId: String, transform: (TodoItem) -> TodoItem) {
        updateList(listId) { list -> list.copy(items = list.items.updateItem(itemId, transform)) }
    }

    private fun updateList(listId: String, transform: (TodoList) -> TodoList) {
        _lists.update { lists ->
            lists.map { if (it.id == listId) transform(it) else it }
        }
    }
}

/** Sample data for the skeleton, including a branch nested three levels deep. */
private fun sampleLists(): List<TodoList> = listOf(
    TodoList(
        name = "Groceries",
        fileName = "groceries.md",
        items = listOf(
            TodoItem(
                text = "Fruit & veg",
                children = listOf(
                    TodoItem(text = "Apples"),
                    TodoItem(text = "Spinach", done = true),
                ),
            ),
            TodoItem(
                text = "Dinner party",
                children = listOf(
                    TodoItem(
                        text = "Starters",
                        children = listOf(
                            TodoItem(text = "Olives"),
                            TodoItem(text = "Sourdough", done = true),
                        ),
                    ),
                    TodoItem(text = "Dessert"),
                ),
            ),
            TodoItem(text = "Coffee beans", done = true),
        ),
    ),
    TodoList(
        name = "House",
        fileName = "house.md",
        items = listOf(
            TodoItem(
                text = "Kitchen",
                children = listOf(
                    TodoItem(text = "Fix the tap"),
                    TodoItem(text = "Replace bulb", done = true),
                ),
            ),
            TodoItem(text = "Book a plumber"),
        ),
    ),
    TodoList(
        name = "Reading",
        fileName = "reading.md",
        items = listOf(
            TodoItem(text = "Finish the Kotlin coroutines guide"),
            TodoItem(text = "Compose drag-and-drop write-up", done = true),
        ),
    ),
)
