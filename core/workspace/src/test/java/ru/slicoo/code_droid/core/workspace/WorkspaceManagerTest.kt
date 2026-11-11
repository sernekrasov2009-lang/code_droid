package ru.slicoo.code_droid.core.workspace

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceManagerTest {

    @Test
    fun resolveAcceptsPathInsideRoot() {
        val root = tempRoot()
        val manager = WorkspaceManager(root)
        val resolved = manager.resolve("src/main.kt")
        assertEquals(File(root, "src/main.kt").canonicalFile, resolved)
    }

    @Test
    fun resolveRejectsTraversalOutsideRoot() {
        // Build a sibling of the workspace root and escape into it via "..",
        // so the test does not depend on temp-dir layout or OS quirks.
        val parent = kotlin.io.path.createTempDirectory("ws-parent").toFile()
        val root = File(parent, "ws-root").apply { mkdirs() }
        File(parent, "sibling.txt").createNewFile()
        val manager = WorkspaceManager(root)
        assertThrows(IllegalArgumentException::class.java) {
            manager.resolve("../sibling.txt")
        }
    }

    @Test
    fun resolveRejectsAbsolutePathOutsideRoot() {
        // A truly absolute path on the current platform, guaranteed outside
        // any temp directory: drive-rooted on Windows, slash-rooted on Unix.
        val root = tempRoot()
        val manager = WorkspaceManager(root)
        val outside = root.canonicalFile.toPath().root!!
            .resolve("definitely-outside.txt").toFile().path
        assertThrows(IllegalArgumentException::class.java) {
            manager.resolve(outside)
        }
    }

    @Test
    fun readReturnsFileContent() {
        val root = tempRoot()
        File(root, "a.txt").writeText("привет")
        val manager = WorkspaceManager(root)
        assertEquals("привет", manager.read("a.txt"))
    }

    @Test
    fun readSupportsOffsetAndLimit() {
        val root = tempRoot()
        File(root, "a.txt").writeText("abcdef")
        val manager = WorkspaceManager(root)
        assertEquals("cd", manager.read("a.txt", offset = 2, limit = 2))
    }

    @Test
    fun writeCreatesParentDirectories() {
        val root = tempRoot()
        val manager = WorkspaceManager(root)
        manager.write("src/ru/Main.kt", "fun main() {}")
        assertEquals("fun main() {}", File(root, "src/ru/Main.kt").readText())
    }

    @Test
    fun editReplacesExactUniqueMatch() {
        val root = tempRoot()
        File(root, "a.txt").writeText("val version = 1")
        val manager = WorkspaceManager(root)
        assertEquals(1, manager.edit("a.txt", "1", "2"))
        assertEquals("val version = 2", File(root, "a.txt").readText())
    }

    @Test
    fun editFailsWhenOldStringAmbiguous() {
        val root = tempRoot()
        File(root, "a.txt").writeText("a-a-a")
        val manager = WorkspaceManager(root)
        assertThrows(IllegalArgumentException::class.java) {
            manager.edit("a.txt", "a", "b")
        }
    }

    @Test
    fun editReplaceAllHandlesMultipleOccurrences() {
        val root = tempRoot()
        File(root, "a.txt").writeText("a-a-a")
        val manager = WorkspaceManager(root)
        assertEquals(3, manager.edit("a.txt", "a", "b", replaceAll = true))
        assertEquals("b-b-b", File(root, "a.txt").readText())
    }

    @Test
    fun globMatchesAcrossDirectories() {
        val root = tempRoot()
        File(root, "nested/x/Main.kt").apply { parentFile.mkdirs(); writeText("") }
        File(root, "nested/y/Main.java").apply { parentFile.mkdirs(); writeText("") }
        File(root, "README.md").writeText("")
        val manager = WorkspaceManager(root)
        val matches = manager.glob("**/*.kt")
        assertEquals(listOf("nested/x/Main.kt"), matches)
    }

    @Test
    fun globSingleStarStaysWithinSegment() {
        val root = tempRoot()
        File(root, "a/b.txt").apply { parentFile.mkdirs(); writeText("") }
        File(root, "b.txt").writeText("")
        val manager = WorkspaceManager(root)
        assertEquals(listOf("b.txt"), manager.glob("*.txt"))
    }

    @Test
    fun grepFindsMatchesWithLineNumbers() {
        val root = tempRoot()
        File(root, "a.kt").writeText("fun a() {}\nfun b() {}\n")
        val manager = WorkspaceManager(root)
        val matches = manager.grep("fun b")
        assertEquals(1, matches.size)
        assertEquals("a.kt", matches[0].path)
        assertEquals(2, matches[0].line)
    }

    @Test
    fun listSortsDirectoriesFirst() {
        val root = tempRoot()
        File(root, "zzz.txt").writeText("")
        File(root, "nested").mkdirs()
        val manager = WorkspaceManager(root)
        val paths = manager.list().map { it.path }
        assertEquals(listOf("nested", "zzz.txt"), paths)
    }

    private fun tempRoot(): File = kotlin.io.path.createTempDirectory("ws-test").toFile().apply {
        resolve("nested").mkdirs()
    }
}
