package me.rerere.momoly.ui.chat

import me.rerere.momoly.data.api.ApiConfig
import me.rerere.momoly.data.api.ImageGenerationSettings
import me.rerere.momoly.data.api.ReasoningLevel

data class ChatUiState(
    val conversation: ChatConversationState,
    val input: ChatInputState,
    val generation: ChatGenerationState,
    val profile: ChatProfileState,
    val model: ModelState,
    val search: ChatSearchState,
    val settings: ChatUiSettings,
    val globalVariables: Map<String, String> = emptyMap(),
) {
    data class ModelState(
        val apiConfig: ApiConfig,
        val revision: Int,
        val displaySpec: String?,
        val reasoning: ReasoningLevel,
        val imageGeneration: ImageGenerationSettings,
        val imageGenerationAvailable: Boolean,
    )
}
