package ru.slicoo.code_droid.core.agent

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import ru.slicoo.code_droid.core.llm.LlmProvider
import ru.slicoo.code_droid.core.llm.LlmRequest
import ru.slicoo.code_droid.core.llm.StreamEvent
import ru.slicoo.code_droid.core.llm.ToolSpec
import ru.slicoo.code_droid.core.model.Message
import ru.slicoo.code_droid.core.model.MessagePart
import ru.slicoo.code_droid.core.model.Role
import ru.slicoo.code_droid.core.workspace.WorkspaceManager
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {

    /** Provider that first requests a tool call, then answers in plain text. */
    private class FakeProvider(private val responses: List<List<StreamEvent>>) : LlmProvider {
        override val id: String = "fake"
        var calls = 0

        override suspend fun chatStream(request: LlmRequest) = flow {
            val response = responses[calls]
            calls++
            response.forEach { emit(it) }
        }

        override suspend fun chat(request: LlmRequest) = ru.slicoo.code_droid.core.llm.LlmResponse("")
    }

    @Test
    fun toolCallIsExecutedAndResultFedBack() = runTest {
        val root = createTempDirectory("agent-workspace").toFile()
        val workspace = WorkspaceManager(root)
        val tools = WorkspaceTools(workspace)

        val provider = FakeProvider(
            listOf(
                listOf(
                    StreamEvent.ToolCall("call-1", "write", """{"path":"notes.txt","content":"hello"}"""),
                ),
                listOf(
                    StreamEvent.TextDelta("готово"),
                    StreamEvent.Done,
                ),
            ),
        )
        val loop = AgentLoop(provider = provider, tools = tools)
        val history = listOf(userMessage("создай файл notes.txt с текстом hello"))

        val events = mutableListOf<AgentEvent>()
        val transcript = loop.run("test-model", history) { events += it }

        assertEquals(2, provider.calls)
        assertEquals(1, calls(events) { it is AgentEvent.ToolStarted })
        assertEquals(1, calls(events) { it is AgentEvent.ToolFinished })
        // transcript: system, user, assistant tool-call, tool result, assistant text
        assertEquals(5, transcript.size)
        assertEquals(Role.TOOL, transcript[3].role)
        val result = transcript[3].parts.filterIsInstance<MessagePart.ToolResult>().single()
        assertTrue(result.content.contains("wrote notes.txt"))
        assertTrue(File(root, "notes.txt").readText().contains("hello"))
        val finalText = transcript[4].parts.filterIsInstance<MessagePart.Text>().single()
        assertEquals("готово", finalText.text)
    }

    @Test
    fun declinedMutationReportsRejectionToModel() = runTest {
        val root = createTempDirectory("agent-workspace").toFile()
        val tools = WorkspaceTools(WorkspaceManager(root))
        val provider = FakeProvider(
            listOf(
                listOf(
                    StreamEvent.ToolCall("call-1", "write", """{"path":"notes.txt","content":"hello"}"""),
                ),
                listOf(StreamEvent.Done),
            ),
        )
        val loop = AgentLoop(
            provider = provider,
            tools = tools,
            approval = { _, _ -> false },
        )

        val transcript = loop.run("test-model", listOf(userMessage("запиши файл"))) { }

        val result = transcript[3].parts.filterIsInstance<MessagePart.ToolResult>().single()
        assertTrue(result.content.contains("declined"))
        assertEquals(false, File(root, "notes.txt").exists())
    }

    @Test
    fun plainAnswerWithoutToolCallsEndsLoop() = runTest {
        val root = createTempDirectory("agent-workspace").toFile()
        val tools = WorkspaceTools(WorkspaceManager(root))
        val provider = FakeProvider(
            listOf(listOf(StreamEvent.TextDelta("просто ответ"), StreamEvent.Done)),
        )
        val loop = AgentLoop(provider = provider, tools = tools)

        val transcript = loop.run("test-model", listOf(userMessage("привет"))) { }

        assertEquals(1, provider.calls)
        // transcript: system, user, assistant answer.
        assertEquals(3, transcript.size)
    }

    @Test
    fun maxTurnsStopsTheLoop() = runTest {
        val root = createTempDirectory("agent-workspace").toFile()
        val tools = WorkspaceTools(WorkspaceManager(root))
        // Every turn requests the same read of a missing file.
        val toolCallTurn = listOf(
            StreamEvent.ToolCall("call-1", "read", """{"path":"missing.txt"}"""),
        )
        val provider = object : LlmProvider {
            override val id: String = "fake"
            var calls = 0
            override suspend fun chatStream(request: LlmRequest) = flow {
                emit(StreamEvent.ToolCall("call-${calls}", "read", """{"path":"missing.txt"}"""))
            }
            override suspend fun chat(request: LlmRequest) = ru.slicoo.code_droid.core.llm.LlmResponse("")
        }
        val loop = AgentLoop(provider = provider, tools = tools, maxTurns = 3)

        val events = mutableListOf<AgentEvent>()
        loop.run("test-model", listOf(userMessage("читай"))) { events += it }

        assertTrue(events.any { it is AgentEvent.Error && it.message.contains("лимит") })
    }

    private fun userMessage(text: String) = Message(
        id = "u-${System.nanoTime()}",
        role = Role.USER,
        parts = listOf(MessagePart.Text(text)),
        createdAt = System.currentTimeMillis(),
    )

    private fun calls(events: List<AgentEvent>, predicate: (AgentEvent) -> Boolean): Int =
        events.count(predicate)
}
