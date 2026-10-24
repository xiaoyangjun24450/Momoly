package me.rerere.momoly.di

import android.content.Context
import java.io.File
import me.rerere.momoly.core.resource.EditorDraftStorage
import me.rerere.momoly.data.FileEditorDraftStorage
import me.rerere.momoly.data.api.ApiConfigRepository
import me.rerere.momoly.data.api.PreferencesApiConfigRepository
import me.rerere.momoly.data.chat.RoomChatDataRepository
import me.rerere.momoly.data.generation.NetworkGenerationGateway
import me.rerere.momoly.domain.chat.ChatDataRepository
import me.rerere.momoly.domain.GenerationGateway
import me.rerere.momoly.extension.AndroidExtensionGateway
import me.rerere.momoly.extension.AndroidPluginHostCapabilities
import me.rerere.momoly.extension.ExtensionGateway

/** 应用级依赖装配点，避免 ViewModel 和业务对象自行获取全局存储或网络实现。 */
internal class AppContainer(
    context: Context,
    val apiConfigRepository: ApiConfigRepository = PreferencesApiConfigRepository(context),
) {
    val editorDrafts: EditorDraftStorage = FileEditorDraftStorage(File(context.noBackupFilesDir, "editor_drafts"))
    val chatRepository: ChatDataRepository = RoomChatDataRepository(context)
    val generationGateway: GenerationGateway = NetworkGenerationGateway(apiConfigRepository)
    val extensionGateway: ExtensionGateway = AndroidExtensionGateway(context, chatRepository)
    val pluginHostCapabilities = AndroidPluginHostCapabilities(chatRepository)
    val features = AppFeatureControllers(context, chatRepository, apiConfigRepository)
}
