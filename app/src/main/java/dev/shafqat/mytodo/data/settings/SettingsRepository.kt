package dev.shafqat.mytodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persisted user settings: where the todo files live, and how rows behave. */
class SettingsRepository(private val context: Context) {

    /**
     * The SAF tree URI of the user's todo folder, or null while the app-private default is in use.
     *
     * Deliberately deduplicated: every DataStore write re-emits the whole preferences object, and
     * collapse state shares this DataStore, so without this a collapse toggle would look like a
     * folder change and send the repository through a full reload.
     */
    val todoFolderUri: Flow<String?> = context.dataStore.data
        .map { it[TODO_FOLDER_URI] }
        .distinctUntilChanged()

    suspend fun setTodoFolderUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) preferences.remove(TODO_FOLDER_URI) else preferences[TODO_FOLDER_URI] = uri
        }
    }

    /**
     * Whether swiping a row deletes it. Off unless the user turns it on.
     *
     * Off by default because the gesture is easy to trigger by accident — the rows are the same
     * place a finger lands to scroll — and every row already carries a delete button. Deduplicated
     * for the same reason as [todoFolderUri]: this flow reaches the list screen, and an unrelated
     * write to this DataStore should not look like a change here.
     */
    val swipeToDeleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[SWIPE_TO_DELETE] ?: false }
        .distinctUntilChanged()

    suspend fun setSwipeToDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SWIPE_TO_DELETE] = enabled }
    }

    /**
     * Whether "Add from text" puts the paste at the top of the list rather than the end.
     *
     * The dialog offers both and remembers whichever was used last, so a habit only has to be
     * expressed once. It ships off: the list is in front of you when you paste into it, and the
     * end is where the eye already is.
     */
    val addFromTextAtTop: Flow<Boolean> = context.dataStore.data
        .map { it[ADD_FROM_TEXT_AT_TOP] ?: false }
        .distinctUntilChanged()

    suspend fun setAddFromTextAtTop(atTop: Boolean) {
        context.dataStore.edit { preferences -> preferences[ADD_FROM_TEXT_AT_TOP] = atTop }
    }

    private companion object {
        val TODO_FOLDER_URI = stringPreferencesKey("todo_folder_uri")
        val SWIPE_TO_DELETE = booleanPreferencesKey("swipe_to_delete")
        val ADD_FROM_TEXT_AT_TOP = booleanPreferencesKey("add_from_text_at_top")
    }
}
