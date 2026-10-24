package me.rerere.momoly.ui.chat

import android.content.Context
import java.io.File
import me.rerere.momoly.data.settings.GlobalVariableStore
import me.rerere.momoly.data.settings.UserProfileStore
import me.rerere.momoly.data.settings.WorldInfoSettingsStore
import me.rerere.momoly.data.worldbook.WorldInfoSettings
import me.rerere.momoly.extension.Extension
import me.rerere.momoly.extension.ExtensionGateway

internal class AndroidChatPromptEnvironment(
    private val context: Context,
    private val extensions: ExtensionGateway,
) : ChatPromptEnvironment {
    override fun enabledExtensions(): List<Extension> = extensions.enabledExtensions()

    override fun extensionConfig(id: String): String = extensions.config(id)

    override fun globalVariables(): Map<String, String> = GlobalVariableStore.get(context)

    override fun observeGlobalVariables() = GlobalVariableStore.observe(context)

    override fun userDescription(): String = UserProfileStore.getDescription(context)

    override fun worldInfoSettings(): WorldInfoSettings = WorldInfoSettingsStore.get(context)

    override fun filesDir(): File = context.filesDir
}
