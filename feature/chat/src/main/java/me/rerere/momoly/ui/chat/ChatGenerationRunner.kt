package me.rerere.momoly.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.momoly.core.diagnostics.SafeLog
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ImageGenerationSettings
import me.rerere.momoly.data.api.ReasoningLevel
import me.rerere.momoly.data.character.CharRegex
import me.rerere.momoly.data.character.CharacterCard
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.domain.chat.ChatDataRepository
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.data.preset.StPreset
import me.rerere.momoly.data.worldbook.WorldBook
import me.rerere.momoly.domain.GenerationEngine
import me.rerere.momoly.domain.chat.CommitChatGenerationUseCase
import me.rerere.momoly.domain.InterruptedReplyDraft

internal class ChatGenerationRunner(
    private val chatRepository: ChatDataRepository,
    private val generation: GenerationEngine,
    private val resources: ChatGenerationResources,
    private val prepare: PrepareChatGenerationUseCase,
    private val commit: CommitChatGenerationUseCase,
    private val searchTool: ChatSearchTool,
) {
    data class Request(
        val sessionId: String,
        val provider: ApiProvider,
        val modelId: String,
        val card: CharacterCard?,
        val userName: String,
        val worldBooks: List<WorldBook>,
        val preset: StPreset?,
        val promptRegex: List<CharRegex>,
        val reasoning: ReasoningLevel,
        val imageGeneration: ImageGenerationSettings,
        val searchEnabled: Boolean,
        val deferDelivery: Boolean = false,
        val interruptedDraft: InterruptedReplyDraft? = null,
    )

    data class Result(
        val completedSession: ChatSession?,
        val finalMessage: ChatMessage,
        val prepared: PreparedChatGeneration,
    )

    suspend fun run(
        request: Request,
        onStarted: (ChatSession, ChatMessage) -> Unit,
        onLocalVariablesCommitted: (Map<String, String>) -> Unit,
        onUpdate: (ChatMessage) -> Unit,
    ): Result? {
        val prepared = withContext(Dispatchers.Default) { prepare(request) } ?: return null
        val base = prepared.baseSession
        val generationMessage = prepared.generationMessage
        onStarted(base, generationMessage)
        val assembled = prepared.prompt
        val built = assembled.built
        val variableState = assembled.variableState
        val localChanged = prepared.commitVariables && variableState.localChanged()
        val globalChanged = prepared.commitVariables && variableState.globalChanged()
        var localCommitted = false
        var globalCommitted = false

        try {
            if (localChanged && !request.deferDelivery) {
                chatRepository.saveLocalVariables(
                    request.sessionId,
                    variableState.localVariables(),
                )
                localCommitted = true
                onLocalVariablesCommitted(variableState.localVariables())
            }
            if (globalChanged && !request.deferDelivery) {
                resources.saveGlobalVariables(variableState.globalVariables())
                globalCommitted = true
            }
            val finalMessage = generation.run(
                apiMessages = assembled.apiMessages,
                genMessage = generationMessage,
                providerId = request.provider.id,
                providerName = request.provider.name,
                modelId = request.modelId,
                built = built,
                streaming = request.card?.streaming ?: true,
                tools = if (prepared.useSearchTool) listOf(searchTool.spec()) else emptyList(),
                toolExecutor = if (prepared.useSearchTool) searchTool.executor() else null,
                persistGeneratedImage = resources::persistGeneratedImage,
                errorText = resources::errorText,
                throwOnFailure = request.deferDelivery,
                onUpdate = onUpdate,
            )
            if (request.deferDelivery && finalMessage.content.isBlank() && finalMessage.images.isEmpty()) {
                error("The model returned an empty reply")
            }
            val completedSession = if (request.deferDelivery) null else
                commit(request.sessionId, finalMessage, assembled.built.timedWi)
            onUpdate(finalMessage)
            return Result(
                completedSession = completedSession,
                finalMessage = finalMessage,
                prepared = prepared,
            )
        } catch (error: Exception) {
            if (localCommitted) {
                runCatching {
                    chatRepository.saveLocalVariables(request.sessionId, base.localVariables)
                }.onFailure { rollbackError ->
                    error.addSuppressed(rollbackError)
                    SafeLog.warn(TAG, "session_variables_rollback_failed", rollbackError)
                }
            }
            if (globalCommitted) {
                runCatching {
                    resources.saveGlobalVariables(variableState.initialGlobalVariables())
                }.onFailure { rollbackError ->
                    error.addSuppressed(rollbackError)
                    SafeLog.warn(TAG, "global_variables_rollback_failed", rollbackError)
                }
            }
            throw error
        }
    }

    /** Called only through the scheduler's serialized publication gate. */
    suspend fun publish(result: Result, text: String, index: Int): ChatMessage {
        val prepared = result.prepared
        val sessionId = prepared.baseSession.id
        val final = result.finalMessage
        val metadata = chatRepository.getMetadata(sessionId) ?: error("Conversation no longer exists")
        val timestamp = if (index == 0 && final.alts.isNotEmpty()) final.ts else
            maxOf(System.currentTimeMillis(), (metadata.messageTimestamps.lastOrNull() ?: 0L) + 1)
        val message = if (index == 0) final.copy(
            content = text, ts = timestamp,
            alts = final.alts.mapIndexed { i, alt -> if (i == final.altIdx) alt.copy(content = text) else alt },
        ) else ChatMessage(role = "assistant", content = text, model = final.model, ts = timestamp)
        if (index == 0) {
            val variables = prepared.prompt.variableState
            if (prepared.commitVariables && variables.globalChanged()) {
                resources.saveGlobalVariables(variables.globalVariables())
            }
            chatRepository.commitReply(sessionId, message, prepared.prompt.built.timedWi,
                variables.localVariables().takeIf { prepared.commitVariables && variables.localChanged() })
        } else chatRepository.putMessage(sessionId, message)
        return message
    }

    suspend fun metadata(sessionId: String): ChatSession? = chatRepository.getMetadata(sessionId)

    private companion object {
        const val TAG = "ChatGenerationRunner"
    }
}
