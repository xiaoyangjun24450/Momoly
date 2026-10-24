package me.rerere.momoly.extension

import me.rerere.momoly.data.api.ApiConfig

interface ExtensionGateway {
    fun enabledExtensions(): List<Extension>
    fun config(id: String): String
    fun services(apiConfig: ApiConfig): ExtensionServices
}
