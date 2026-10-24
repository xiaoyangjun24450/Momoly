package me.rerere.momoly.domain.chat

import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.ChatSession

/** Commits the final message and timed world-info state as one repository transaction. */
class CommitChatGenerationUseCase(
    private val repository: ChatDataRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        message: ChatMessage,
        timedWorldInfo: Map<String, Int>,
    ): ChatSession? {
        repository.commitGeneration(sessionId, message, timedWorldInfo)
        return repository.get(sessionId)
    }
}
