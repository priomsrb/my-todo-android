package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.StorageState
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.model.flattenVisible
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
