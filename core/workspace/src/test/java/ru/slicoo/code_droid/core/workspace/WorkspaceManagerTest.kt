package ru.slicoo.code_droid.core.workspace

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        assertFailsWith<IllegalArgumentException> {
            manager.resolve("../escape.txt")
        }
    }

    @Test
    fun resolveRejectsAbsolutePathOutsideRoot() {
        val manager = WorkspaceManager(tempRoot())
        assertFailsWith<IllegalArgumentException> {
            manager.resolve("C:/Windows/system32/config")
        }
    }

    private fun tempRoot(): File = createTempDir(prefix = "ws-test").apply {
        resolve("nested").mkdirs()
    }
}
