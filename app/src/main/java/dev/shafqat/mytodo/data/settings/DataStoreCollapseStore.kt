package dev.shafqat.mytodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.data.collapse.CollapseStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists collapse state in its own DataStore file.
 *
 * Deliberately separate from the settings store: every write re-emits the whole preferences object,
 * so sharing a file would make each collapse toggle look like a settings change to anything
 * observing settings.
 */
private val Context.collapseDataStore: DataStore<Preferences> by preferencesDataStore(name = "collapse")

class DataStoreCollapseStore(private val context: Context) : CollapseStore {

    override suspend fun collapsedKeys(): Set<String> =
        context.collapseDataStore.data.map { it[COLLAPSED_KEYS].orEmpty() }.first()

    override suspend fun setCollapsed(key: String, collapsed: Boolean) {
        context.collapseDataStore.edit { preferences ->
            val current = preferences[COLLAPSED_KEYS].orEmpty()
            preferences[COLLAPSED_KEYS] = if (collapsed) current + key else current - key
        }
    }

    override suspend fun replaceKeysForFile(fileName: String, keys: Set<String>) {
        context.collapseDataStore.edit { preferences ->
            val others = preferences[COLLAPSED_KEYS].orEmpty()
                .filterNot { CollapseKeys.belongsToFile(it, fileName) }
            preferences[COLLAPSED_KEYS] = others.toSet() + keys
        }
    }

    override suspend fun renameFile(oldFileName: String, newFileName: String) {
        context.collapseDataStore.edit { preferences ->
            preferences[COLLAPSED_KEYS] = preferences[COLLAPSED_KEYS].orEmpty()
                .map { key ->
                    if (CollapseKeys.belongsToFile(key, oldFileName)) {
                        CollapseKeys.reparent(key, oldFileName, newFileName)
                    } else {
                        key
                    }
                }
                .toSet()
        }
    }

    private companion object {
        val COLLAPSED_KEYS = stringSetPreferencesKey("collapsed_keys")
    }
}
