package dev.shafqat.mytodo.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.TodoRepository
import dev.shafqat.mytodo.model.SearchHit
import dev.shafqat.mytodo.model.searchItems
import dev.shafqat.mytodo.todoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Search across every list.
 *
 * Matching runs over the lists already in memory rather than over the files, so results update as
 * items are edited and nothing has to be re-read from storage to answer a keystroke.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TodoRepository = application.todoRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<SearchHit>> =
        combine(repository.lists, _query) { lists, query -> lists.searchItems(query) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun setQuery(query: String) {
        _query.value = query
    }
}
