package dev.shafqat.mytodo.ui.lists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.TodoRepository
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.todoRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TodoRepository = application.todoRepository

    val lists: StateFlow<List<TodoList>> = repository.lists

    fun createList(name: String) {
        viewModelScope.launch { repository.createList(name) }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch { repository.deleteList(listId) }
    }
}
