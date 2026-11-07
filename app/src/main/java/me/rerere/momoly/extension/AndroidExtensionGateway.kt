package me.rerere.momoly.extension

import android.content.Context
import me.rerere.momoly.data.api.ApiConfig
import me.rerere.momoly.domain.chat.ChatDataRepository

internal class AndroidExtensionGateway(
    context: Context,
    private val chatRepository: ChatDataRepository,
) : ExtensionGateway {
    private val appContext = context.applicationContext

    override fun enabledExtensions(): List<Extension> = ExtensionStore.enabledExtensions(appContext)

    override fun config(id: String): String = ExtensionStore.getConfig(appContext, id)

    override fun services(apiConfig: ApiConfig): ExtensionServices = HostServices(
        config = apiConfig,
        chatRepository = chatRepository,
    )
}
