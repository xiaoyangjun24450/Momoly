package me.rerere.momoly.ui.api

import me.rerere.momoly.data.api.ApiConfig
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
