package me.rerere.momoly.ui.chat

import me.rerere.momoly.core.diagnostics.SafeLog
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.chat.ChatSession

internal enum class ChatSendOutcome { STARTED, NO_MODEL, SKIPPED, FILE_TOO_LARGE }

internal sealed interface ChatSendFailure {
    data object Attachment : ChatSendFailure
    data class Send(val error: Exception, val rollbackFailed: Boolean) : ChatSendFailure
}

/** 协调草稿校验、用户消息落盘、生成和失败回滚。 */
internal class ChatSendCoordinator(
    private val input: ChatInputStateHolder,
    private val conversation: ChatConversationStateHolder,
    private val promptContext: ChatPromptContextStateHolder,
    private val attachments: ChatAttachmentCoordinator,
    private val messageMutations: ChatMessageMutationCoordinator,
    private val sendMessage: SendChatMessageUseCase,
    private val generation: ChatGenerationOrchestrator,
    private val resolveModel: () -> Pair<ApiProvider, String>?,
    private val onFailure: (ChatSendFailure) -> Unit,
    private val canSend: () -> Boolean = { true },
) {
    fun send(): ChatSendOutcome {
        if (!canSend()) return ChatSendOutcome.SKIPPED
        if (!promptContext.isLoadedFor(conversation.current?.charFile.orEmpty())) {
            return ChatSendOutcome.SKIPPED
        }
        val text = input.text.trim()
        val pendingAttachments = input.attachments
        if (text.isBlank() && pendingAttachments.isEmpty()) return ChatSendOutcome.SKIPPED

        val editingTimestamp = input.editingTimestamp
        if (editingTimestamp != null) {
            if (text.isBlank() || generation.isRunning) return ChatSendOutcome.SKIPPED
            input.editingMessage?.let { messageMutations.updateMessage(it, text) }
            input.finishEditing()
            return ChatSendOutcome.STARTED
        }

        val (provider, modelId) = resolveModel() ?: return ChatSendOutcome.NO_MODEL
        if (attachments.hasOversizedFile(pendingAttachments)) {
            return ChatSendOutcome.FILE_TOO_LARGE
        }
        val source = conversation.current ?: ChatSession().also(conversation::replaceCurrent)
        input.clearDraft()
        generation.submitSend(source.id, provider, modelId) {
            when (val result = sendMessage(
                currentSession = { source },
                text = text,
                pendingAttachments = pendingAttachments,
                onPrepared = { prepared, userMessage ->
                    if (conversation.current?.id == prepared.id) {
                        conversation.replacePersistedCurrent(prepared)
                        conversation.putOverlay(userMessage)
                    }
                },
                generate = {},
            )) {
                SendChatMessageResult.Completed -> true
                SendChatMessageResult.AttachmentFailed -> {
                    input.restoreDraft(text, pendingAttachments)
                    onFailure(ChatSendFailure.Attachment)
                    false
                }
                is SendChatMessageResult.Failed -> {
                    result.restoredSession?.let { restored ->
                        if (conversation.current?.id == restored.id) conversation.replacePersistedCurrent(restored)
                    }
                    result.retainedTimestamps?.let(conversation::retainOverlays)
                    input.restoreDraft(text, pendingAttachments)
                    SafeLog.error(TAG, "send_message_failed", result.error)
                    onFailure(ChatSendFailure.Send(result.error, result.rollbackFailed))
                    false
                }
            }
        }
        return ChatSendOutcome.STARTED
    }

    private companion object {
        const val TAG = "ChatSendCoordinator"
    }
}
