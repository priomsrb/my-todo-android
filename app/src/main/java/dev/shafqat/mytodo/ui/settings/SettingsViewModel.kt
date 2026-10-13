package dev.shafqat.mytodo.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shafqat.mytodo.data.StorageState
import dev.shafqat.mytodo.model.CopyFormat
import dev.shafqat.mytodo.todoApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.todoApp

    val storageState: StateFlow<StorageState> = app.repository.storageState

    val folderUri: StateFlow<String?> = app.settings.todoFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val swipeToDeleteEnabled: StateFlow<Boolean> = app.settings.swipeToDeleteEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSwipeToDeleteEnabled(enabled: Boolean) {
        viewModelScope.launch { app.settings.setSwipeToDeleteEnabled(enabled) }
    }

    val widgetTopLevelOnly: StateFlow<Boolean> = app.settings.widgetTopLevelOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWidgetTopLevelOnly(enabled: Boolean) {
        viewModelScope.launch { app.settings.setWidgetTopLevelOnly(enabled) }
    }

    val widgetActionsAtTop: StateFlow<Boolean> = app.settings.widgetActionsAtTop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWidgetActionsAtTop(enabled: Boolean) {
        viewModelScope.launch { app.settings.setWidgetActionsAtTop(enabled) }
    }

    val widgetShowCount: StateFlow<Boolean> = app.settings.widgetShowCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWidgetShowCount(enabled: Boolean) {
        viewModelScope.launch { app.settings.setWidgetShowCount(enabled) }
    }

    val copyFormat: StateFlow<CopyFormat> = app.settings.copyFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopyFormat.Default)

    fun setCopyFormat(format: CopyFormat) {
        viewModelScope.launch { app.settings.setCopyFormat(format) }
    }

    /**
     * Records the folder the user picked, holding on to the permission so it survives a reboot.
     * The repository follows the setting, so nothing else needs to be told about the change.
     */
    fun onFolderPicked(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModelScope.launch { app.settings.setTodoFolderUri(uri.toString()) }
    }

    private val _moveState = MutableStateFlow<MoveState>(MoveState.Idle)
    val moveState: StateFlow<MoveState> = _moveState

    /** Moves the files into the picked folder, taking its permission first so it outlives a reboot. */
    fun onMoveTargetPicked(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
        _moveState.value = MoveState.Moving
        viewModelScope.launch {
            _moveState.value = try {
                app.moveFolderTo(uri)
                MoveState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MoveState.Failed(e.message ?: "Could not move the folder.")
            }
        }
    }

    fun dismissMoveResult() {
        if (_moveState.value !is MoveState.Moving) _moveState.value = MoveState.Idle
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

sealed interface MoveState {
    data object Idle : MoveState
    data object Moving : MoveState
    data object Done : MoveState
    data class Failed(val message: String) : MoveState
}
