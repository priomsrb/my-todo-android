package dev.shafqat.mytodo.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.StorageState
import dev.shafqat.mytodo.todoApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.todoApp

    val storageState: StateFlow<StorageState> = app.repository.storageState

    val folderUri: StateFlow<String?> = app.settings.todoFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Records the folder the user picked, holding on to the permission so it survives a reboot.
     * The repository follows the setting, so nothing else needs to be told about the change.
     */
    fun onFolderPicked(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModelScope.launch { app.settings.setTodoFolderUri(uri.toString()) }
    }

    /** Returns to the app-private folder, releasing the permission on the folder being dropped. */
    fun useDefaultFolder() {
        val current = folderUri.value?.let(Uri::parse)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (current != null) {
            runCatching { app.contentResolver.releasePersistableUriPermission(current, flags) }
        }
        viewModelScope.launch { app.settings.setTodoFolderUri(null) }
    }
}
