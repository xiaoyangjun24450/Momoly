package me.rerere.momoly.ui.chat

import kotlinx.coroutines.CoroutineScope
import me.rerere.momoly.data.api.ApiConfig
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ImageGenerationSettings
import me.rerere.momoly.data.api.ReasoningLevel
import me.rerere.momoly.data.character.CharRegex
import me.rerere.momoly.data.character.CharacterCard
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.data.preset.StPreset
import me.rerere.momoly.data.worldbook.WorldBook
import me.rerere.momoly.domain.chat.ChatSessionCoordinator
import org.json.JSONObject

internal data class ChatGenerationSnapshot(
    val card: CharacterCard?,
    val userName: String,
    val worldBooks: List<WorldBook>,
    val preset: StPreset?,
    val promptRegex: List<CharRegex>,
    val reasoning: ReasoningLevel,
    val imageGeneration: ImageGenerationSettings,
    val searchEnabled: Boolean,
    val apiConfig: ApiConfig,
)

/** 协调生成状态更新以及响应完成后的生命周期。 */
internal class ChatGenerationOrchestrator(
    private val scope: CoroutineScope,
    private val runner: ChatGenerationRunner,
    private val generationState: ChatGenerationCoordinator,
    private val conversation: ChatConversationStateHolder,
    private val sessions: ChatSessionCoordinator,
    private val postGeneration: ChatPostGenerationCoordinator,
    private val snapshot: () -> ChatGenerationSnapshot,
    private val onFailure: (Throwable) -> Unit = {},
    private val onFrontendEvent: (String, String) -> Unit = { _, _ -> },
    private val worldDriver: WorldChatDriver? = null,
) {
    private val delivery = ChatReplyScheduler<ChatGenerationRunner.Result>(
        scope = scope,
        textOf = { it.finalMessage.content },
        onFailure = onFailure,
    )
    val isRunning: Boolean
        get() = generationState.isRunning || delivery.busy

    val uiState: ChatGenerationState
        get() = ChatGenerationState(isRunning, generationState.targetTimestamp)

    suspend fun removeSessions(sessionIds: Set<String>, remove: suspend () -> Unit) =
        delivery.removeSessions(sessionIds, remove)

    fun stop() {
        worldDriver?.stop()
        delivery.stop()
        generationState.stop()
    }

    fun submitSend(
        sessionId: String,
        provider: ApiProvider,
        modelId: String,
        appendInput: suspend () -> Boolean,
    ) = enqueue(sessionId, provider, modelId, appendInput = appendInput)

    private fun enqueue(
        sessionId: String,
        provider: ApiProvider,
        modelId: String,
        appendInput: suspend () -> Boolean,
    ) {
        val context = snapshot()
        if (worldDriver?.submit(sessionId,context.card,provider,modelId,appendInput) == true) return
        delivery.submit(
            sessionId = sessionId,
            appendInput = appendInput,
            generate = { draft, onPartial ->
                runner.run(
                    request = ChatGenerationRunner.Request(
                        sessionId = sessionId,
                        provider = provider,
                        modelId = modelId,
                        card = context.card,
                        userName = context.userName,
                        worldBooks = context.worldBooks,
                        preset = context.preset,
                        promptRegex = context.promptRegex,
                        reasoning = context.reasoning,
                        imageGeneration = context.imageGeneration,
                        searchEnabled = context.searchEnabled,
                        deferDelivery = true,
                        interruptedDraft = draft,
                    ),
                    onStarted = { _, message ->
                        if (conversation.current?.id == sessionId) {
                            onFrontendEvent("generation_started", JSONObject().put("message_ts", message.ts).toString())
                        }
                    },
                    onLocalVariablesCommitted = {},
                    onUpdate = { message -> onPartial(message.content) },
                )
            },
            publish = { result, text, index, _ ->
                val message = runner.publish(result, text, index)
                if (conversation.current?.id == sessionId) {
                    conversation.putOverlay(message)
                    // The row is already committed. A UI refresh failure must not make it unsent again.
                    runCatching {
                        val metadata = runner.metadata(sessionId)
                        if (conversation.current?.id == sessionId) {
                            metadata?.let(conversation::replaceCurrent)
                            onFrontendEvent("message_received", JSONObject().put("message_ts", message.ts)
                                .put("message_id", conversation.current!!.messageTimestamps.indexOf(message.ts)).toString())
                        }
                    }.onFailure(onFailure)
                }
            },
            completed = {
                sessions.loadFull(sessionId)?.let { completed ->
                    runPostGeneration(completed)
                    if (conversation.current?.id == sessionId) {
                        onFrontendEvent("generation_ended", JSONObject().put("message_id", completed.messages.lastIndex).toString())
                    }
                }
            },
        )
    }

    private fun runPostGeneration(session: ChatSession) {
        val context = snapshot()
        postGeneration.runExtensions(
            session = session,
            apiConfig = context.apiConfig,
            userName = context.userName,
            characterName = context.card?.name ?: session.charName,
            isCurrent = { conversation.current?.id == session.id },
            onSessionRefreshed = { fresh ->
                conversation.updateCurrent(session.id) { it.copy(extState = fresh.extState) }
            },
        )
    }
}
