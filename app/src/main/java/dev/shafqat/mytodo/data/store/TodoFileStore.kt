package dev.shafqat.mytodo.data.store

/**
 * A folder of markdown files the app can read and write.
 *
 * This is the only seam that knows where files physically live, which keeps
 * [dev.shafqat.mytodo.data.MarkdownTodoRepository] free of Android storage APIs and testable on
 * the JVM. Implementations: [LocalDirectoryStore] (app-private default) and
 * [SafDirectoryStore] (a folder the user picked).
 *
 * Every method may throw; the repository translates failures into
 * [dev.shafqat.mytodo.data.StorageState].
 */
interface TodoFileStore {

    /** Short human-readable location shown in settings, e.g. "Download/todo". */
    val label: String

    /** True when the store can currently be read — false if a permission was revoked or it vanished. */
    suspend fun isAccessible(): Boolean

    /** Names of the markdown files in the folder, excluding anything else it contains. */
    suspend fun listFileNames(): List<String>

    suspend fun read(fileName: String): String

    suspend fun write(fileName: String, content: String)

    /**
     * Creates an empty file and returns the name it actually got.
     *
     * The name can differ from the one requested: SAF providers are free to adjust it (adding or
     * changing an extension, de-duplicating), so callers must use the returned name.
     */
    suspend fun create(fileName: String): String

    /** Renames a file and returns the name it actually got, for the same reason as [create]. */
    suspend fun rename(fileName: String, newFileName: String): String

    suspend fun delete(fileName: String)
}
