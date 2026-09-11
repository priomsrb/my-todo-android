package dev.shafqat.mytodo.data.store

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dev.shafqat.mytodo.model.isTodoFile
import java.io.FileNotFoundException
import java.io.IOException

/**
 * A folder the user picked through the Storage Access Framework.
 *
 * The app holds a persisted permission on the tree URI, so this works for Downloads, an SD card, a
 * synced folder or a cloud provider without any storage permissions. That permission can still be
 * revoked (the user clears app data, the SD card leaves, the provider forgets), which surfaces as
 * [isAccessible] returning false or a `SecurityException` from any other call.
 */
class SafDirectoryStore(
    private val context: Context,
    private val treeUri: Uri,
) : TodoFileStore {

    override val label: String = friendlyPath(treeUri)

    private val resolver get() = context.contentResolver

    override suspend fun isAccessible(): Boolean = try {
        val held = resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        held && root()?.canRead() == true
    } catch (e: SecurityException) {
        false
    }

    override suspend fun listFileNames(): List<String> =
        requireRoot().listFiles()
            .filter { it.isFile && isTodoFile(it.name.orEmpty()) }
            .mapNotNull { it.name }
            .sorted()

    override suspend fun read(fileName: String): String {
        val document = findFile(fileName) ?: throw FileNotFoundException(fileName)
        return resolver.openInputStream(document.uri)?.use { it.reader().readText() }
            ?: throw IOException("Could not open $fileName for reading")
    }

    override suspend fun write(fileName: String, content: String) {
        val document = findFile(fileName) ?: requireRoot().createFileOrThrow(fileName)
        // "wt" truncates: without it a shorter list would leave the old tail behind.
        resolver.openOutputStream(document.uri, "wt")?.use { it.writer().apply { write(content); flush() } }
            ?: throw IOException("Could not open $fileName for writing")
    }

    override suspend fun create(fileName: String): String {
        val existing = findFile(fileName)
        if (existing != null) return existing.name ?: fileName
        return requireRoot().createFileOrThrow(fileName).name ?: fileName
    }

    override suspend fun rename(fileName: String, newFileName: String): String {
        val document = findFile(fileName) ?: throw FileNotFoundException(fileName)
        val renamed = DocumentsContract.renameDocument(resolver, document.uri, newFileName)
            ?: throw IOException("Could not rename $fileName to $newFileName")
        return DocumentFile.fromSingleUri(context, renamed)?.name ?: newFileName
    }

    override suspend fun delete(fileName: String) {
        val document = findFile(fileName) ?: return
        if (!document.delete()) throw IOException("Could not delete $fileName")
    }

    private fun root(): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    private fun requireRoot(): DocumentFile =
        root() ?: throw IOException("Folder is no longer available")

    private fun findFile(fileName: String): DocumentFile? =
        requireRoot().listFiles().firstOrNull { it.name.equals(fileName, ignoreCase = true) }

    /**
     * Providers may adjust the name they are given (most notably by appending an extension for the
     * MIME type), so the caller always reads the real name back off the result.
     */
    private fun DocumentFile.createFileOrThrow(fileName: String): DocumentFile =
        createFile(MARKDOWN_MIME_TYPE, fileName)
            ?: throw IOException("Could not create $fileName")

    private companion object {
        const val MARKDOWN_MIME_TYPE = "text/markdown"

        /** Turns `content://…/tree/primary%3ADownload%2Ftodo` into `Download/todo`. */
        fun friendlyPath(treeUri: Uri): String {
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                ?: return treeUri.lastPathSegment ?: treeUri.toString()
            val path = documentId.substringAfter(':', documentId)
            return path.ifEmpty { "Storage root" }
        }
    }
}
