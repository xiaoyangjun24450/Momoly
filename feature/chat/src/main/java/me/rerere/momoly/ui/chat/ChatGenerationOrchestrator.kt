package me.rerere.momoly.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.momoly.data.api.ApiConfig
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ImageGenerationSettings
import me.rerere.momoly.data.api.ReasoningLevel
import me.rerere.momoly.data.character.CharRegex
import me.rerere.momoly.data.character.CharacterCard
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.data.preset.StPreset
import me.rerere.momoly.data.worldbook.WorldBook
import me.rerere.momoly.domain.ChatGenerationMode
import me.rerere.momoly.domain.ChatRegenerationPlan
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
    val chatModel: String?,
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
) {
    private var deliveryTargetTimestamp by mutableStateOf<Long?>(null)
    private val delivery = ChatReplyScheduler<ChatGenerationRunner.Result>(
        scope = scope,
        textOf = { it.finalMessage.content },
        onFailure = onFailure,
    )
    val isRunning: Boolean
        get() = generationState.isRunning || delivery.busy

    val uiState: ChatGenerationState
        get() = ChatGenerationState(isRunning, deliveryTargetTimestamp ?: generationState.targetTimestamp)

    suspend fun removeSessions(sessionIds: Set<String>, remove: suspend () -> Unit) =
        delivery.removeSessions(sessionIds, remove)

    fun stop() {
        delivery.stop()
        generationState.stop()
    }

    fun submitSend(
        sessionId: String,
        provider: ApiProvider,
        modelId: String,
        appendInput: suspend () -> Boolean,
    ) = enqueue(sessionId, provider, modelId, appendInput = appendInput)

    fun launchRegeneration(
        sessionId: String,
        provider: ApiProvider,
        modelId: String,
        createPlan: (ChatSession) -> ChatRegenerationPlan?,
    ): Boolean {
        if (isRunning) return false
        var plan: ChatRegenerationPlan? = null
        enqueue(
            sessionId, provider, modelId,
            appendInput = {
                plan = sessions.loadFull(sessionId)?.let(createPlan)
                deliveryTargetTimestamp = (plan as? ChatRegenerationPlan.Regenerate)?.targetTimestamp
                (plan as? ChatRegenerationPlan.TruncateAndSend)?.let {
                    sessions.truncateAfter(sessionId, it.afterTimestamp)
                }
                plan != null
            },
            regeneration = { plan as? ChatRegenerationPlan.Regenerate },
            waitForInput = false,
        )
        return true
    }

    private fun enqueue(
        sessionId: String,
        provider: ApiProvider,
        modelId: String,
        appendInput: suspend () -> Boolean,
        regeneration: () -> ChatRegenerationPlan.Regenerate? = { null },
        waitForInput: Boolean = true,
    ) {
        val context = snapshot()
        deliveryTargetTimestamp = null
        delivery.submit(
            sessionId = sessionId,
            appendInput = appendInput,
            waitForInput = waitForInput,
            // Regeneration replaces one selected message/version in place.
            splitReply = { it.finalMessage.alts.isEmpty() },
            generate = { draft, onPartial ->
                val regenerate = regeneration()
                runner.run(
                    request = ChatGenerationRunner.Request(
                        sessionId = sessionId,
                        provider = provider,
                        modelId = modelId,
                        mode = if (regenerate == null) ChatGenerationMode.SEND else ChatGenerationMode.REGENERATE,
                        targetTimestamp = regenerate?.targetTimestamp,
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

    fun generateTitle(session: ChatSession, force: Boolean) {
        val context = snapshot()
        val chatModel = context.chatModel ?: return
        postGeneration.generateTitle(
            session = session,
            force = force,
            chatModel = chatModel,
            apiConfig = context.apiConfig,
            userName = context.userName,
            characterName = context.card?.name ?: session.charName,
            onTitle = { title ->
                conversation.updateCurrent(session.id) { it.copy(title = title) }
            },
        )
    }

    fun generateTitle(sessionId: String) {
        scope.launch {
            sessions.loadFull(sessionId)?.let { generateTitle(it, force = true) }
        }
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
        generateTitle(session, force = false)
    }
}
