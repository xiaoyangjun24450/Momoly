package me.rerere.momoly.ui.chat

import me.rerere.momoly.data.character.CharacterCard
import me.rerere.momoly.data.api.ApiProvider

/** Application-owned delivery for living-world contacts; survives leaving a chat page. */
interface WorldChatDriver {
    fun submit(sessionId: String, card: CharacterCard?, provider: ApiProvider, modelId: String,
               appendInput: suspend () -> Boolean): Boolean
    fun stop()
}
