package me.rerere.momoly.ui.chat

import android.content.Context
import me.rerere.momoly.data.settings.DefaultModelStore
import me.rerere.momoly.data.settings.DefaultModelPrompts
import me.rerere.momoly.data.settings.DefaultModelRole

internal class AndroidChatTitleSettingsDataSource(
    private val context: Context,
) : ChatTitleSettingsDataSource {
    override fun resolveModel(chatModel: String): Pair<String, String>? =
        DefaultModelStore.resolveModel(context, DefaultModelRole.TITLE.storageKey, chatModel)

    override fun promptTemplate(): String {
        val entry = DefaultModelStore.get(context, DefaultModelRole.TITLE.storageKey)
        return entry.prompt.ifBlank { DefaultModelPrompts.TITLE }
    }
}
