package ru.slicoo.code_droid.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.slicoo.code_droid.core.model.Message
import ru.slicoo.code_droid.core.model.MessagePart
import ru.slicoo.code_droid.core.model.Role

/** Decoding of saved SSE samples, per spec section 17. */
class OpenAiWireTest {

    @Test
    fun decodesTextDeltaChunk() {
        val chunk = OpenAiWire.decodeChunk(
            """{"choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}""",
        )
        assertEquals("Hel", chunk?.choices?.first()?.delta?.content)
    }

    @Test
    fun decodesFinalChunkWithUsage() {
        val chunk = OpenAiWire.decodeChunk(
            """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":9,"completion_tokens":12,"total_tokens":21}}""",
        )
        assertEquals("stop", chunk?.choices?.first()?.finishReason)
        assertEquals(9L, chunk?.usage?.promptTokens)
        assertEquals(12L, chunk?.usage?.completionTokens)
    }

    @Test
    fun decodesUsageOnlyChunkAfterDone() {
        // OpenRouter-style final usage chunk with empty choices.
        val chunk = OpenAiWire.decodeChunk(
            """{"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":50}}""",
        )
        assertTrue(chunk?.choices?.isEmpty() == true)
        assertEquals(100L, chunk?.usage?.promptTokens)
    }

    @Test
    fun toleratesUnknownFields() {
        val chunk = OpenAiWire.decodeChunk(
            """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini","system_fingerprint":"fp1","choices":[{"index":0,"delta":{"content":"!"}}]}""",
        )
        assertEquals("!", chunk?.choices?.first()?.delta?.content)
    }

    @Test
    fun emptyContentStaysNull() {
        val chunk = OpenAiWire.decodeChunk(
            """{"choices":[{"delta":{"role":"assistant","content":null}}]}""",
        )
        assertNull(chunk?.choices?.first()?.delta?.content)
    }

    @Test
    fun encodesRequestWithStreamAndUsage() {
        val request = LlmRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                Message(
                    id = "1",
                    role = Role.USER,
                    parts = listOf(MessagePart.Text("привет")),
                    createdAt = 0L,
                ),
            ),
        )
        val encoded = OpenAiWire.json.encodeToString(
            OpenAiWire.ChatRequest.serializer(),
            OpenAiWire.encodeChatRequest(request),
        )
        assertTrue(encoded.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(encoded.contains("\"role\":\"user\""))
        assertTrue(encoded.contains("\"content\":\"привет\""))
        assertTrue(encoded.contains("\"stream\":true"))
        assertTrue(encoded.contains("\"include_usage\":true"))
    }

    @Test
    fun assistantAndToolRolesMap() {
        assertEquals("assistant", OpenAiWire.wireRole(Role.ASSISTANT))
        assertEquals("tool", OpenAiWire.wireRole(Role.TOOL))
        assertEquals("system", OpenAiWire.wireRole(Role.SYSTEM))
    }
}
