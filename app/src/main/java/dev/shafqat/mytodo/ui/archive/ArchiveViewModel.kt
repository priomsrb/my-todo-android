package dev.shafqat.mytodo.ui.archive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.TodoRepository
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.todoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One list's archive: what was put there, and the ways of getting it back out. */
class ArchiveViewModel(
    application: Application,
    private val listId: String,
) : AndroidViewModel(application) {

    private val repository: TodoRepository = application.todoRepository

    /** The list the archive belongs to, for its name and colour. */
    val list: StateFlow<TodoList?> = repository.lists
        .map { lists -> lists.firstOrNull { it.id == listId } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.lists.value.firstOrNull { it.id == listId },
        )

    val items: StateFlow<List<TodoItem>> = repository.archives
        .map { it[listId].orEmpty() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.archives.value[listId].orEmpty(),
        )

    fun restore(itemId: String) {
        viewModelScope.launch { repository.restoreArchived(listId, setOf(itemId)) }
    }

    fun restoreAll() {
        val ids = items.value.map { it.id }.toSet()
        viewModelScope.launch { repository.restoreArchived(listId, ids) }
    }

    fun delete(itemId: String) {
        viewModelScope.launch { repository.deleteArchived(listId, setOf(itemId)) }
    }

    fun deleteAll() {
        val ids = items.value.map { it.id }.toSet()
        viewModelScope.launch { repository.deleteArchived(listId, ids) }
    }
}
