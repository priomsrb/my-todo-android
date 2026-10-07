package dev.shafqat.mytodo

import dev.shafqat.mytodo.data.store.LocalDirectoryStore
import dev.shafqat.mytodo.data.store.MoveFolderException
import dev.shafqat.mytodo.data.store.moveFolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoveFolderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `lists and archives move and the source is emptied`() = runTest {
        val from = LocalDirectoryStore(tmp.newFolder("from"))
        val to = LocalDirectoryStore(tmp.newFolder("to"))
        from.write("groceries.md", "- [ ] milk\n")
        from.write("groceries.archive.md", "- [X] eggs\n")

        assertEquals(2, moveFolder(from, to))

        assertEquals(listOf("groceries.archive.md", "groceries.md"), to.listFileNames())
        assertEquals("- [ ] milk\n", to.read("groceries.md"))
        assertEquals("- [X] eggs\n", to.read("groceries.archive.md"))
        assertTrue(from.listFileNames().isEmpty())
    }

    @Test
    fun `a clash refuses the move and touches nothing`() = runTest {
        val from = LocalDirectoryStore(tmp.newFolder("from"))
        val to = LocalDirectoryStore(tmp.newFolder("to"))
        from.write("a.md", "- [ ] one\n")
        from.write("b.md", "- [ ] two\n")
        to.write("B.md", "- [ ] theirs\n")

        try {
            moveFolder(from, to)
            fail("expected a refusal")
        } catch (e: MoveFolderException) {
            assertTrue(e.message!!.contains("b.md"))
        }

        assertEquals(listOf("a.md", "b.md"), from.listFileNames())
        assertEquals(listOf("B.md"), to.listFileNames())
        assertEquals("- [ ] theirs\n", to.read("B.md"))
    }
}
