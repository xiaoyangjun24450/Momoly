package me.rerere.momoly.ui.chat

import me.rerere.momoly.extension.ExtensionGateway
import me.rerere.momoly.extension.QuickReplyProvider

/** 将已启用扩展的快捷回复投影到输入状态。 */
internal class ChatQuickReplyCoordinator(
    private val extensions: ExtensionGateway,
    private val input: ChatInputStateHolder,
) {
    fun refresh() {
        val replies = extensions.enabledExtensions().flatMap { extension ->
            if (extension is QuickReplyProvider) {
                extension.quickReplies(extensions.config(extension.info.id))
            } else {
                emptyList()
            }
        }
        input.replaceQuickReplies(replies)
    }
}
