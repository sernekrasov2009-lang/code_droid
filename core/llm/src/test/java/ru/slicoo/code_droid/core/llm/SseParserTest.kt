package ru.slicoo.code_droid.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun extractsSingleDataField() {
        val data = SseParser.dataOf(
            listOf(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}",
            ),
        )
        assertEquals("{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}", data)
    }

    @Test
    fun ignoresNonDataFields() {
        val data = SseParser.dataOf(
            listOf(
                "event: message",
                "id: 42",
                "data: {\"x\":1}",
            ),
        )
        assertEquals("{\"x\":1}", data)
    }

    @Test
    fun multiLineDataIsJoinedWithNewlines() {
        val data = SseParser.dataOf(
            listOf(
                "data: line1",
                "data: line2",
            ),
        )
        assertEquals("line1\nline2", data)
    }

    @Test
    fun commentAndEmptyReturnNull() {
        assertNull(SseParser.dataOf(listOf(": keep-alive")))
        assertNull(SseParser.dataOf(emptyList()))
    }

    @Test
    fun recognizesDoneMarker() {
        assertTrue(SseParser.isDone("[DONE]"))
        assertFalse(SseParser.isDone("[done]"))
    }
}
