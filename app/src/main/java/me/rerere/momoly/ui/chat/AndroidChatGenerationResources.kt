package me.rerere.momoly.ui.chat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.momoly.R
import me.rerere.momoly.data.api.GeneratedImage
import me.rerere.momoly.data.chat.AttachmentStore
import me.rerere.momoly.data.chat.PersistedGeneratedImage
import me.rerere.momoly.data.settings.GlobalVariableStore

internal class AndroidChatGenerationResources(context: Context) : ChatGenerationResources {
    private val appContext = context.applicationContext

    override suspend fun persistGeneratedImage(image: GeneratedImage): PersistedGeneratedImage? =
        AttachmentStore.persistGeneratedImage(appContext, image)

    override suspend fun saveGlobalVariables(variables: Map<String, String>) {
        withContext(Dispatchers.IO) { GlobalVariableStore.set(appContext, variables) }
    }

    override fun errorText(error: Exception): String =
        appContext.getString(R.string.chat_error_fmt, error.message.orEmpty())
}
