package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.collapse.InMemoryCollapseStore
import dev.shafqat.mytodo.data.prefs.InMemoryListPrefsStore
import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.model.splitDictation
import dev.shafqat.mytodo.widget.addDictated
import dev.shafqat.mytodo.widget.removeDictated
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What the voice widget does once the recogniser hands back a sentence.
 *
 * The dictation sheet itself needs a real microphone and cannot be driven here, so these cover the
 * half that reaches the user's file: that a sentence lands as the items it named, at the top of the
 * list and in the order spoken, that it is on disk straight away rather than waiting on the
 * autosave debounce — nothing keeps that process alive — and that undo takes back exactly what was
 * added.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCaptureTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var folder: File

    @Before
    fun setUp() {
        folder = temporaryFolder.newFolder("todo")
        File(folder, "groceries.md").writeText("- [ ] Bread\n")
    }

    private fun TestScope.repository() = MarkdownTodoRepository(
        scope = this,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        // Long enough that anything reaching the file inside a test did so by being flushed.
        autosaveDelayMillis = 60_000L,
        collapseStore = InMemoryCollapseStore(),
        listPrefsStore = InMemoryListPrefsStore(),
    )

    private suspend fun TestScope.loadedRepository(): MarkdownTodoRepository {
        val repository = repository()
        repository.useStore(LocalDirectoryStore(folder))
        advanceUntilIdle()
        return repository
    }

    private fun fileText() = File(folder, "groceries.md").readText()

    @Test
    fun `one spoken sentence lands on top, on disk without waiting for autosave`() = runTest {
        val repository = loadedRepository()

        repository.addDictated("groceries.md", splitDictation("buy oat milk"))
        advanceUntilIdle()

        assertEquals("- [ ] buy oat milk\n- [ ] Bread\n", fileText())
    }

    @Test
    fun `several things in one sentence go on top together, in the order spoken`() = runTest {
        val repository = loadedRepository()

        repository.addDictated("groceries.md", splitDictation("milk and then bread and then bin bags"))
        advanceUntilIdle()

        assertEquals(
            listOf("milk", "bread", "bin bags", "Bread"),
            repository.lists.value.single().items.map { it.text },
        )
        assertEquals("- [ ] milk\n- [ ] bread\n- [ ] bin bags\n- [ ] Bread\n", fileText())
    }

    @Test
    fun `a later capture goes above an earlier one, each batch still in spoken order`() = runTest {
        val repository = loadedRepository()

        repository.addDictated("groceries.md", splitDictation("milk and then bread"))
        advanceUntilIdle()
        repository.addDictated("groceries.md", splitDictation("apples and then pears"))
        advanceUntilIdle()

        assertEquals(
            listOf("apples", "pears", "milk", "bread", "Bread"),
            repository.lists.value.single().items.map { it.text },
        )
    }

    @Test
    fun `nested items below keep their nesting when something is put on top`() = runTest {
        File(folder, "groceries.md").writeText("- [ ] Shop\n\t- [ ] Milk\n\t- [ ] Bread\n- [ ] Chores\n")
        val repository = loadedRepository()

        repository.addDictated("groceries.md", splitDictation("call the plumber"))
        advanceUntilIdle()

        assertEquals(
            "- [ ] call the plumber\n- [ ] Shop\n\t- [ ] Milk\n\t- [ ] Bread\n- [ ] Chores\n",
            fileText(),
        )
    }

    @Test
    fun `undo removes exactly what was added and leaves the list as it was`() = runTest {
        val repository = loadedRepository()

        val added = repository.addDictated("groceries.md", splitDictation("milk and then bin bags"))
        advanceUntilIdle()
        assertEquals(2, added.size)

        repository.removeDictated("groceries.md", added)
        advanceUntilIdle()

        assertEquals("- [ ] Bread\n", fileText())
    }

    @Test
    fun `undo leaves anything added since the capture alone`() = runTest {
        val repository = loadedRepository()

        val added = repository.addDictated("groceries.md", splitDictation("milk"))
        advanceUntilIdle()
        repository.addItem("groceries.md", "Typed afterwards")
        advanceUntilIdle()

        repository.removeDictated("groceries.md", added)
        advanceUntilIdle()

        assertEquals(
            listOf("Bread", "Typed afterwards"),
            repository.lists.value.single().items.map { it.text },
        )
    }

    @Test
    fun `speech that named nothing writes nothing`() = runTest {
        val repository = loadedRepository()

        val added = repository.addDictated("groceries.md", splitDictation("   "))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), added)
        assertEquals("- [ ] Bread\n", fileText())
    }
}
