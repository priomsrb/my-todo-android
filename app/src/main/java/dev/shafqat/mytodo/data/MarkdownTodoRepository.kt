package dev.shafqat.mytodo.data

import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.data.collapse.CollapseStore
import dev.shafqat.mytodo.data.collapse.InMemoryCollapseStore
import dev.shafqat.mytodo.data.markdown.MarkdownDocument
import dev.shafqat.mytodo.data.markdown.MarkdownParser
import dev.shafqat.mytodo.data.markdown.MarkdownSerializer
import dev.shafqat.mytodo.data.prefs.InMemoryListPrefsStore
import dev.shafqat.mytodo.data.prefs.ListPrefsStore
import dev.shafqat.mytodo.data.store.TodoFileStore
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.addItem
import dev.shafqat.mytodo.model.archiveFileNameFor
import dev.shafqat.mytodo.model.isArchiveFile
import dev.shafqat.mytodo.model.splitArchivable
import dev.shafqat.mytodo.model.completedLast
import dev.shafqat.mytodo.model.fileNameFor
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.hasSameContentAs
import dev.shafqat.mytodo.model.indentItem
import dev.shafqat.mytodo.model.insertSubtree
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.outdentItem
import dev.shafqat.mytodo.model.placedAsIn
import dev.shafqat.mytodo.model.removeItem
import dev.shafqat.mytodo.model.uniqueFileName
import dev.shafqat.mytodo.model.updateItem
import dev.shafqat.mytodo.model.visibleRowOf
import dev.shafqat.mytodo.model.withDoneFrom
import dev.shafqat.mytodo.model.withSubtreeDone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    private val listPrefsStore: ListPrefsStore = InMemoryListPrefsStore(),
) : TodoRepository {

    private val _lists = MutableStateFlow<List<TodoList>>(emptyList())
    override val lists: StateFlow<List<TodoList>> = _lists.asStateFlow()

    private val _archives = MutableStateFlow<Map<String, List<TodoItem>>>(emptyMap())
    override val archives: StateFlow<Map<String, List<TodoItem>>> = _archives.asStateFlow()

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
            _archives.value = emptyMap()
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

            val allFileNames = store.listFileNames()
            val archiveNames = allFileNames.filter(::isArchiveFile).toSet()
            val fileNames = allFileNames.filterNot(::isArchiveFile)
            if (fileNames.isEmpty() && seedWhenEmpty) {
                createWelcomeList(store)
                return@withStore
            }

            // Read once for the whole reload rather than per file: both stores hand back
            // everything they know in one go.
            val collapsedKeys = collapseStore.collapsedKeys()
            val allPrefs = listPrefsStore.all()

            val loadedArchives = fileNames.mapNotNull { fileName ->
                val archiveName = archiveFileNameFor(fileName)
                if (archiveName !in archiveNames) return@mapNotNull null
                val items = MarkdownParser.parse(withContext(ioDispatcher) { store.read(archiveName) }).items
                val current = _archives.value[fileName]
                // Same reasoning as keepingIdentity: an archive that has not changed keeps its ids,
                // or the screen showing it would be rebuilt by every reload.
                fileName to if (current != null && current.hasSameContentAs(items)) current else items
            }.filter { it.second.isNotEmpty() }.toMap()

            val loaded = fileNames.map { fileName ->
                if (pendingSaves.containsKey(fileName)) {
                    // An unsaved edit is newer than what is on disk; keep it.
                    _lists.value.firstOrNull { it.fileName == fileName }
                        ?: readList(store, fileName, collapsedKeys, allPrefs)
                } else {
                    keepingIdentity(readList(store, fileName, collapsedKeys, allPrefs))
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
            _archives.value = loadedArchives
            _storageState.value = StorageState.Ready(store.label, loaded.size)
        }
    }

    /**
     * The list as it was in memory when the file turns out to say the same thing, and [loaded]
     * otherwise.
     *
     * Every read mints new ids, so a reload that found no change would still replace every item
     * with an identical one under a different id — and anything keyed on those ids, above all the
     * row the user is typing into, would be torn down and rebuilt. Reloads are not rare: a widget
     * redraws roughly a second after any edit, and it refreshes before it draws. Only the ids and
     * the collapse state are kept; what is in the file is re-read as always.
     */
    private fun keepingIdentity(loaded: TodoList): TodoList {
        val current = _lists.value.firstOrNull { it.fileName == loaded.fileName } ?: return loaded
        return if (current.items.hasSameContentAs(loaded.items)) {
            loaded.copy(items = current.items)
        } else {
            loaded
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

    override suspend fun renameList(listId: String, name: String): String {
        val store = store ?: return listId
        val current = _lists.value.firstOrNull { it.id == listId } ?: return listId
        val newFileName = uniqueFileName(
            fileNameFor(name),
            _lists.value.filter { it.id != listId }.map { it.fileName },
        )
        if (newFileName == current.fileName) return listId

        // A rename must not race the autosave of the file being renamed.
        pendingSaves.remove(current.fileName)?.cancel()
        save(current.fileName)

        var renamedTo = listId
        withStore(store) {
            val actualName = fileMutex.withLock { store.rename(current.fileName, newFileName) }
            documents[actualName] = documents.remove(current.fileName) ?: MarkdownDocument()
            if (_archives.value.containsKey(current.fileName)) {
                // Best effort for the file's name, exact for the key: the archive follows its list.
                store.rename(archiveFileNameFor(current.fileName), archiveFileNameFor(actualName))
            }
            _archives.update { archives ->
                archives[current.fileName]?.let { archives - current.fileName + (actualName to it) } ?: archives
            }
            collapseStore.renameFile(current.fileName, actualName)
            listPrefsStore.renameFile(current.fileName, actualName)
            _lists.update { lists ->
                lists.map { if (it.id == listId) it.copy(fileName = actualName) else it }
                    .sortedBy { it.fileName }
            }
            renamedTo = actualName
        }
        return renamedTo
    }

    override suspend fun deleteList(listId: String) {
        val store = store ?: return
        val current = _lists.value.firstOrNull { it.id == listId } ?: return

        pendingSaves.remove(current.fileName)?.cancel()
        withStore(store) {
            fileMutex.withLock {
                store.delete(current.fileName)
                if (_archives.value.containsKey(current.fileName)) {
                    store.delete(archiveFileNameFor(current.fileName))
                }
            }
            _archives.update { it - current.fileName }
            documents.remove(current.fileName)
            collapseStore.replaceKeysForFile(current.fileName, emptySet())
            listPrefsStore.clear(current.fileName)
            _lists.update { lists -> lists.filterNot { it.id == listId } }
            _storageState.value = StorageState.Ready(store.label, _lists.value.size)
        }
    }

    override suspend fun addItem(listId: String, text: String, parentId: String?): TodoItem {
        val item = TodoItem(text = text)
        mutate(listId) { items -> items.addItem(item, parentId) }
        return item
    }

    override suspend fun addItemAt(listId: String, text: String, index: Int): TodoItem {
        val item = TodoItem(text = text)
        mutate(listId) { items -> items.insertSubtree(item, index, targetDepth = 0) }
        // Unlike appending, inserting above existing rows renumbers same-named siblings — and
        // collapse keys are paths built from exactly that numbering.
        rekeyCollapse(listId)
        return item
    }

    override suspend fun addItemAfter(listId: String, afterItemId: String, text: String): TodoItem {
        val item = TodoItem(text = text)
        rearrange(listId, { item }) { shown ->
            val row = shown.visibleRowOf(afterItemId)
            if (row == null) shown + item else shown.insertSubtree(item, row.index + 1, row.depth)
        }
        return item
    }

    override suspend fun addItemBefore(listId: String, beforeItemId: String, text: String): TodoItem {
        val item = TodoItem(text = text)
        rearrange(listId, { item }) { shown ->
            val row = shown.visibleRowOf(beforeItemId)
            // Its own row index at its own depth: the new item takes the slot, and the item that
            // was there — with everything under it — moves down one.
            if (row == null) listOf(item) + shown else shown.insertSubtree(item, row.index, row.depth)
        }
        return item
    }

    override suspend fun addItems(listId: String, items: List<TodoItem>, atTop: Boolean) {
        if (items.isEmpty()) return
        // Top-level rows either side of what is there already, so this needs no row coordinates:
        // the trees arrive built, and the paste's own nesting is simply kept.
        mutate(listId) { existing -> if (atTop) items + existing else existing + items }
        rekeyCollapse(listId)
    }

    override suspend fun removeItems(listId: String, items: List<TodoItem>, atTop: Boolean) {
        if (items.isEmpty()) return

        val texts = items.map { it.text }
        mutate(listId) { existing ->
            if (existing.size < items.size) return@mutate existing
            if (atTop) {
                if (existing.take(items.size).map { it.text } == texts) {
                    existing.drop(items.size)
                } else {
                    existing
                }
            } else {
                if (existing.takeLast(items.size).map { it.text } == texts) {
                    existing.dropLast(items.size)
                } else {
                    existing
                }
            }
        }
        rekeyCollapse(listId)
    }

    override suspend fun setItemDone(listId: String, itemId: String, done: Boolean) {
        mutate(listId) { items -> items.updateItem(itemId) { it.copy(done = done) } }
    }

    override suspend fun setSubtreeDone(listId: String, itemId: String, done: Boolean) {
        mutate(listId) { items -> items.updateItem(itemId) { it.withSubtreeDone(done) } }
    }

    override suspend fun restoreSubtreeDone(listId: String, itemId: String, before: TodoItem) {
        mutate(listId) { items -> items.updateItem(itemId) { it.withDoneFrom(before) } }
    }

    override suspend fun setItemText(listId: String, itemId: String, text: String) {
        mutate(listId) { items -> items.updateItem(itemId) { it.copy(text = text) } }
    }

    override suspend fun deleteItem(listId: String, itemId: String) {
        mutate(listId) { items -> items.removeItem(itemId) }
    }

    override suspend fun moveItem(listId: String, itemId: String, targetIndex: Int, targetDepth: Int) {
        rearrange(listId, { it.findItem(itemId) }) { shown -> shown.moveSubtree(itemId, targetIndex, targetDepth) }
        rekeyCollapse(listId)
    }

    /**
     * Re-derives this list's collapse keys from where its items now sit.
     *
     * Collapse keys are paths, so anything that reshapes the tree invalidates them; without this a
     * collapsed subtree springs open on the next reload.
     */
    private suspend fun rekeyCollapse(listId: String) {
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        collapseStore.replaceKeysForFile(
            list.fileName,
            CollapseKeys.collapsedKeys(list.items, list.fileName),
        )
    }

    override suspend fun indentItem(listId: String, itemId: String) {
        rearrange(listId, { it.findItem(itemId) }) { shown -> shown.indentItem(itemId) }
        rekeyCollapse(listId)
    }

    override suspend fun outdentItem(listId: String, itemId: String) {
        rearrange(listId, { it.findItem(itemId) }) { shown -> shown.outdentItem(itemId) }
        rekeyCollapse(listId)
    }

    override suspend fun restoreItem(
        listId: String,
        item: TodoItem,
        targetIndex: Int,
        targetDepth: Int,
    ) {
        mutate(listId) { items -> items.insertSubtree(item, targetIndex, targetDepth) }
        rekeyCollapse(listId)
    }

    override suspend fun moveCompletedToBottom(listId: String) {
        mutate(listId) { items -> items.completedLast() }
        rekeyCollapse(listId)
    }

    override suspend fun archiveCompleted(listId: String): List<TodoItem> {
        val list = _lists.value.firstOrNull { it.id == listId } ?: return emptyList()
        val (remaining, archived) = list.items.splitArchivable()
        if (archived.isEmpty()) return emptyList()

        // The archive is written before the list loses the items: a failure part way leaves them in
        // both places, which is a duplicate to tidy, rather than in neither, which is a loss.
        writeArchive(list.fileName, _archives.value[list.fileName].orEmpty() + archived)
        mutate(listId) { remaining }
        rekeyCollapse(listId)
        return archived
    }

    override suspend fun undoArchive(listId: String, before: List<TodoItem>, archived: List<TodoItem>) {
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        val texts = archived.map { it.text }
        val inArchive = _archives.value[list.fileName].orEmpty()
        // Matched by position and text, never by id, as for removeItems: only the tail of the
        // archive is ours to take back, and only if it still reads as what was put there.
        if (inArchive.size >= archived.size && inArchive.takeLast(archived.size).map { it.text } == texts) {
            writeArchive(list.fileName, inArchive.dropLast(archived.size))
        } else {
            return
        }
        val untouched = list.items.hasSameContentAs(before.splitArchivable().first)
        mutate(listId) { items -> if (untouched) before else items + archived }
        rekeyCollapse(listId)
    }

    override suspend fun restoreArchived(listId: String, itemIds: Set<String>) {
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        val inArchive = _archives.value[list.fileName].orEmpty()
        val (restored, kept) = inArchive.partition { it.id in itemIds }
        if (restored.isEmpty()) return

        // Unticked: an item brought back is work to do again, not another finished one.
        writeArchive(list.fileName, kept)
        mutate(listId) { items -> items + restored.map { it.withSubtreeDone(false) } }
    }

    override suspend fun deleteArchived(listId: String, itemIds: Set<String>) {
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        val inArchive = _archives.value[list.fileName].orEmpty()
        writeArchive(list.fileName, inArchive.filterNot { it.id in itemIds })
    }

    /**
     * Replaces a list's archive with [items], right away rather than on the autosave debounce:
     * archiving is a deliberate act with its own undo, not a burst of typing. An archive with
     * nothing in it is deleted rather than left as an empty file.
     */
    private suspend fun writeArchive(fileName: String, items: List<TodoItem>) {
        val store = store ?: return
        val archiveName = archiveFileNameFor(fileName)
        withStore(store) {
            fileMutex.withLock {
                if (items.isEmpty()) {
                    if (fileName in _archives.value) store.delete(archiveName)
                } else {
                    store.write(archiveName, MarkdownSerializer.serialize(items))
                }
            }
            _archives.update { if (items.isEmpty()) it - fileName else it + (fileName to items) }
        }
    }

    override suspend fun setListPrefs(listId: String, prefs: ListPrefs) {
        // Colour and view options are local, like collapse: no save is scheduled because none of
        // this belongs in the markdown file.
        val list = _lists.value.firstOrNull { it.id == listId } ?: return
        _lists.update { lists ->
            lists.map { if (it.id == listId) it.copy(prefs = prefs) else it }
        }
        listPrefsStore.setPrefs(list.fileName, prefs)
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

    private fun mutate(listId: String, transform: (List<TodoItem>) -> List<TodoItem>) =
        mutateList(listId) { list -> transform(list.items) }

    /**
     * An edit that places one item, worked out among the rows on screen and carried over to the
     * whole tree with [placedAsIn].
     *
     * Every edit made through here — a drag, Tab, Shift-Tab, Enter — arrives in the coordinates of
     * what the list is showing, and a list hiding its finished items shows fewer rows than its tree
     * has. Working those edits out on the whole tree nested items under rows nobody could see, and
     * left them hidden there with them. [subtree] is the item being placed, read off the whole tree
     * so that it brings its hidden children along; [edit] gets the rows on screen.
     */
    private fun rearrange(
        listId: String,
        subtree: (List<TodoItem>) -> TodoItem?,
        edit: (shown: List<TodoItem>) -> List<TodoItem>,
    ) = mutateList(listId) { list ->
        val moving = subtree(list.items)
        if (moving == null) list.items else list.items.placedAsIn(edit(list.visibleItems), moving)
    }

    private fun mutateList(listId: String, transform: (TodoList) -> List<TodoItem>) {
        var fileName: String? = null
        _lists.update { lists ->
            lists.map { list ->
                if (list.id != listId) {
                    list
                } else {
                    fileName = list.fileName
                    list.copy(items = transform(list))
                }
            }
        }
        fileName?.let(::scheduleSave)
    }

    /**
     * Replaces any pending write for this file, so rapid edits collapse into a single save.
     *
     * The file stays marked as pending until the write has actually landed, not merely until it
     * has been started: a reload treats a file with no pending write as the authority, and one
     * that arrives mid-write — a widget redraws about a second after any edit — would read the
     * version from before it and take that over what the user has on screen.
     *
     * Started lazily so the job is in the map before its own body can clear it, and cleared only
     * if it is still the pending one, since a later edit may have replaced it by then.
     */
    private fun scheduleSave(fileName: String) {
        pendingSaves.remove(fileName)?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(autosaveDelayMillis)
            try {
                save(fileName)
            } finally {
                pendingSaves.remove(fileName, coroutineContext[Job])
            }
        }
        pendingSaves[fileName] = job
        job.start()
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

    private suspend fun readList(
        store: TodoFileStore,
        fileName: String,
        collapsedKeys: Set<String>,
        allPrefs: Map<String, ListPrefs>,
    ): TodoList {
        val document = MarkdownParser.parse(withContext(ioDispatcher) { store.read(fileName) })
        documents[fileName] = document
        // Collapse state and list preferences are remembered outside the file, so re-apply them to
        // what was just parsed.
        val items = CollapseKeys.applyTo(document.items, fileName, collapsedKeys)
        return TodoList(
            fileName = fileName,
            items = items,
            prefs = allPrefs[fileName] ?: ListPrefs.Default,
        )
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
