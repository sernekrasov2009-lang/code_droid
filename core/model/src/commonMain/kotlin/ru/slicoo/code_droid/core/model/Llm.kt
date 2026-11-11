package ru.slicoo.code_droid.core.model

import kotlinx.serialization.Serializable

/** Role of a chat message. */
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/** A single logical chunk of a message (text, reasoning, tool call, ...). */
@Serializable
sealed interface MessagePart {
    @Serializable
    data class Text(val text: String) : MessagePart
}

/** One message in a conversation. */
@Serializable
data class Message(
    val id: String,
    val role: Role,
    val parts: List<MessagePart>,
    val createdAt: Long,
)
