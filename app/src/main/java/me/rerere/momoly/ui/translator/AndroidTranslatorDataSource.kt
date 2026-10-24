package me.rerere.momoly.ui.translator

import android.content.Context
import me.rerere.momoly.data.api.ApiMessage
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ChatApi
import me.rerere.momoly.data.settings.DefaultModelRole
import me.rerere.momoly.data.settings.DefaultModelStore

internal class AndroidTranslatorDataSource(
    private val context: Context,
) : TranslatorDataSource {
    override fun defaults(): TranslationDefaults =
        DefaultModelStore.get(context, DefaultModelRole.TRANSLATION.storageKey).let {
            TranslationDefaults(modelSpec = it.model, prompt = it.prompt)
        }

    override fun saveModel(modelSpec: String) {
        DefaultModelStore.setModel(context, DefaultModelRole.TRANSLATION.storageKey, modelSpec)
    }

    override suspend fun stream(
        provider: ApiProvider,
        modelId: String,
        messages: List<ApiMessage>,
        isCancelled: () -> Boolean,
        onDelta: (String) -> Unit,
    ) {
        try {
            ChatApi.streamChat(
                provider = provider,
                modelId = modelId,
                messages = messages,
                params = null,
                isCancelled = isCancelled,
                onDelta = { content, _ -> onDelta(content) },
            )
        } catch (_: ChatApi.Stopped) {
        }
    }
}
