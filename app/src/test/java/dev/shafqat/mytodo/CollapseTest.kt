package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.data.collapse.InMemoryCollapseStore
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.model.flattenVisible
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CollapseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var folder: File
    private lateinit var collapseStore: InMemoryCollapseStore

    private val nested = "- [ ] Top\n\t- [ ] Child\n\t\t- [ ] Grandchild\n- [ ] Other\n"

    @Before
    fun setUp() {
        folder = temporaryFolder.newFolder("todo")
        collapseStore = InMemoryCollapseStore()
    }

    private fun TestScope.repository() = MarkdownTodoRepository(
        scope = this,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        autosaveDelayMillis = 500L,
        collapseStore = collapseStore,
    )

    private fun store() = LocalDirectoryStore(folder, label = "test folder")

    private fun write(name: String, content: String) = File(folder, name).writeText(content)

    private fun read(name: String) = File(folder, name).readText()

    // --- keys -------------------------------------------------------------------------------

    @Test
    fun `a key is the file name followed by the path of item texts`() {
        val items = listOf(
            TodoItem(text = "Top", children = listOf(TodoItem(text = "Child"))),
        )

        val childId = items.single().children.single().id
        val key = CollapseKeys.keyOf(items, "list.md", childId)

        assertEquals(listOf("list.md", "Top", "Child"), key?.split("\u0000"))
    }

    @Test
    fun `siblings with the same text get distinct keys`() {
        val items = listOf(
            TodoItem(text = "Task", children = listOf(TodoItem(text = "x"))),
            TodoItem(text = "Task", children = listOf(TodoItem(text = "y"))),
        )

        val first = CollapseKeys.keyOf(items, "list.md", items[0].id)
        val second = CollapseKeys.keyOf(items, "list.md", items[1].id)

        assertNotNull(first)
        assertTrue(second!!.endsWith("Task#2"))
        assertTrue(first != second)
    }

    @Test
    fun `only items with children are collapsible`() {
        val items = listOf(
            TodoItem(text = "Parent", children = listOf(TodoItem(text = "Leaf"))),
            TodoItem(text = "Lonely"),
        )

        val keys = CollapseKeys.collapsibleKeys(items, "list.md")

        assertEquals(1, keys.size)
        assertTrue(keys.single().endsWith("Parent"))
    }

    @Test
    fun `applying keys never collapses a leaf`() {
        val items = listOf(TodoItem(text = "Lonely"))
        val everyKey = setOf(CollapseKeys.key("list.md", listOf("Lonely")))

        val applied = CollapseKeys.applyTo(items, "list.md", everyKey)

        assertFalse(applied.single().collapsed)
    }

    // --- repository -------------------------------------------------------------------------

    @Test
    fun `collapsing hides descendants without touching the file`() = runTest {
        write("list.md", nested)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val topId = repository.lists.value.single().items.first().id

        repository.setItemCollapsed("list.md", topId, true)
        advanceUntilIdle()

        val rows = repository.lists.value.single().items.flattenVisible()
        assertEquals(listOf("Top", "Other"), rows.map { it.item.text })
        assertEquals(nested, read("list.md"))
    }

    @Test
    fun `collapse survives a reload`() = runTest {
        write("list.md", nested)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val topId = repository.lists.value.single().items.first().id
        repository.setItemCollapsed("list.md", topId, true)
        advanceUntilIdle()

        // A fresh repository stands in for restarting the app: ids are regenerated, keys are not.
        val reopened = repository()
        reopened.useStore(store())
        advanceUntilIdle()

        val items = reopened.lists.value.single().items
        assertTrue(items.first().collapsed)
        assertEquals(listOf("Top", "Other"), items.flattenVisible().map { it.item.text })
    }

    @Test
    fun `expanding forgets the collapse`() = runTest {
        write("list.md", nested)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val topId = repository.lists.value.single().items.first().id

        repository.setItemCollapsed("list.md", topId, true)
        repository.setItemCollapsed("list.md", topId, false)
        advanceUntilIdle()

        assertTrue(collapseStore.collapsedKeys().isEmpty())
        assertEquals(4, repository.lists.value.single().items.flattenVisible().size)
    }

    @Test
    fun `collapse all collapses every parent, expand all clears them`() = runTest {
        write("list.md", nested)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        repository.setAllCollapsed("list.md", true)
        advanceUntilIdle()
        assertEquals(listOf("Top", "Other"), repository.lists.value.single().items.flattenVisible().map { it.item.text })
        assertEquals(2, collapseStore.collapsedKeys().size)

        repository.setAllCollapsed("list.md", false)
        advanceUntilIdle()
        assertEquals(4, repository.lists.value.single().items.flattenVisible().size)
        assertTrue(collapseStore.collapsedKeys().isEmpty())
    }

    @Test
    fun `collapse follows a renamed list`() = runTest {
        write("list.md", nested)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val topId = repository.lists.value.single().items.first().id
        repository.setItemCollapsed("list.md", topId, true)
        advanceUntilIdle()

        repository.renameList("list.md", "Renamed")
        advanceUntilIdle()

        val list = repository.lists.value.single()
        assertEquals("renamed.md", list.fileName)
        assertTrue(list.items.first().collapsed)
        assertTrue(collapseStore.collapsedKeys().all { it.startsWith("renamed.md") })
    }

    @Test
    fun `deleting a list forgets its collapse state`() = runTest {
        write("list.md", nested)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val topId = repository.lists.value.single().items.first().id
        repository.setItemCollapsed("list.md", topId, true)
        advanceUntilIdle()

        repository.deleteList("list.md")
        advanceUntilIdle()

        assertTrue(collapseStore.collapsedKeys().isEmpty())
    }

    @Test
    fun `editing an item keeps its collapsed children hidden`() = runTest {
        write("list.md", nested)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        val topId = repository.lists.value.single().items.first().id
        repository.setItemCollapsed("list.md", topId, true)
        advanceUntilIdle()

        repository.addItem("list.md", "Appended")
        advanceUntilIdle()

        val items = repository.lists.value.single().items
        assertTrue(items.first { it.id == topId }.collapsed)
        assertNotNull(items.findItem(topId))
        assertTrue(read("list.md").contains("- [ ] Appended"))
    }
}
