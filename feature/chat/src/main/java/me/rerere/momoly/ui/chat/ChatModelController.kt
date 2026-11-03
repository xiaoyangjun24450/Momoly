package me.rerere.momoly.ui.chat

import me.rerere.momoly.data.api.ApiConfig
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ImageGenerationSettings
import me.rerere.momoly.data.api.ReasoningLevel
import me.rerere.momoly.data.api.ModelType

interface ChatModelDataSource {
    fun characterModel(characterName: String): String
    fun saveCharacterModel(characterName: String, modelSpec: String)
    fun defaultChatModel(): String
    fun saveDefaultChatModel(modelSpec: String)
    fun reasoning(modelSpec: String): ReasoningLevel
    fun saveReasoning(modelSpec: String, level: ReasoningLevel)
    fun imageGeneration(modelSpec: String): ImageGenerationSettings
    fun saveImageGeneration(modelSpec: String, settings: ImageGenerationSettings)
}

class ChatModelController(
    private val dataSource: ChatModelDataSource,
) {
    fun effectiveModelSpec(characterName: String?, apiConfig: ApiConfig): String? {
        val characterModel = characterName
            ?.takeIf { it.isNotBlank() }
            ?.let(dataSource::characterModel)
            ?.takeIf { it.isNotBlank() }
        val defaultModel = dataSource.defaultChatModel().takeIf { it.isNotBlank() }
        val preferred = listOfNotNull(characterModel, defaultModel, apiConfig.currentModel.takeIf { it.isNotBlank() })
        val enabledChatModels = apiConfig.providers.filter { it.enabled && it.type != "gradio" }
            .flatMap { provider ->
                provider.models.filter { it.type == ModelType.CHAT && it.id.isNotBlank() }
                    .map { "${provider.id}::${it.id}" }
            }
        return preferred.firstOrNull { it in enabledChatModels } ?: enabledChatModels.firstOrNull()
    }

    fun select(characterName: String?, modelSpec: String): ReasoningLevel {
        if (characterName.isNullOrBlank()) dataSource.saveDefaultChatModel(modelSpec)
        else dataSource.saveCharacterModel(characterName, modelSpec)
        return dataSource.reasoning(modelSpec)
    }

    fun reasoning(modelSpec: String): ReasoningLevel = dataSource.reasoning(modelSpec)

    fun saveReasoning(modelSpec: String, level: ReasoningLevel) {
        dataSource.saveReasoning(modelSpec, level)
    }

    fun imageGeneration(modelSpec: String): ImageGenerationSettings = dataSource.imageGeneration(modelSpec)

    fun saveImageGeneration(modelSpec: String, settings: ImageGenerationSettings) {
        dataSource.saveImageGeneration(modelSpec, settings)
    }

    fun resolveProvider(
        characterName: String?,
        apiConfig: ApiConfig,
    ): Pair<ApiProvider, String>? = providerFor(effectiveModelSpec(characterName, apiConfig), apiConfig)

    fun providerFor(modelSpec: String?, apiConfig: ApiConfig): Pair<ApiProvider, String>? {
        val spec = modelSpec ?: return null
        val providerId = spec.substringBefore("::")
        val modelId = spec.substringAfter("::", "")
        val provider = apiConfig.providers.find { it.id == providerId && it.enabled }
        return if (provider?.model(modelId) != null) provider to modelId else null
    }

}
