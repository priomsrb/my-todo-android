package dev.shafqat.mytodo.data.collapse

/**
 * Remembers which items the user has collapsed.
 *
 * Collapse is a view preference rather than part of the user's data, so it lives beside the app's
 * other settings and is never written into a markdown file.
 */
interface CollapseStore {

    suspend fun collapsedKeys(): Set<String>

    suspend fun setCollapsed(key: String, collapsed: Boolean)

    /** Replaces every key belonging to [fileName] — used by collapse-all, expand-all and deletion. */
    suspend fun replaceKeysForFile(fileName: String, keys: Set<String>)

    /** Moves a file's keys after its list was renamed, so collapse survives the rename. */
    suspend fun renameFile(oldFileName: String, newFileName: String)
}

/** Non-persistent implementation, used in tests and as a safe default. */
class InMemoryCollapseStore(initial: Set<String> = emptySet()) : CollapseStore {

    private val keys = initial.toMutableSet()

    override suspend fun collapsedKeys(): Set<String> = keys.toSet()

    override suspend fun setCollapsed(key: String, collapsed: Boolean) {
        if (collapsed) keys += key else keys -= key
    }

    override suspend fun replaceKeysForFile(fileName: String, keys: Set<String>) {
        this.keys.removeAll { CollapseKeys.belongsToFile(it, fileName) }
        this.keys += keys
    }

    override suspend fun renameFile(oldFileName: String, newFileName: String) {
        val moved = keys.filter { CollapseKeys.belongsToFile(it, oldFileName) }
        keys.removeAll(moved.toSet())
        keys += moved.map { CollapseKeys.reparent(it, oldFileName, newFileName) }
    }
}
