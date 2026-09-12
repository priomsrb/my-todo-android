package dev.shafqat.mytodo.data.prefs

import dev.shafqat.mytodo.model.ListPrefs

/**
 * Remembers each list's colour and view options.
 *
 * Keyed by file name, which is a list's only identity, so the store has to follow a rename the way
 * [dev.shafqat.mytodo.data.collapse.CollapseStore] does.
 */
interface ListPrefsStore {

    /** Every list's preferences, keyed by file name. Lists with none are simply absent. */
    suspend fun all(): Map<String, ListPrefs>

    suspend fun setPrefs(fileName: String, prefs: ListPrefs)

    /** Moves a list's preferences after it was renamed, so its colour survives the rename. */
    suspend fun renameFile(oldFileName: String, newFileName: String)

    /** Forgets a list's preferences — used when the list is deleted. */
    suspend fun clear(fileName: String)
}

/** Non-persistent implementation, used in tests and as a safe default. */
class InMemoryListPrefsStore(initial: Map<String, ListPrefs> = emptyMap()) : ListPrefsStore {

    private val prefs = initial.toMutableMap()

    override suspend fun all(): Map<String, ListPrefs> = prefs.toMap()

    override suspend fun setPrefs(fileName: String, prefs: ListPrefs) {
        if (prefs.isDefault) this.prefs.remove(fileName) else this.prefs[fileName] = prefs
    }

    override suspend fun renameFile(oldFileName: String, newFileName: String) {
        prefs.remove(oldFileName)?.let { prefs[newFileName] = it }
    }

    override suspend fun clear(fileName: String) {
        prefs.remove(fileName)
    }
}
