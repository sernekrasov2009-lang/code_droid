package ru.slicoo.code_droid.core.llm

/**
 * Incremental parser for `text/event-stream` frames. One SSE event per call:
 * lines until an empty line, only `data:` fields are used, per SSE spec.
 */
internal object SseParser {

    /** Extracts the data payload of one SSE event from [lines]. */
    fun dataOf(lines: List<String>): String? {
        val data = lines
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").removePrefix(" ") }
        return data.ifEmpty { null }
    }

    fun isDone(data: String): Boolean = data == "[DONE]"
}
