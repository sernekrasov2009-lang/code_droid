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
        val manager = WorkspaceManager(tempRoot())
        assertThrows(IllegalArgumentException::class.java) {
            manager.resolve("C:/Windows/system32/config")
        }
    }

    private fun tempRoot(): File = kotlin.io.path.createTempDirectory("ws-test").toFile().apply {
        resolve("nested").mkdirs()
    }
}
