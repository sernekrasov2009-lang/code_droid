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
        val manager = WorkspaceManager(tempRoot())
        assertThrows(IllegalArgumentException::class.java) {
            manager.resolve("../escape.txt")
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

    private fun tempRoot(): File = kotlin.io.path.createTempDirectory("ws-test").toFile().apply {
        resolve("nested").mkdirs()
    }
}
