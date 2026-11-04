package me.rerere.momoly.ui.chat

import android.content.Context
import java.io.File
import me.rerere.momoly.data.settings.GlobalVariableStore
import me.rerere.momoly.data.settings.WorldUserProfileStore
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

    override fun userDescription(world: String): String = WorldUserProfileStore.load(context, world).description

    override fun worldInfoSettings(): WorldInfoSettings = WorldInfoSettingsStore.get(context)

    override fun filesDir(): File = context.filesDir
    override fun livingWorldContext(characterFile: String): String {
        val world=me.rerere.momoly.data.settings.WorldLifeStore.read(context).worlds.values.firstOrNull { characterFile in it.actors } ?: return ""
        val actor=world.actors.getValue(characterFile)
        return "当前世界事实（不能用旧开场覆盖）：${me.rerere.momoly.data.world.LifeRules.situation(world,actor)}。心情${actor.mood}；好感${actor.affinity}。完整日程和数值不直接向用户公开。重新生成仅改写表达，不改变已经发生的事实。"
    }
}
