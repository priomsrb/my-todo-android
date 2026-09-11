package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.collapse.InMemoryCollapseStore
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.visibleIndexOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The repository side of dragging: a move must reach the file and keep collapse state correct. */
@OptIn(ExperimentalCoroutinesApi::class)
class MoveItemTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var folder: File
    private lateinit var collapseStore: InMemoryCollapseStore

    private val markdown = "- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n- [ ] B\n- [X] C\n"

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

    private fun read() = File(folder, "list.md").readText()

    private fun itemId(repository: MarkdownTodoRepository, text: String): String =
        repository.lists.value.first { it.fileName == "list.md" }.items.flattenVisible()
            .first { it.item.text == text }.item.id

    private suspend fun TestScope.loadedRepository(): MarkdownTodoRepository {
        File(folder, "list.md").writeText(markdown)
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        return repository
    }

    @Test
    fun `a move is written back to the file`() = runTest {
        val repository = loadedRepository()

        // Drag C to the top.
        repository.moveItem("list.md", itemId(repository, "C"), targetIndex = 0, targetDepth = 0)
        advanceUntilIdle()

        assertEquals("- [X] C\n- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n- [ ] B\n", read())
    }

    @Test
    fun `indenting an item makes it a child in the file`() = runTest {
        val repository = loadedRepository()

        // B sits right after A's subtree; one level in makes it A's last child.
        repository.moveItem("list.md", itemId(repository, "B"), targetIndex = 3, targetDepth = 1)
        advanceUntilIdle()

        assertEquals("- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n\t- [ ] B\n- [X] C\n", read())
    }

    @Test
    fun `outdenting an item promotes it in the file`() = runTest {
        val repository = loadedRepository()

        repository.moveItem("list.md", itemId(repository, "A2"), targetIndex = 2, targetDepth = 0)
        advanceUntilIdle()

        assertEquals("- [ ] A\n\t- [ ] A1\n- [ ] A2\n- [ ] B\n- [X] C\n", read())
    }

    @Test
    fun `moving a parent carries its children into the file`() = runTest {
        val repository = loadedRepository()

        repository.moveItem("list.md", itemId(repository, "A"), targetIndex = 2, targetDepth = 0)
        advanceUntilIdle()

        assertEquals("- [ ] B\n- [X] C\n- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n", read())
    }

    @Test
    fun `a move within the tree does not change how many items there are`() = runTest {
        val repository = loadedRepository()
        val before = repository.lists.value.single().items.flattenVisible().map { it.item.text }.sorted()

        repository.moveItem("list.md", itemId(repository, "A1"), targetIndex = 4, targetDepth = 1)
        advanceUntilIdle()

        val after = repository.lists.value.single().items.flattenVisible().map { it.item.text }.sorted()
        assertEquals(before, after)
    }

    @Test
    fun `collapse state follows an item that was moved`() = runTest {
        val repository = loadedRepository()
        val aId = itemId(repository, "A")
        repository.setItemCollapsed("list.md", aId, true)
        advanceUntilIdle()

        // Move the collapsed parent to the end; its children travel with it, still hidden.
        repository.moveItem("list.md", aId, targetIndex = 2, targetDepth = 0)
        advanceUntilIdle()

        assertEquals("- [ ] B\n- [X] C\n- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n", read())

        // Reopening re-derives collapse from the store, so a stale key would show up here.
        val reopened = repository()
        reopened.useStore(store())
        advanceUntilIdle()

        val rows = reopened.lists.value.single().items.flattenVisible()
        assertEquals(listOf("B", "C", "A"), rows.map { it.item.text })
        assertTrue(rows.last().item.collapsed)
    }

    @Test
    fun `dropping an item back where it started changes nothing`() = runTest {
        val repository = loadedRepository()
        val bId = itemId(repository, "B")
        val index = repository.lists.value.single().items.visibleIndexOf(bId)

        repository.moveItem("list.md", bId, index, targetDepth = 0)
        advanceUntilIdle()

        assertEquals(markdown, read())
    }

    @Test
    fun `a move into a deeply nested position keeps the nesting`() = runTest {
        File(folder, "list.md").writeText("- [ ] A\n\t- [ ] B\n\t\t- [ ] C\n- [ ] D\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        val dId = repository.lists.value.single().items.flattenVisible()
            .first { it.item.text == "D" }.item.id
        repository.moveItem("list.md", dId, targetIndex = 3, targetDepth = 3)
        advanceUntilIdle()

        assertEquals("- [ ] A\n\t- [ ] B\n\t\t- [ ] C\n\t\t\t- [ ] D\n", read())
    }

    @Test
    fun `moving an item in one list leaves other lists alone`() = runTest {
        File(folder, "other.md").writeText("- [ ] Untouched\n")
        val repository = loadedRepository()

        repository.moveItem("list.md", itemId(repository, "C"), targetIndex = 0, targetDepth = 0)
        advanceUntilIdle()

        assertEquals("- [ ] Untouched\n", File(folder, "other.md").readText())
    }

    @Test
    fun `hand-written lines survive a move`() = runTest {
        File(folder, "list.md").writeText("# Notes\n- [ ] A\n- [ ] B\n")
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()

        val bId = repository.lists.value.single().items.flattenVisible()
            .first { it.item.text == "B" }.item.id
        repository.moveItem("list.md", bId, targetIndex = 0, targetDepth = 0)
        advanceUntilIdle()

        assertTrue(read().startsWith("# Notes\n"))
        assertTrue(read().contains("- [ ] B\n- [ ] A"))
    }

    @Test
    fun `moving an item that is not in the list is ignored`() = runTest {
        val repository = loadedRepository()

        repository.moveItem("list.md", TodoItem(text = "ghost").id, targetIndex = 0, targetDepth = 0)
        advanceUntilIdle()

        assertEquals(markdown, read())
    }
}
