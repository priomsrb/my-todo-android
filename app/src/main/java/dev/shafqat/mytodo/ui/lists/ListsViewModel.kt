package dev.shafqat.mytodo.ui.lists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.StorageState
import dev.shafqat.mytodo.data.TodoRepository
import dev.shafqat.mytodo.model.CopyFormat
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.todoApp
import dev.shafqat.mytodo.todoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TodoRepository = application.todoRepository

    val lists: StateFlow<List<TodoList>> = repository.lists

    val storageState: StateFlow<StorageState> = repository.storageState

    /** Which format "Copy list" writes — a setting, markdown checkboxes until it is changed. */
    val copyFormat: StateFlow<CopyFormat> = application.todoApp.settings.copyFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopyFormat.Default)

    fun createList(name: String) {
        viewModelScope.launch { repository.createList(name) }
    }

    fun renameList(listId: String, name: String) {
        viewModelScope.launch { repository.renameList(listId, name) }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch { repository.deleteList(listId) }
    }

    /** Recolours a card. Null means "no colour of its own", not palette entry zero. */
    fun setColor(listId: String, colorIndex: Int?) {
        val current = lists.value.firstOrNull { it.id == listId }?.prefs ?: ListPrefs.Default
        viewModelScope.launch { repository.setListPrefs(listId, current.copy(colorIndex = colorIndex)) }
    }
}
