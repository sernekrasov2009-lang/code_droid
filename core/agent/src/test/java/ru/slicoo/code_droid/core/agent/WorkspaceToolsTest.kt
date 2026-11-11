package ru.slicoo.code_droid.core.agent

import kotlinx.coroutines.test.runTest
import ru.slicoo.code_droid.core.workspace.WorkspaceManager
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolsTest {

    @Test
    fun executeRunsReadAndWrite() = runTest {
        val root = createTempDirectory("tools-workspace").toFile()
        val tools = WorkspaceTools(WorkspaceManager(root))

        tools.execute("write", """{"path":"a.txt","content":"привет мир"}""")
        val readResult = tools.execute("read", """{"path":"a.txt"}""")

        assertEquals("привет мир", readResult)
    }

    @Test
    fun unknownToolReturnsError() = runTest {
        val tools = WorkspaceTools(WorkspaceManager(createTempDirectory("tools-workspace").toFile()))

        val result = tools.execute("shell", """{}""")

        assertTrue(result.startsWith("ERROR"))
    }

    @Test
    fun invalidJsonReturnsError() = runTest {
        val tools = WorkspaceTools(WorkspaceManager(createTempDirectory("tools-workspace").toFile()))

        val result = tools.execute("read", "not json")

        assertTrue(result.startsWith("ERROR"))
    }
}
