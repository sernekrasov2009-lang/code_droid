package ru.slicoo.code_droid.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
        val content: String,
    )

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
            data class Delta(val content: String? = null)
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
        stream = true,
        streamOptions = StreamOptions(includeUsage = true),
    )

    private fun toWire(message: Message): WireMessage = WireMessage(
        role = wireRole(message.role),
        content = message.parts.filterIsInstance<MessagePart.Text>()
            .joinToString("") { it.text },
    )

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
