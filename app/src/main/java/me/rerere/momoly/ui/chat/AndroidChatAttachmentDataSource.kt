package me.rerere.momoly.ui.chat

import android.content.Context
import android.net.Uri
import me.rerere.momoly.data.chat.AttachmentStore
import me.rerere.momoly.data.chat.MsgFile
import me.rerere.momoly.domain.chat.ChatDataRepository

internal class AndroidChatAttachmentDataSource(
    private val context: Context,
    private val chatRepository: ChatDataRepository,
) : ChatAttachmentDataSource {
    override fun isTooLarge(uri: Uri): Boolean = AttachmentStore.isTooLarge(context, uri)

    override suspend fun persistImage(uri: Uri): String? =
        AttachmentStore.persistImage(context, uri)

    override suspend fun persistFile(uri: Uri): MsgFile? =
        AttachmentStore.persistFile(context, uri)

    override suspend fun collectUnused() = chatRepository.collectUnusedAttachments()
}
