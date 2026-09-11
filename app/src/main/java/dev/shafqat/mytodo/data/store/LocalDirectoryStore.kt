package dev.shafqat.mytodo.data.store

import dev.shafqat.mytodo.model.isTodoFile
import java.io.File
import java.io.IOException

/**
 * The default store: a plain directory the app owns, used until the user picks a folder of their
 * own in settings. Also handy in unit tests, where a temp directory stands in for real storage.
 */
class LocalDirectoryStore(
    private val directory: File,
    override val label: String = "App storage (internal)",
) : TodoFileStore {

    override suspend fun isAccessible(): Boolean = directory.exists() || directory.mkdirs()

    override suspend fun listFileNames(): List<String> {
        ensureDirectory()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && isTodoFile(it.name) }
            .map { it.name }
            .sorted()
    }

    override suspend fun read(fileName: String): String = file(fileName).readText()

    override suspend fun write(fileName: String, content: String) {
        ensureDirectory()
        file(fileName).writeText(content)
    }

    override suspend fun create(fileName: String): String {
        ensureDirectory()
        val target = file(fileName)
        if (!target.exists() && !target.createNewFile()) {
            throw IOException("Could not create ${target.path}")
        }
        return target.name
    }

    override suspend fun rename(fileName: String, newFileName: String): String {
        val target = file(newFileName)
        if (!file(fileName).renameTo(target)) {
            throw IOException("Could not rename $fileName to $newFileName")
        }
        return target.name
    }

    override suspend fun delete(fileName: String) {
        val target = file(fileName)
        if (target.exists() && !target.delete()) {
            throw IOException("Could not delete ${target.path}")
        }
    }

    private fun file(fileName: String) = File(directory, fileName)

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create ${directory.path}")
        }
    }
}
