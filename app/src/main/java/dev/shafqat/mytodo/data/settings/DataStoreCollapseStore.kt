package dev.shafqat.mytodo.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.data.collapse.CollapseStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists collapse state in the app's DataStore, alongside the folder setting. */
class DataStoreCollapseStore(private val context: Context) : CollapseStore {

    override suspend fun collapsedKeys(): Set<String> =
        context.dataStore.data.map { it[COLLAPSED_KEYS].orEmpty() }.first()

    override suspend fun setCollapsed(key: String, collapsed: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[COLLAPSED_KEYS].orEmpty()
            preferences[COLLAPSED_KEYS] = if (collapsed) current + key else current - key
        }
    }

    override suspend fun replaceKeysForFile(fileName: String, keys: Set<String>) {
        context.dataStore.edit { preferences ->
            val others = preferences[COLLAPSED_KEYS].orEmpty()
                .filterNot { CollapseKeys.belongsToFile(it, fileName) }
            preferences[COLLAPSED_KEYS] = others.toSet() + keys
        }
    }

    override suspend fun renameFile(oldFileName: String, newFileName: String) {
        context.dataStore.edit { preferences ->
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
