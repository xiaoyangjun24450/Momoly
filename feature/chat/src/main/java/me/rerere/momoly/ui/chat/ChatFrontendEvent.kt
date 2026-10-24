package me.rerere.momoly.ui.chat

data class ChatFrontendEvent(
    val sequence: Long,
    val type: String,
    val payloadJson: String = "{}",
)
