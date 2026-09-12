package dev.shafqat.mytodo

import android.app.Application
import android.content.Context
import android.net.Uri
import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.settings.DataStoreCollapseStore
import dev.shafqat.mytodo.data.settings.DataStoreListPrefsStore
import dev.shafqat.mytodo.data.settings.SettingsRepository
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.data.store.SafDirectoryStore
import dev.shafqat.mytodo.widget.updateTodoWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manual dependency wiring.
 *
 * One repository instance is shared across screens, and it follows the folder setting: whenever the
 * user picks a different folder the repository is pointed at the matching store and reloads.
 */
class MyTodoApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val repository: MarkdownTodoRepository by lazy {
        MarkdownTodoRepository(
            scope = applicationScope,
            ioDispatcher = Dispatchers.IO,
            collapseStore = DataStoreCollapseStore(this),
            listPrefsStore = DataStoreListPrefsStore(this),
        )
    }

    /** The folder currently attached, so an unchanged setting never triggers a reload. */
    private var attachedFolderUri: String? = null
    private var hasAttachedStore = false

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            settings.todoFolderUri.collectLatest { folderUri -> attachStore(folderUri) }
        }
        applicationScope.launch { keepWidgetsInStep() }
    }

    /**
     * Redraws the home-screen widgets whenever the lists change.
     *
     * The widgets follow the repository rather than the files, so an edit shows up on the home
     * screen without waiting for a save. Debounced because `lists` changes on every keystroke while
     * an item is being typed, and the first value is dropped because it is just the initial load —
     * the widgets are drawing themselves at that point anyway.
     */
    private suspend fun keepWidgetsInStep() {
        repository.lists
            .drop(1)
            .distinctUntilChanged()
            .debounce(WIDGET_UPDATE_DEBOUNCE_MILLIS)
            .collectLatest { updateTodoWidgets() }
    }

    private suspend fun attachStore(folderUri: String?) {
        // Reloading clears the lists briefly, which the UI would show as an empty screen. Only do
        // it when the folder has actually changed.
        if (hasAttachedStore && folderUri == attachedFolderUri) return
        attachedFolderUri = folderUri
        hasAttachedStore = true

        if (folderUri == null) {
            // No folder chosen yet: keep lists in a directory the app owns, seeded on first run.
            repository.useStore(
                store = LocalDirectoryStore(File(filesDir, DEFAULT_FOLDER_NAME)),
                seedWhenEmpty = true,
            )
            return
        }

        val store = SafDirectoryStore(this, Uri.parse(folderUri))
        if (store.isAccessible()) {
            repository.useStore(store)
        } else {
            // The permission was revoked or the folder is gone. Say so rather than silently
            // falling back to a different set of files.
            repository.markUnavailable(store.label)
        }
    }

    private companion object {
        const val DEFAULT_FOLDER_NAME = "todo"
        const val WIDGET_UPDATE_DEBOUNCE_MILLIS = 1_000L
    }
}

val Context.todoApp: MyTodoApplication
    get() = applicationContext as MyTodoApplication

/** Convenience accessor used by the ViewModel factories. */
val Application.todoRepository: MarkdownTodoRepository
    get() = (this as MyTodoApplication).repository
