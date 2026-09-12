package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.collapse.InMemoryCollapseStore
import dev.shafqat.mytodo.data.prefs.InMemoryListPrefsStore
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.visibleRowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The Phase 4 editing operations as they reach the file: inline entry, Tab/Shift-Tab, undoing a
 * delete, sinking finished items, and the per-list preferences that deliberately never get there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListEditingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var folder: File
    private lateinit var collapseStore: InMemoryCollapseStore
    private lateinit var prefsStore: InMemoryListPrefsStore

    private val markdown = "- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n- [ ] B\n- [X] C\n"

    @Before
    fun setUp() {
        folder = temporaryFolder.newFolder("todo")
        collapseStore = InMemoryCollapseStore()
        prefsStore = InMemoryListPrefsStore()
        File(folder, "list.md").writeText(markdown)
    }

    private fun TestScope.repository() = MarkdownTodoRepository(
        scope = this,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        autosaveDelayMillis = AUTOSAVE_DELAY,
        collapseStore = collapseStore,
        listPrefsStore = prefsStore,
    )

    private fun store() = LocalDirectoryStore(folder, label = "test folder")

    private fun read() = File(folder, "list.md").readText()

    private suspend fun TestScope.loaded(): MarkdownTodoRepository {
        val repository = repository()
        repository.useStore(store())
        advanceUntilIdle()
        return repository
    }

    private fun MarkdownTodoRepository.outline(): String =
        lists.value.single().items.flattenVisible()
            .joinToString("\n") { "  ".repeat(it.depth) + it.item.text }

    private fun MarkdownTodoRepository.idOf(text: String): String =
        lists.value.single().items.flattenVisible().first { it.item.text == text }.item.id

    // --- inline entry ----------------------------------------------------------------------

    @Test
    fun `a new item lands on the row after the one being edited`() = runTest {
        val repository = loaded()

        repository.addItemAfter("list.md", repository.idOf("B"), "B2")
        advanceUntilIdle()

        assertEquals("A\n  A1\n  A2\nB\nB2\nC", repository.outline())
        assertEquals("- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n- [ ] B\n- [ ] B2\n- [X] C\n", read())
    }

    @Test
    fun `a new item after a parent becomes its first child, which is where the next row is`() =
        runTest {
            val repository = loaded()

            repository.addItemAfter("list.md", repository.idOf("A"), "A0")
            advanceUntilIdle()

            assertEquals("A\n  A0\n  A1\n  A2\nB\nC", repository.outline())
        }

    @Test
    fun `a new item after a nested one stays at that depth`() = runTest {
        val repository = loaded()

        repository.addItemAfter("list.md", repository.idOf("A1"), "A1b")
        advanceUntilIdle()

        assertEquals("A\n  A1\n  A1b\n  A2\nB\nC", repository.outline())
    }

    // --- indent and outdent -----------------------------------------------------------------

    @Test
    fun `indenting reaches the file`() = runTest {
        val repository = loaded()

        repository.indentItem("list.md", repository.idOf("B"))
        advanceUntilIdle()

        assertEquals("A\n  A1\n  A2\n  B\nC", repository.outline())
        assertEquals("- [ ] A\n\t- [ ] A1\n\t- [ ] A2\n\t- [ ] B\n- [X] C\n", read())
    }

    @Test
    fun `outdenting reaches the file`() = runTest {
        val repository = loaded()

        repository.outdentItem("list.md", repository.idOf("A1"))
        advanceUntilIdle()

        assertEquals("A\n  A2\nA1\nB\nC", repository.outline())
        assertEquals("- [ ] A\n\t- [ ] A2\n- [ ] A1\n- [ ] B\n- [X] C\n", read())
    }

    @Test
    fun `indenting keeps a collapsed subtree collapsed after a reload`() = runTest {
        val repository = loaded()
        val parent = repository.idOf("A")
        repository.setItemCollapsed("list.md", parent, true)

        // "B" cannot go under a collapsed "A", so indent "C" under "B" instead and reload.
        repository.indentItem("list.md", repository.idOf("C"))
        advanceUntilIdle()
        repository.refresh()
        advanceUntilIdle()

        val a = repository.lists.value.single().items.first()
        assertTrue("A stayed collapsed", a.collapsed)
        assertEquals("A\nB\n  C", repository.outline())
    }

    // --- undoing a delete -------------------------------------------------------------------

    @Test
    fun `a deleted subtree can be put back exactly where it was`() = runTest {
        val repository = loaded()
        val items = repository.lists.value.single().items
        val subtree = items.first()
        val row = requireNotNull(items.visibleRowOf(subtree.id))

        repository.deleteItem("list.md", subtree.id)
        advanceUntilIdle()
        assertEquals("B\nC", repository.outline())

        repository.restoreItem("list.md", subtree, row.index, row.depth)
        advanceUntilIdle()

        assertEquals("A\n  A1\n  A2\nB\nC", repository.outline())
        assertEquals(markdown, read())
    }

    @Test
    fun `undoing a delete of a nested item restores its depth`() = runTest {
        val repository = loaded()
        val items = repository.lists.value.single().items
        val nested = requireNotNull(items.first().children.firstOrNull { it.text == "A2" })
        val row = requireNotNull(items.visibleRowOf(nested.id))

        repository.deleteItem("list.md", nested.id)
        repository.restoreItem("list.md", nested, row.index, row.depth)
        advanceUntilIdle()

        assertEquals("A\n  A1\n  A2\nB\nC", repository.outline())
    }

    // --- completed items --------------------------------------------------------------------

    @Test
    fun `moving completed to the bottom rewrites the file`() = runTest {
        val repository = loaded()
        repository.setItemDone("list.md", repository.idOf("A1"), true)

        repository.moveCompletedToBottom("list.md")
        advanceUntilIdle()

        assertEquals("A\n  A2\n  A1\nB\nC", repository.outline())
        assertEquals("- [ ] A\n\t- [ ] A2\n\t- [X] A1\n- [ ] B\n- [X] C\n", read())
    }

    // --- per-list preferences ---------------------------------------------------------------

    @Test
    fun `a list colour is remembered without touching the file`() = runTest {
        val repository = loaded()

        repository.setListPrefs("list.md", ListPrefs(colorIndex = 3))
        advanceUntilIdle()

        assertEquals(3, repository.lists.value.single().prefs.colorIndex)
        assertEquals(markdown, read())
        assertEquals(ListPrefs(colorIndex = 3), prefsStore.all()["list.md"])
    }

    @Test
    fun `list preferences survive a reload`() = runTest {
        val repository = loaded()
        repository.setListPrefs("list.md", ListPrefs(colorIndex = 2, hideCompleted = true))
        advanceUntilIdle()

        repository.refresh()
        advanceUntilIdle()

        assertEquals(
            ListPrefs(colorIndex = 2, hideCompleted = true),
            repository.lists.value.single().prefs,
        )
    }

    @Test
    fun `list preferences follow a rename and are forgotten on delete`() = runTest {
        val repository = loaded()
        repository.setListPrefs("list.md", ListPrefs(colorIndex = 1))
        advanceUntilIdle()

        val newId = repository.renameList("list.md", "Shopping")
        advanceUntilIdle()

        assertEquals("shopping.md", newId)
        assertEquals(1, repository.lists.value.single().prefs.colorIndex)
        assertEquals(ListPrefs(colorIndex = 1), prefsStore.all()["shopping.md"])

        repository.deleteList(newId)
        advanceUntilIdle()

        assertTrue(prefsStore.all().isEmpty())
    }

    @Test
    fun `hiding completed is a view and leaves the items alone`() = runTest {
        val repository = loaded()

        repository.setListPrefs("list.md", ListPrefs(hideCompleted = true))
        advanceUntilIdle()

        val list = repository.lists.value.single()
        assertEquals(listOf("A", "B", "C"), list.items.map { it.text })
        assertEquals(listOf("A", "B"), list.visibleItems.map { it.text })
        assertEquals(markdown, read())
    }

    @Test
    fun `renaming a list that keeps its file name reports the id it already had`() = runTest {
        val repository = loaded()

        assertEquals("list.md", repository.renameList("list.md", "List"))
    }

    @Test
    fun `a list with no preferences stores nothing`() = runTest {
        val repository = loaded()

        repository.setListPrefs("list.md", ListPrefs(colorIndex = 4))
        repository.setListPrefs("list.md", ListPrefs.Default)
        advanceUntilIdle()

        assertNull(prefsStore.all()["list.md"])
    }

    private companion object {
        const val AUTOSAVE_DELAY = 500L
    }
}
