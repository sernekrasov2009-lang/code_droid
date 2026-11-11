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
        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (line.isEmpty()) {
                // End of one SSE event: flush it.
                val data = SseParser.dataOf(pending)
                pending.clear()
                if (data == null || SseParser.isDone(data)) continue
                val chunk = Wire.decodeChunk(data) ?: continue
                emitEvents(chunk)
            } else {
                pending += line
            }
        }
        emit(StreamEvent.Done)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitEvents(
        chunk: Wire.Chunk,
    ) {
        val delta = chunk.choices.firstOrNull()?.delta?.content
        if (!delta.isNullOrEmpty()) {
            emit(StreamEvent.TextDelta(delta))
        }
        val usage = chunk.usage
        if (usage != null) {
            emit(StreamEvent.Usage(usage.promptTokens, usage.completionTokens))
        }
    }
}

/** HTTP-level failure carrying the provider's error payload, if any. */
class LlmHttpException(val statusCode: Int, val body: String) : Exception(body)
