package ru.slicoo.code_droid.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.slicoo.code_droid.core.model.Message
import ru.slicoo.code_droid.core.model.MessagePart
import ru.slicoo.code_droid.core.model.Role

/**
 * Serialization of the OpenAI-compatible wire format (chat/completions).
 * Kept separate from the provider so it can be unit-tested on saved
 * SSE samples without any networking (spec section 17).
 */
internal object OpenAiWire {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<WireMessage>,
        val tools: List<WireTool>? = null,
        // No default: encodeDefaults=false drops defaulted properties,
        // and streaming must always be requested on the wire.
        val stream: Boolean,
        @SerialName("stream_options")
        val streamOptions: StreamOptions? = null,
    )

    @Serializable
    data class StreamOptions(
        // No default: with encodeDefaults=false a defaulted property would be
        // dropped from the payload, and we always want it on the wire.
        @SerialName("include_usage") val includeUsage: Boolean,
    )

    @Serializable
    data class WireMessage(
        val role: String,
        val content: String? = null,
        @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
        @SerialName("tool_call_id") val toolCallId: String? = null,
    )

    @Serializable
    data class WireTool(
        val type: String = "function",
        val function: WireFunction,
    ) {
        @Serializable
        data class WireFunction(
            val name: String,
            val description: String? = null,
            val parameters: JsonElement? = null,
        )
    }

    @Serializable
    data class WireToolCall(
        val index: Int = 0,
        val id: String? = null,
        val type: String = "function",
        val function: WireToolCallFunction? = null,
    ) {
        @Serializable
        data class WireToolCallFunction(
            val name: String? = null,
            val arguments: String? = null,
        )
    }

    @Serializable
    data class Chunk(
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null,
    ) {
        @Serializable
        data class Choice(
            val delta: Delta? = null,
            @SerialName("finish_reason") val finishReason: String? = null,
        ) {
            @Serializable
            data class Delta(
                val content: String? = null,
                @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
            )
        }

        @Serializable
        data class Usage(
            @SerialName("prompt_tokens") val promptTokens: Long = 0,
            @SerialName("completion_tokens") val completionTokens: Long = 0,
        )
    }

    fun encodeChatRequest(request: LlmRequest): ChatRequest = ChatRequest(
        model = request.model,
        messages = request.messages.map(::toWire),
        tools = request.tools?.map(::toWireTool),
        stream = true,
        streamOptions = StreamOptions(includeUsage = true),
    )

    private fun toWireTool(spec: ToolSpec): WireTool = WireTool(
        function = WireTool.WireFunction(
            name = spec.name,
            description = spec.description,
            parameters = spec.parameters,
        ),
    )

    internal fun toWire(message: Message): WireMessage {
        // Assistant tool-call messages and tool results have their own wire
        // shapes; everything else is a plain text message.
        val toolCalls = message.parts.filterIsInstance<MessagePart.ToolCall>()
        if (message.role == Role.ASSISTANT && toolCalls.isNotEmpty()) {
            return WireMessage(
                role = "assistant",
                content = message.parts.filterIsInstance<MessagePart.Text>()
                    .joinToString("") { it.text }.ifEmpty { null },
                toolCalls = toolCalls.map {
                    WireToolCall(
                        id = it.callId,
                        function = WireToolCall.WireToolCallFunction(
                            name = it.name,
                            arguments = it.arguments,
                        ),
                    )
                },
            )
        }
        if (message.role == Role.TOOL) {
            val result = message.parts.filterIsInstance<MessagePart.ToolResult>().firstOrNull()
            return WireMessage(
                role = "tool",
                content = message.parts.filterIsInstance<MessagePart.Text>()
                    .joinToString("") { it.text }.ifEmpty { null }
                    ?: result?.content
                    ?: "",
                toolCallId = result?.callId,
            )
        }
        return WireMessage(
            role = wireRole(message.role),
            content = message.parts.filterIsInstance<MessagePart.Text>()
                .joinToString("") { it.text },
        )
    }

    internal fun wireRole(role: Role): String = when (role) {
        Role.SYSTEM -> "system"
        Role.USER -> "user"
        Role.ASSISTANT -> "assistant"
        Role.TOOL -> "tool"
    }

    fun decodeChunk(data: String): Chunk? = json.decodeFromString(
        Chunk.serializer(), data,
    )
}
