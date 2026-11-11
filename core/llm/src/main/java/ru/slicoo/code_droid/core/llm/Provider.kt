package ru.slicoo.code_droid.core.llm

import kotlinx.serialization.json.JsonObject
import ru.slicoo.code_droid.core.model.Message

/** Common contract every LLM adapter implements. See spec section 2. */
interface LlmProvider {
    val id: String

    suspend fun chat(request: LlmRequest): LlmResponse

    suspend fun chatStream(request: LlmRequest): Flow<StreamEvent>
}

// Placeholder types, filled in during Stage 1 (MVP chat).
data class LlmRequest(
    val model: String,
    val messages: List<Message>,
    /** Tools the model may call; null disables tool use. */
    val tools: List<ToolSpec>? = null,
)

/** A tool advertised to the model, in provider-agnostic form. */
data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema of the tool parameters. */
    val parameters: JsonObject,
)

typealias Flow<T> = kotlinx.coroutines.flow.Flow<T>

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ToolCall(val callId: String, val name: String, val arguments: String) : StreamEvent
    data class Usage(val inputTokens: Long, val outputTokens: Long) : StreamEvent
    data class Error(val throwable: Throwable) : StreamEvent
    data object Done : StreamEvent
}

data class LlmResponse(
    val text: String,
)
