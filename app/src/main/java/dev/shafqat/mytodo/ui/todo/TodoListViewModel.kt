package dev.shafqat.mytodo.ui.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.TodoRepository
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.todoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoListViewModel(
    application: Application,
    private val listId: String,
) : AndroidViewModel(application) {

    private val repository: TodoRepository = application.todoRepository

    val list: StateFlow<TodoList?> = repository.lists
        .map { lists -> lists.firstOrNull { it.id == listId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.lists.value.firstOrNull { it.id == listId },
        )

    fun addItem(text: String, parentId: String? = null) {
        viewModelScope.launch { repository.addItem(listId, text, parentId) }
    }

    fun setDone(itemId: String, done: Boolean) {
        viewModelScope.launch { repository.setItemDone(listId, itemId, done) }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { repository.deleteItem(listId, itemId) }
    }

    fun moveItem(itemId: String, targetIndex: Int, targetDepth: Int) {
        viewModelScope.launch { repository.moveItem(listId, itemId, targetIndex, targetDepth) }
    }

    fun setCollapsed(itemId: String, collapsed: Boolean) {
        viewModelScope.launch { repository.setItemCollapsed(listId, itemId, collapsed) }
    }

    fun setAllCollapsed(collapsed: Boolean) {
        viewModelScope.launch { repository.setAllCollapsed(listId, collapsed) }
    }
}
