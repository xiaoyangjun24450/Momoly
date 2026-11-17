package me.rerere.momoly.ui.api

import me.rerere.momoly.data.api.ApiConfig
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ModelType
import me.rerere.momoly.data.api.withValidCurrentModel

data class ApiConfigLoadResult(
    val config: ApiConfig,
    val recovered: Boolean,
)

interface ApiConfigDataSource {
    fun load(): ApiConfig
    fun save(config: ApiConfig)
    fun consumeRecoveryNotice(): Boolean
}

class ApiConfigController(
    private val dataSource: ApiConfigDataSource,
) {
    fun selectConnectedModel(provider: ApiProvider, modelId: String): ApiConfig {
        require(provider.type == "openai" && provider.enabled &&
            provider.models.any { it.id == modelId && it.type == ModelType.CHAT })
        val config = dataSource.load()
        // 列表只是候选项，不能全部保存为可调用模型，否则失败重试会轮流调用未选项。
        val selected = provider.copy(models = listOf(provider.models.first { it.id == modelId }))
        val previous = config.providers.map {
            when {
                it.id == provider.id -> selected
                it.models.any { model -> model.type == ModelType.CHAT } -> it.copy(enabled = false)
                else -> it
            }
        }
        val providers = if (previous.any { it.id == provider.id }) previous else previous + selected
        return save(config.copy(providers = providers, currentModel = "${provider.id}::$modelId"))
    }

    fun load(): ApiConfigLoadResult = ApiConfigLoadResult(
        config = dataSource.load().withValidCurrentModel(),
        recovered = dataSource.consumeRecoveryNotice(),
    )

    fun save(config: ApiConfig): ApiConfig {
        val normalized = config.withValidCurrentModel()
        dataSource.save(normalized)
        return normalized
    }
}
