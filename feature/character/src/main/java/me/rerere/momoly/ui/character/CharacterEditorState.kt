package me.rerere.momoly.ui.character

import kotlinx.serialization.Serializable
import me.rerere.momoly.data.character.CharacterCard

@Serializable
data class CharacterEditorState(
    val card: CharacterCard,
    val depthInput: String = (card.depthPrompt?.depth ?: 4).toString(),
    val addingGreeting: Boolean = false,
    val greetingIndex: Int? = null,
    val greetingDraft: String = "",
)
