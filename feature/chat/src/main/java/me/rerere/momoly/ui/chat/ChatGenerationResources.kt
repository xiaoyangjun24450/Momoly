package me.rerere.momoly.ui.chat

import me.rerere.momoly.data.api.GeneratedImage
import me.rerere.momoly.data.chat.PersistedGeneratedImage

interface ChatGenerationResources {
    suspend fun persistGeneratedImage(image: GeneratedImage): PersistedGeneratedImage?
    suspend fun saveGlobalVariables(variables: Map<String, String>)
    fun errorText(error: Exception): String
}
