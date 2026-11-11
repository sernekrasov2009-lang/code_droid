package ru.slicoo.code_droid.core.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.slicoo.code_droid.core.llm.OpenAiWire as Wire

/**
 * OpenAI-compatible chat adapter: POST {baseUrl}/chat/completions with
 * `stream: true`. Works with OpenAI, OpenRouter, Groq, Ollama-style local
 * servers and any API that mirrors the chat/completions contract.
 */
class OpenAiCompatibleProvider(
    override val id: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val client: HttpClient,
) : LlmProvider {

    override suspend fun chat(request: LlmRequest): LlmResponse {
        val text = StringBuilder()
        chatStream(request).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> text.append(event.text)
                is StreamEvent.ToolCall -> Unit
                is StreamEvent.Error -> throw event.throwable
                is StreamEvent.Usage -> Unit
                StreamEvent.Done -> Unit
            }
        }
        return LlmResponse(text.toString())
    }

    override suspend fun chatStream(request: LlmRequest): Flow<StreamEvent> = flow {
        val payload = Wire.json.encodeToString(
            Wire.ChatRequest.serializer(),
            Wire.encodeChatRequest(request),
        )
        val response: HttpResponse = client.post("$baseUrl/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header(HttpHeaders.Accept, "text/event-stream")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw LlmHttpException(response.status.value, response.bodyAsText())
        }

        val channel = response.bodyAsChannel()
        val pending = mutableListOf<String>()
        // Tool calls stream in fragments; accumulate by index.
        val toolCallAccumulator = mutableMapOf<Int, AccumulatedToolCall>()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (line.isEmpty()) {
                // End of one SSE event: flush it.
                val data = SseParser.dataOf(pending)
                pending.clear()
                if (data == null || SseParser.isDone(data)) continue
                val chunk = Wire.decodeChunk(data) ?: continue
                emitEvents(chunk, toolCallAccumulator)
            } else {
                pending += line
            }
        }
        // Flush accumulated tool calls after the stream ends.
        for (call in toolCallAccumulator.values.sortedBy { it.index }) {
            emit(
                StreamEvent.ToolCall(
                    callId = call.id ?: "call_${call.index}",
                    name = call.name ?: "",
                    arguments = call.arguments.toString(),
                ),
            )
        }
        emit(StreamEvent.Done)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitEvents(
        chunk: Wire.Chunk,
        toolCallAccumulator: MutableMap<Int, AccumulatedToolCall>,
    ) {
        val delta = chunk.choices.firstOrNull()?.delta?.content
        if (!delta.isNullOrEmpty()) {
            emit(StreamEvent.TextDelta(delta))
        }
        chunk.choices.firstOrNull()?.delta?.toolCalls?.forEach { call ->
            val acc = toolCallAccumulator.getOrPut(call.index) {
                AccumulatedToolCall(index = call.index, id = call.id)
            }
            call.id?.let { acc.id = it }
            call.function?.name?.let { acc.name = it }
            call.function?.arguments?.let { acc.arguments.append(it) }
        }
        val usage = chunk.usage
        if (usage != null) {
            emit(StreamEvent.Usage(usage.promptTokens, usage.completionTokens))
        }
    }

    /** Mutable accumulation state for one streamed tool call. */
    private class AccumulatedToolCall(
        val index: Int,
        var id: String?,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )
}

/** HTTP-level failure carrying the provider's error payload, if any. */
class LlmHttpException(val statusCode: Int, val body: String) : Exception(body)
