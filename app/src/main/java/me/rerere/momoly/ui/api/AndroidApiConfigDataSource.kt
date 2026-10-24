package me.rerere.momoly.ui.api

import android.content.Context
import me.rerere.momoly.data.api.ApiConfig
import me.rerere.momoly.data.api.ApiConfigStore

internal class AndroidApiConfigDataSource(
    private val context: Context,
) : ApiConfigDataSource {
    override fun load(): ApiConfig = ApiConfigStore.loadConfig(context)
    override fun save(config: ApiConfig) = ApiConfigStore.saveConfig(context, config)
    override fun consumeRecoveryNotice(): Boolean = ApiConfigStore.consumeCorruptionNotice(context)
}
