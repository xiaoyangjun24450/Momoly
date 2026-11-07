package me.rerere.momoly.domain

import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.ChatSession

data class ChatGenerationPlan(
    val message: ChatMessage,
    val buildHistory: List<ChatMessage>,
    val promptHistory: List<ChatMessage>,
    val updateTimedWorldInfo: Boolean,
)

object ChatGenerationPlanner {
    fun create(session: ChatSession, modelId: String): ChatGenerationPlan = ChatGenerationPlan(
        message = ChatMessage(
            role = "assistant",
            model = modelId,
            ts = ConversationOps.nextTs(session),
        ),
        buildHistory = session.messages,
        promptHistory = session.messages,
        updateTimedWorldInfo = true,
    )
}
