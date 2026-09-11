package dev.shafqat.mytodo

import android.app.Application
import dev.shafqat.mytodo.data.InMemoryTodoRepository
import dev.shafqat.mytodo.data.TodoRepository

/**
 * Manual dependency wiring.
 *
 * A single repository instance is shared across screens so edits made in one list are visible on
 * the home grid. Phase 1 replaces the implementation here with the markdown/SAF-backed one.
 */
class MyTodoApplication : Application() {

    val repository: TodoRepository by lazy { InMemoryTodoRepository() }
}

/** Convenience accessor used by the ViewModel factories. */
val Application.todoRepository: TodoRepository
    get() = (this as MyTodoApplication).repository
