package dev.shafqat.mytodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persisted user settings. Currently just where the todo files live. */
class SettingsRepository(private val context: Context) {

    /** The SAF tree URI of the user's todo folder, or null while the app-private default is in use. */
    val todoFolderUri: Flow<String?> = context.dataStore.data.map { it[TODO_FOLDER_URI] }

    suspend fun setTodoFolderUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) preferences.remove(TODO_FOLDER_URI) else preferences[TODO_FOLDER_URI] = uri
        }
    }

    private companion object {
        val TODO_FOLDER_URI = stringPreferencesKey("todo_folder_uri")
    }
}
