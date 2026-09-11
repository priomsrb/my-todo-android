package dev.shafqat.mytodo.data

import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.data.collapse.CollapseStore
import dev.shafqat.mytodo.data.collapse.InMemoryCollapseStore
import dev.shafqat.mytodo.data.markdown.MarkdownDocument
import dev.shafqat.mytodo.data.markdown.MarkdownParser
import dev.shafqat.mytodo.data.markdown.MarkdownSerializer
import dev.shafqat.mytodo.data.store.TodoFileStore
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.addItem
import dev.shafqat.mytodo.model.fileNameFor
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.removeItem
import dev.shafqat.mytodo.model.uniqueFileName
import dev.shafqat.mytodo.model.updateItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the in-memory tree and the markdown files in step.
 *
 * Edits apply to memory immediately and are written back after a short pause ([autosaveDelayMillis]),
 * so a burst of typing or ticking becomes one write. Structural changes to the *set* of lists
 * (create, rename, delete) touch the filesystem right away, since they cannot be represented in
 * memory alone.
 *
 * Deliberately free of Android APIs: everything platform-specific lives behind [TodoFileStore].
 */
class MarkdownTodoRepository(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val autosaveDelayMillis: Long = DEFAULT_AUTOSAVE_DELAY_MILLIS,
    private val collapseStore: CollapseStore = InMemoryCollapseStore(),
) : TodoRepository {

    private val _lists = MutableStateFlow<List<TodoList>>(emptyList())
    override val lists: StateFlow<List<TodoList>> = _lists.asStateFlow()

    private val _storageState = MutableStateFlow<StorageState>(StorageState.Loading)
    override val storageState: StateFlow<StorageState> = _storageState.asStateFlow()

    private var store: TodoFileStore? = null

    // Both maps are touched from the main dispatcher (edits) and the IO dispatcher (saves and
    // reloads), so they are concurrent rather than plain maps.

    /** Unparseable extras per file, so hand-written content survives being rewritten. */
    private val documents = ConcurrentHashMap<String, MarkdownDocument>()

    /** In-flight autosaves, keyed by filename, so a later edit replaces an earlier pending write. */
    private val pendingSaves = ConcurrentHashMap<String, Job>()

    /** Serializes file mutations against each other and against [refresh]. */
    private val fileMutex = Mutex()

    /**
     * Points the repository at [store] and loads it.
     *
     * [seedWhenEmpty] creates a welcome list in a folder that has no lists yet. It is on for the
     * app-private default and off for a folder the user picked — writing an uninvited file into
     * someone's own directory would be rude.
     */
    suspend fun useStore(store: TodoFileStore, seedWhenEmpty: Boolean = false) {
        flushPendingSaves()
        fileMutex.withLock {
            this.store = store
            documents.clear()
            _lists.value = emptyList()
        }
        refresh(seedWhenEmpty)
    }

    /**
     * Reports that the configured folder cannot be used, without discarding the store.
     * The UI keeps whatever it had and settings shows how to fix it.
     */
    fun markUnavailable(label: String) {
        _storageState.value = StorageState.PermissionLost(label)
    }

    /**
     * Re-reads every file from disk, picking up edits made outside the app.
     *
     * Files with an unsaved edit pending are left alone so a background reload cannot undo
     * something the user just typed.
     */
    override suspend fun refresh() = refresh(seedWhenEmpty = false)

    private suspend fun refresh(seedWhenEmpty: Boolean) {
        val store = store ?: return
        withStore(store) {
            if (!store.isAccessible()) {
                markUnavailable(store.label)
                return@withStore
            }

            val fileNames = store.listFileNames()
            if (fileNames.isEmpty() && seedWhenEmpty) {
                createWelcomeList(store)
                return@withStore
            }

            val loaded = fileNames.map { fileName ->
                if (pendingSaves.containsKey(fileName)) {
                    // An unsaved edit is newer than what is on disk; keep it.
                    _lists.value.firstOrNull { it.fileName == fileName }
                        ?: readList(store, fileName)
                } else {
                    readList(store, fileName)
                }
            }

            fileMutex.withLock {
                documents.keys.retainAll(fileNames.toSet())
                // Re-check under the lock: an edit may have landed while the files were being read,
                // and what the user just typed always wins over what was on disk.
                _lists.value = loaded.map { list ->
                    if (pendingSaves.containsKey(list.fileName)) {
                        _lists.value.firstOrNull { it.fileName == list.fileName } ?: list
                    } else {
                        list
                    }
                }
            }
            _storageState.value = StorageState.Ready(store.label, loaded.size)
        }
    }

    /** Writes every pending edit immediately. Called when the app goes to the background. */
    suspend fun flushPendingSaves() {
        val pending = pendingSaves.keys.toList()
        pending.forEach { fileName ->
            pendingSaves.remove(fileName)?.cancel()
            save(fileName)
        }
    }

    override suspend fun createList(name: String): TodoList {
        val store = store ?: error("No store attached")
        val fileName = uniqueFileName(fileNameFor(name), _lists.value.map { it.fileName })

        var created = TodoList(fileName = fileName)
        withStore(store) {
            val actualName = fileMutex.withLock { store.create(fileName) }
            created = TodoList(fileName = actualName)
            documents[actualName] = MarkdownDocument()
            _lists.update { (it + created).sortedBy { list -> list.fileName } }
            _storageState.value = StorageState.Ready(store.label, _lists.value.size)
        }
        return created
    }

    override suspend fun renameList(listId: String, name: String) {
        val store = store ?: return
        val current = _lists.value.firstOrNull { it.id == listId } ?: return
        val newFileName = uniqueFileName(
            fileNameFor(name),
            _lists.value.filter { it.id != listId }.map { it.fileName },
        )
        if (newFileName == current.fileName) return

        // A rename must not race the autosave of the file being renamed.
        pendingSaves.remove(current.fileName)?.cancel()
        save(current.fileName)

        withStore(store) {
            val actualName = fileMutex.withLock { store.rename(current.fileName, newFileName) }
            documents[actualName] = documents.remove(current.fileName) ?: MarkdownDocument()
            collapseStore.renameFile(current.fileName, actualName)
            _lists.update { lists ->
                lists.map { if (it.id == listId) it.copy(fileName = actualName) else it }
                    .sortedBy { it.fileName }
            }
        }
    }

    override suspend fun deleteList(listId: String) {
        val store = store ?: return
        val current = _lists.value.firstOrNull { it.id == listId } ?: return

        pendingSaves.remove(current.fileName)?.cancel()
        withStore(store) {
            fileMutex.withLock { store.delete(current.fileName) }
            documents.remove(current.fileName)
            collapseStore.replaceKeysForFile(current.fileName, emptySet())
            _lists.update { lists -> lists.filterNot { it.id == listId } }
            _storageState.value = StorageState.Ready(store.label, _lists.value.size)
        }
    }

    override suspend fun addItem(listId: String, text: String, parentId: String?): TodoItem {
        val item = TodoItem(text = text)
        mutate(listId) { items -> items.addItem(item, parentId) }
        return item
    }

    override suspend fun setItemDone(listId: String, itemId: String, done: Boolean) {
        mutate(listId) { items -> items.updateItem(itemId) { it.copy(done = done) } }
    }

    override suspend fun setItemText(listId: String, itemId: String, text: String) {
        mutate(listId) { items -> items.updateItem(itemId) { it.copy(text = text) } }
    }

    override suspend fun deleteItem(listId: String, itemId: String) {
        mutate(listId) { items -> items.removeItem(itemId) }
    }

    override suspend fun moveItem(listId: String, itemId: String, targetIndex: Int, targetDepth: Int) {
        mutate(listId) { items -> items.moveSubtree(itemId, targetIndex, targetDepth) }

        // Collapse keys are paths, so a move invalidates them. Re-derive them from where the
        // items ended up, or collapsed subtrees would spring open on the next reload.
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        collapseStore.replaceKeysForFile(
            list.fileName,
            CollapseKeys.collapsedKeys(list.items, list.fileName),
        )
    }

    override suspend fun setItemCollapsed(listId: String, itemId: String, collapsed: Boolean) {
        // Collapse never reaches the markdown file; it is remembered in the collapse store instead,
        // so this deliberately skips scheduling a save.
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        _lists.update { lists ->
            lists.map { candidate ->
                if (candidate.id != listId) {
                    candidate
                } else {
                    candidate.copy(items = candidate.items.updateItem(itemId) { it.copy(collapsed = collapsed) })
                }
            }
        }

        CollapseKeys.keyOf(list.items, list.fileName, itemId)?.let { key ->
            collapseStore.setCollapsed(key, collapsed)
        }
    }

    override suspend fun setAllCollapsed(listId: String, collapsed: Boolean) {
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        val keys = if (collapsed) CollapseKeys.collapsibleKeys(list.items, list.fileName) else emptySet()

        _lists.update { lists ->
            lists.map { candidate ->
                if (candidate.id != listId) {
                    candidate
                } else {
                    candidate.copy(items = CollapseKeys.applyTo(candidate.items, candidate.fileName, keys))
                }
            }
        }
        collapseStore.replaceKeysForFile(list.fileName, keys)
    }

    private fun mutate(listId: String, transform: (List<TodoItem>) -> List<TodoItem>) {
        var fileName: String? = null
        _lists.update { lists ->
            lists.map { list ->
                if (list.id != listId) {
                    list
                } else {
                    fileName = list.fileName
                    list.copy(items = transform(list.items))
                }
            }
        }
        fileName?.let(::scheduleSave)
    }

    /** Replaces any pending write for this file, so rapid edits collapse into a single save. */
    private fun scheduleSave(fileName: String) {
        pendingSaves.remove(fileName)?.cancel()
        pendingSaves[fileName] = scope.launch {
            delay(autosaveDelayMillis)
            pendingSaves.remove(fileName)
            save(fileName)
        }
    }

    private suspend fun save(fileName: String) {
        val store = store ?: return
        val list = _lists.value.firstOrNull { it.fileName == fileName } ?: return
        val document = (documents[fileName] ?: MarkdownDocument()).copy(items = list.items)

        withStore(store) {
            fileMutex.withLock { store.write(fileName, MarkdownSerializer.serialize(document)) }
            documents[fileName] = document
        }
    }

    private suspend fun readList(store: TodoFileStore, fileName: String): TodoList {
        val document = MarkdownParser.parse(withContext(ioDispatcher) { store.read(fileName) })
        documents[fileName] = document
        // Collapse state is remembered outside the file, so re-apply it to what was just parsed.
        val items = CollapseKeys.applyTo(document.items, fileName, collapseStore.collapsedKeys())
        return TodoList(fileName = fileName, items = items)
    }

    private suspend fun createWelcomeList(store: TodoFileStore) {
        val fileName = store.create(fileNameFor(WELCOME_LIST_NAME))
        store.write(fileName, MarkdownSerializer.serialize(welcomeItems()))
        val document = MarkdownParser.parse(store.read(fileName))
        documents[fileName] = document
        _lists.value = listOf(TodoList(fileName = fileName, items = document.items))
        _storageState.value = StorageState.Ready(store.label, 1)
    }

    /** Runs a storage operation, turning its failures into a [StorageState] instead of a crash. */
    private suspend fun withStore(store: TodoFileStore, block: suspend () -> Unit) {
        try {
            withContext(ioDispatcher) { block() }
        } catch (e: SecurityException) {
            markUnavailable(store.label)
        } catch (e: Exception) {
            _storageState.value = StorageState.Error(store.label, e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        const val DEFAULT_AUTOSAVE_DELAY_MILLIS = 500L
        const val WELCOME_LIST_NAME = "My first list"

        fun welcomeItems(): List<TodoItem> = listOf(
            TodoItem(
                text = "Tap the checkbox to tick something off",
                children = listOf(
                    TodoItem(text = "Items can nest as deep as you like"),
                    TodoItem(text = "Done items gray out", done = true),
                ),
            ),
            TodoItem(text = "Add items with the button below"),
            TodoItem(text = "Choose where your files live in Settings"),
        )
    }
}
