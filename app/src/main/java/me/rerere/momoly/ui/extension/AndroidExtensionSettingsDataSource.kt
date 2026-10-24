package me.rerere.momoly.ui.extension

import android.content.Context
import me.rerere.momoly.extension.ExtensionHost
import me.rerere.momoly.extension.ExtensionStore
import me.rerere.momoly.plugin.PluginManager

internal class AndroidExtensionSettingsDataSource(
    private val context: Context,
) : ExtensionSettingsDataSource {
    override fun isEnabled(id: String): Boolean = ExtensionStore.isEnabled(context, id)
    override fun setEnabled(id: String, enabled: Boolean) {
        if (ExtensionHost.byId(id)?.info?.builtin == false) {
            PluginManager.requestSetEnabled(id, enabled)
        } else {
            ExtensionStore.setEnabled(context, id, enabled)
        }
    }
    override fun config(id: String): String = ExtensionStore.getConfig(context, id)
    override fun setConfig(id: String, config: String) = ExtensionStore.setConfig(context, id, config)
}
