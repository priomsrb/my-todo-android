package dev.shafqat.mytodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.shafqat.mytodo.data.prefs.ListPrefsStore
import dev.shafqat.mytodo.model.ListPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists per-list colour and view options in their own DataStore file.
 *
 * A third file alongside settings and collapse, for the same reason those two are separate: a write
 * re-emits the entire preferences object, so anything sharing a file sees every unrelated change as
 * its own.
 */
private val Context.listPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "list_prefs")

class DataStoreListPrefsStore(private val context: Context) : ListPrefsStore {

    override suspend fun all(): Map<String, ListPrefs> =
        context.listPrefsDataStore.data.map { it.toPrefsMap() }.first()

    override suspend fun setPrefs(fileName: String, prefs: ListPrefs) {
        context.listPrefsDataStore.edit { preferences ->
            preferences.write(fileName, prefs)
        }
    }

    override suspend fun renameFile(oldFileName: String, newFileName: String) {
        context.listPrefsDataStore.edit { preferences ->
            val moved = preferences.toPrefsMap()[oldFileName] ?: return@edit
            preferences.write(oldFileName, ListPrefs.Default)
            preferences.write(newFileName, moved)
        }
    }

    override suspend fun clear(fileName: String) {
        context.listPrefsDataStore.edit { preferences ->
            preferences.write(fileName, ListPrefs.Default)
        }
    }

    /**
     * Rebuilds the map from the flat key space.
     *
     * Preferences has no nested values, so each list's fields live under `color::<file>` and
     * `hide_completed::<file>` and are gathered back up by their suffix.
     */
    private fun Preferences.toPrefsMap(): Map<String, ListPrefs> {
        val result = mutableMapOf<String, ListPrefs>()
        asMap().forEach { (key, value) ->
            val name = key.name
            when {
                name.startsWith(COLOR_PREFIX) -> {
                    val file = name.removePrefix(COLOR_PREFIX)
                    result[file] = result.getOrDefault(file, ListPrefs.Default)
                        .copy(colorIndex = value as? Int)
                }
                name.startsWith(HIDE_COMPLETED_PREFIX) -> {
                    val file = name.removePrefix(HIDE_COMPLETED_PREFIX)
                    result[file] = result.getOrDefault(file, ListPrefs.Default)
                        .copy(hideCompleted = value as? Boolean == true)
                }
            }
        }
        return result
    }

    /** Defaults are stored as absence, so a list that is back to normal leaves nothing behind. */
    private fun androidx.datastore.preferences.core.MutablePreferences.write(
        fileName: String,
        prefs: ListPrefs,
    ) {
        val colorKey = intPreferencesKey(COLOR_PREFIX + fileName)
        val hideKey = booleanPreferencesKey(HIDE_COMPLETED_PREFIX + fileName)

        if (prefs.colorIndex == null) remove(colorKey) else set(colorKey, prefs.colorIndex)
        if (!prefs.hideCompleted) remove(hideKey) else set(hideKey, true)
    }

    private companion object {
        const val COLOR_PREFIX = "color::"
        const val HIDE_COMPLETED_PREFIX = "hide_completed::"
    }
}
