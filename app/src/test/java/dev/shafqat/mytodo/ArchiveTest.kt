package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.archiveFileNameFor
import dev.shafqat.mytodo.model.splitArchivable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Archiving finished items into a sibling file, and getting them back. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder by lazy { temporaryFolder.newFolder("todo") }

    private fun file(name: String) = File(folder, name)

    private suspend fun TestScope.loaded(markdown: String): MarkdownTodoRepository {
        file("list.md").writeText(markdown)
        val repository = MarkdownTodoRepository(
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            autosaveDelayMillis = 500,
        )
        repository.useStore(LocalDirectoryStore(folder, label = "test folder"))
        advanceUntilIdle()
        return repository
    }

    @Test
    fun `only subtrees done all the way down are split out`() {
        val tree = listOf(
            TodoItem(text = "A", done = true, children = listOf(TodoItem(text = "A1", done = true))),
            TodoItem(
                text = "B",
                done = true,
                children = listOf(TodoItem(text = "B1", done = true), TodoItem(text = "B2")),
            ),
            TodoItem(text = "C", children = listOf(TodoItem(text = "C1", done = true))),
            TodoItem(text = "D"),
        )

        val (remaining, archived) = tree.splitArchivable()

        assertEquals(listOf("A", "B1", "C1"), archived.map { it.text })
        assertEquals(listOf("B", "C", "D"), remaining.map { it.text })
        assertEquals(listOf("B2"), remaining[0].children.map { it.text })
        assertTrue(remaining[1].children.isEmpty())
    }

    @Test
    fun `archiving moves finished items into the sibling file and out of the list`() = runTest {
        val repository = loaded("- [ ] A\n- [X] B\n\t- [X] B1\n- [X] C\n")

        val archived = repository.archiveCompleted("list.md")
        advanceUntilIdle()

        assertEquals(listOf("B", "C"), archived.map { it.text })
        assertEquals("- [ ] A\n", file("list.md").readText())
        assertEquals("- [X] B\n\t- [X] B1\n- [X] C\n", file("list.archive.md").readText())
        // The archive is not a list.
        assertEquals(listOf("list.md"), repository.lists.value.map { it.fileName })
        assertEquals(listOf("B", "C"), repository.archives.value["list.md"]!!.map { it.text })
    }

    @Test
    fun `archiving again appends, and nothing finished means nothing happens`() = runTest {
        val repository = loaded("- [X] B\n- [ ] A\n")
        repository.archiveCompleted("list.md")
        repository.addItem("list.md", "late")
        repository.setItemDone("list.md", repository.lists.value.single().items.last().id, true)
        repository.archiveCompleted("list.md")
        advanceUntilIdle()
        assertEquals("- [X] B\n- [X] late\n", file("list.archive.md").readText())

        assertTrue(repository.archiveCompleted("list.md").isEmpty())
    }

    @Test
    fun `an archive is read back on reload and never listed`() = runTest {
        file("list.archive.md").writeText("- [X] old\n")
        val repository = loaded("- [ ] A\n")

        assertEquals(listOf("list.md"), repository.lists.value.map { it.fileName })
        assertEquals(listOf("old"), repository.archives.value["list.md"]!!.map { it.text })
    }

    @Test
    fun `restoring puts items back unticked at the end and empties the archive file`() = runTest {
        val repository = loaded("- [ ] A\n- [X] B\n\t- [X] B1\n")
        repository.archiveCompleted("list.md")
        val id = repository.archives.value["list.md"]!!.single().id

        repository.restoreArchived("list.md", setOf(id))
        advanceUntilIdle()

        assertEquals("- [ ] A\n- [ ] B\n\t- [ ] B1\n", file("list.md").readText())
        assertFalse(file("list.archive.md").exists())
        assertTrue(repository.archives.value.isEmpty())
    }

    @Test
    fun `deleting archived items removes them for good`() = runTest {
        val repository = loaded("- [X] B\n- [X] C\n")
        repository.archiveCompleted("list.md")
        val b = repository.archives.value["list.md"]!!.first().id

        repository.deleteArchived("list.md", setOf(b))
        advanceUntilIdle()

        assertEquals("- [X] C\n", file("list.archive.md").readText())
        assertEquals("", file("list.md").readText())
    }

    @Test
    fun `undo puts the list back as it was and takes the items off the archive`() = runTest {
        val repository = loaded("- [X] A\n- [ ] B\n- [X] C\n")
        val before = repository.lists.value.single().items
        val archived = repository.archiveCompleted("list.md")

        repository.undoArchive("list.md", before, archived)
        advanceUntilIdle()

        assertEquals("- [X] A\n- [ ] B\n- [X] C\n", file("list.md").readText())
        assertFalse(file("list.archive.md").exists())
    }

    @Test
    fun `undo after the list was edited returns the items to the end instead`() = runTest {
        val repository = loaded("- [X] A\n- [ ] B\n")
        val before = repository.lists.value.single().items
        val archived = repository.archiveCompleted("list.md")
        repository.addItem("list.md", "new")

        repository.undoArchive("list.md", before, archived)
        advanceUntilIdle()

        assertEquals("- [ ] B\n- [ ] new\n- [X] A\n", file("list.md").readText())
        assertFalse(file("list.archive.md").exists())
    }

    @Test
    fun `renaming or deleting a list takes its archive along`() = runTest {
        val repository = loaded("- [X] A\n")
        repository.archiveCompleted("list.md")

        val newId = repository.renameList("list.md", "Other")
        advanceUntilIdle()
        assertEquals("other.md", newId)
        assertTrue(file(archiveFileNameFor("other.md")).exists())
        assertFalse(file("list.archive.md").exists())
        assertEquals(listOf("A"), repository.archives.value["other.md"]!!.map { it.text })

        repository.deleteList(newId)
        advanceUntilIdle()
        assertFalse(file("other.archive.md").exists())
    }
}
