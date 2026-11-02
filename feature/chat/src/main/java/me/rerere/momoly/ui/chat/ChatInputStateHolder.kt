package me.rerere.momoly.ui.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.momoly.extension.QuickReply
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.MessageQuote

data class ChatInputState(
    val attachments: List<Attachment>,
    val editingTimestamp: Long?,
    val quickReplies: List<QuickReply>,
    val replyTo: MessageQuote? = null,
)

/** 持有需要跨重组和 Activity 重建保留的输入状态。 */
class ChatInputStateHolder {
    val textFieldState = TextFieldState()

    var attachments by mutableStateOf<List<Attachment>>(emptyList())
        private set

    var editingTimestamp by mutableStateOf<Long?>(null)
        private set

    var editingMessage: ChatMessage? = null
        private set

    var replyTo by mutableStateOf<MessageQuote?>(null)
        private set

    fun setReply(quote: MessageQuote?) { replyTo = quote }

    var quickReplies by mutableStateOf<List<QuickReply>>(emptyList())
        private set

    var text: String
        get() = textFieldState.text.toString()
        set(value) = textFieldState.setTextAndPlaceCursorAtEnd(value)

    val state: ChatInputState
        get() = ChatInputState(
            attachments = attachments,
            editingTimestamp = editingTimestamp,
            quickReplies = quickReplies,
            replyTo = replyTo,
        )

    fun addAttachments(values: List<Attachment>) {
        attachments = attachments + values
    }

    fun removeAttachment(value: Attachment) {
        attachments = attachments - value
    }

    fun clearDraft() {
        replyTo = null
        text = ""
        attachments = emptyList()
    }

    fun restoreDraft(draftText: String, sentAttachments: List<Attachment>, quote: MessageQuote? = null) {
        if (replyTo == null) replyTo = quote
        if (draftText.isNotBlank()) {
            text = if (text.isBlank()) draftText else "$draftText\n$text"
        }
        val currentUris = attachments.mapTo(HashSet()) { it.uri }
        attachments = sentAttachments.filter { it.uri !in currentUris } + attachments
    }

    fun beginEditing(message: ChatMessage) {
        replyTo = null
        editingMessage = message
        editingTimestamp = message.ts
        text = message.content
    }

    fun beginEditing(timestamp: Long, content: String) =
        beginEditing(ChatMessage(role = "user", content = content, ts = timestamp))

    fun finishEditing() {
        editingMessage = null
        editingTimestamp = null
        text = ""
    }

    fun cancelEditing() = finishEditing()

    fun replaceQuickReplies(values: List<QuickReply>) {
        quickReplies = values
    }
}
