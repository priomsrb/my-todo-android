package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.StorageState
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.data.store.TodoFileStore
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownTodoRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var folder: File

    @Before
    fun setUp() {
        folder = temporaryFolder.newFolder("todo")
    }

    private fun TestScope.repository() = MarkdownTodoRepository(
        scope = this,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        autosaveDelayMillis = AUTOSAVE_DELAY,
    )

    private fun store(label: String = "test folder") = LocalDirectoryStore(folder, label = label)

    private fun write(name: String, content: String) = File(folder, name).writeText(content)

    private fun read(name: String) = File(folder, name).readText()

    @Test
    fun `an empty folder is seeded with a welcome list`() = runTest {
        val repository = repository()

        repository.useStore(store(), seedWhenEmpty = true)
        advanceUntilIdle()

        val lists = repository.lists.value
        assertEquals(1, lists.size)
        assertEquals("My first list", lists.single().name)
        assertTrue(File(folder, "my-first-list.md").exists())
    }

    @Test
    fun `a folder the user picked is never seeded`() = runTest {
        val repository = repository()

        repository.useStore(store(), seedWhenEmpty = false)
        advanceUntilIdle()

        assertTrue(repository.lists.value.isEmpty())
        assertEquals(emptyList<String>(), folder.list()?.toList())
    }

    @Test
    fun `existing markdown files load as lists named after their filenames`() = runTest {
        write("groceries.md", "- [ ] Milk\n\t- [X] Whole\n")
        write("house.md", "- [ ] Fix tap\n")
        write("notes.txt", "not a todo file")
        val repository = repository()

        repository.useStore(store())
        advanceUntilIdle()

        val lists = repository.lists.value
        assertEquals(listOf("Groceries", "House"), lists.map { it.name })
        assertEquals(listOf("groceries.md", "house.md"), lists.map { it.id })
        assertEquals(listOf(0, 1), lists.first().items.flattenVisible().map { it.depth })
    }

    @Test
    fun `edits are written back after the autosave delay, not before`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        repository.addItem("groceries.md", "Eggs")
        advanceTimeBy(AUTOSAVE_DELAY / 2)
        assertEquals("- [ ] Milk\n", read("groceries.md"))

        advanceUntilIdle()
        assertEquals("- [ ] Milk\n- [ ] Eggs\n", read("groceries.md"))
    }

    @Test
    fun `a burst of edits collapses into a single write`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        repeat(5) { index ->
            repository.addItem("groceries.md", "Item $index")
            advanceTimeBy(AUTOSAVE_DELAY / 5)
        }
        advanceUntilIdle()

        val saved = read("groceries.md")
        assertEquals(6, saved.trim().lines().size)
        assertTrue(saved.contains("- [ ] Item 4\n"))
    }

    @Test
    fun `ticking an item writes an uppercase X`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val itemId = repository.lists.value.single().items.single().id

        repository.setItemDone("groceries.md", itemId, true)
        advanceUntilIdle()

        assertEquals("- [X] Milk\n", read("groceries.md"))
    }

    @Test
    fun `collapsing an item never touches the file`() = runTest {
        write("groceries.md", "- [ ] Milk\n\t- [ ] Whole\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val parentId = repository.lists.value.single().items.single().id
        val before = File(folder, "groceries.md").lastModified()

        repository.setItemCollapsed("groceries.md", parentId, true)
        advanceUntilIdle()

        assertTrue(repository.lists.value.single().items.single().collapsed)
        assertEquals(before, File(folder, "groceries.md").lastModified())
        assertEquals("- [ ] Milk\n\t- [ ] Whole\n", read("groceries.md"))
    }

    @Test
    fun `creating a list creates its file, and deleting removes it`() = runTest {
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        val created = repository.createList("Shopping list")
        advanceUntilIdle()
        assertEquals("shopping-list.md", created.fileName)
        assertTrue(File(folder, "shopping-list.md").exists())

        repository.deleteList(created.id)
        advanceUntilIdle()
        assertFalse(File(folder, "shopping-list.md").exists())
        assertTrue(repository.lists.value.isEmpty())
    }

    @Test
    fun `creating a list whose name is taken picks a free filename`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        val created = repository.createList("Groceries")
        advanceUntilIdle()

        assertEquals("groceries-2.md", created.fileName)
        assertEquals(listOf("groceries-2.md", "groceries.md"), repository.lists.value.map { it.fileName })
    }

    @Test
    fun `renaming a list renames its file and keeps the items`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        repository.renameList("groceries.md", "Weekly shop")
        advanceUntilIdle()

        assertFalse(File(folder, "groceries.md").exists())
        assertTrue(File(folder, "weekly-shop.md").exists())
        val list = repository.lists.value.single()
        assertEquals("Weekly shop", list.name)
        assertEquals(listOf("Milk"), list.items.map { it.text })
    }

    @Test
    fun `refresh picks up a file edited outside the app`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        write("groceries.md", "- [ ] Milk\n- [X] Bread\n")
        write("new-list.md", "- [ ] Added elsewhere\n")
        repository.refresh()
        advanceUntilIdle()

        assertEquals(listOf("groceries.md", "new-list.md"), repository.lists.value.map { it.fileName })
        assertEquals(listOf("Milk", "Bread"), repository.lists.value.first().items.map { it.text })
    }

    @Test
    fun `a refresh that finds no change keeps the items it already had`() = runTest {
        write("groceries.md", "- [ ] Milk\n\t- [ ] Whole\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        // An edit, saved, and then a reload — which is what a widget redraw does about a second
        // after any change. Ids have to survive it: a row being edited is keyed on one, and it is
        // torn down, mid-typing, if the id underneath it changes.
        val milk = repository.lists.value.single().items.first().id
        repository.setItemText("groceries.md", milk, "Oat milk")
        advanceUntilIdle()
        val before = repository.lists.value.single().items.ids()

        repository.refresh()
        advanceUntilIdle()

        assertEquals(before, repository.lists.value.single().items.ids())
        assertEquals(listOf("Oat milk", "Whole"), repository.lists.value.single().items.texts())
    }

    @Test
    fun `a refresh that finds the file changed takes what is on disk`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val before = repository.lists.value.single().items.ids()

        write("groceries.md", "- [ ] Oat milk\n")
        repository.refresh()
        advanceUntilIdle()

        assertEquals(listOf("Oat milk"), repository.lists.value.single().items.texts())
        assertTrue(repository.lists.value.single().items.ids() != before)
    }

    @Test
    fun `refresh does not discard an edit that has not been saved yet`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        repository.addItem("groceries.md", "Eggs")
        repository.refresh()
        advanceUntilIdle()

        assertEquals(listOf("Milk", "Eggs"), repository.lists.value.single().items.map { it.text })
        assertEquals("- [ ] Milk\n- [ ] Eggs\n", read("groceries.md"))
    }

    @Test
    fun `a refresh that lands while a save is still being written keeps the edit`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        // A write held open, which is where one is on a device more often than it looks: a SAF
        // write goes through a content provider, and a widget reloads about a second after any
        // edit. A reload that arrived mid-write used to read the file from before it and take
        // that over what the user had on screen.
        val gate = CompletableDeferred<Unit>()
        val repository = repository()
        repository.useStore(HeldWriteStore(store(), gate))
        advanceUntilIdle()

        repository.addItem("groceries.md", "Eggs")
        advanceUntilIdle()
        assertEquals("- [ ] Milk\n", read("groceries.md"))

        // Concurrently, because a reload reads the files before it takes the lock the write holds
        // — reading what is there now and publishing it once the write lets go.
        val reload = launch { repository.refresh() }
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()
        reload.join()

        assertEquals(listOf("Milk", "Eggs"), repository.lists.value.single().items.texts())
        assertEquals("- [ ] Milk\n- [ ] Eggs\n", read("groceries.md"))
    }

    @Test
    fun `flushing writes pending edits immediately`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        repository.addItem("groceries.md", "Eggs")
        repository.flushPendingSaves()
        advanceUntilIdle()

        assertEquals("- [ ] Milk\n- [ ] Eggs\n", read("groceries.md"))
    }

    @Test
    fun `hand-written content around the items survives an edit`() = runTest {
        write("groceries.md", "# Shopping\n- [ ] Milk\nremember the coupon\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        repository.addItem("groceries.md", "Eggs")
        advanceUntilIdle()

        assertEquals("# Shopping\n- [ ] Milk\nremember the coupon\n- [ ] Eggs\n", read("groceries.md"))
    }

    @Test
    fun `storage state reports the location and list count`() = runTest {
        write("groceries.md", "- [ ] Milk\n")
        val repository = repository()

        repository.useStore(store())
        advanceUntilIdle()

        val state = repository.storageState.value
        assertTrue(state is StorageState.Ready)
        assertEquals(1, (state as StorageState.Ready).listCount)
    }

    @Test
    fun `an unreadable folder is reported rather than crashing`() = runTest {
        val repository = repository()
        val missing = File(folder, "nope/deeper")
        // A store whose directory cannot be created stands in for a revoked folder.
        File(folder, "nope").writeText("this is a file, not a directory")

        repository.useStore(LocalDirectoryStore(missing, label = "nope"))
        advanceUntilIdle()

        assertTrue(repository.storageState.value is StorageState.PermissionLost)
        assertTrue(repository.lists.value.isEmpty())
    }

    private companion object {
        const val AUTOSAVE_DELAY = 500L
    }
    /** Every id in the tree, so a test can say whether identity survived a reload. */
    private fun List<TodoItem>.ids(): List<String> =
        flatMap { listOf(it.id) + it.children.ids() }

    private fun List<TodoItem>.texts(): List<String> =
        flatMap { listOf(it.text) + it.children.texts() }

    /** A store whose writes wait for [gate], so a test can catch one in flight. */
    private class HeldWriteStore(
        private val delegate: TodoFileStore,
        private val gate: CompletableDeferred<Unit>,
    ) : TodoFileStore by delegate {
        override suspend fun write(fileName: String, content: String) {
            gate.await()
            delegate.write(fileName, content)
        }
    }

}
