package me.rerere.momoly.di

import android.content.Context
import me.rerere.momoly.data.api.ApiConfigRepository
import me.rerere.momoly.ui.api.AndroidApiConfigDataSource
import me.rerere.momoly.ui.api.ApiConfigController
import me.rerere.momoly.ui.character.AndroidCharacterEditorDataSource
import me.rerere.momoly.ui.character.AndroidCharacterLibraryDataSource
import me.rerere.momoly.ui.character.CharacterEditorController
import me.rerere.momoly.ui.character.CharacterLibraryController
import me.rerere.momoly.domain.chat.ChatDataRepository
import me.rerere.momoly.ui.chat.AndroidChatSearchDataSource
import me.rerere.momoly.ui.chat.AndroidChatUserProfileDataSource
import me.rerere.momoly.ui.chat.ChatSearchController
import me.rerere.momoly.ui.chat.ChatUserProfileController
import me.rerere.momoly.ui.extension.AndroidExtensionSettingsDataSource
import me.rerere.momoly.ui.extension.ExtensionSettingsController
import me.rerere.momoly.ui.preset.AndroidPresetDataSource
import me.rerere.momoly.ui.preset.PresetDataController
import me.rerere.momoly.ui.regex.AndroidRegexLibraryDataSource
import me.rerere.momoly.ui.regex.RegexLibraryController
import me.rerere.momoly.ui.settings.AndroidDataManagementDataSource
import me.rerere.momoly.ui.settings.AndroidDefaultModelDataSource
import me.rerere.momoly.ui.settings.AndroidDiagnosticsDataSource
import me.rerere.momoly.ui.settings.AndroidSettingsDataSource
import me.rerere.momoly.ui.settings.AndroidTtsConfigDataSource
import me.rerere.momoly.ui.settings.AndroidWebSearchConfigDataSource
import me.rerere.momoly.ui.settings.DataManagementController
import me.rerere.momoly.ui.settings.DefaultModelController
import me.rerere.momoly.ui.settings.DiagnosticsController
import me.rerere.momoly.ui.settings.SettingsDataController
import me.rerere.momoly.ui.settings.TtsConfigController
import me.rerere.momoly.ui.settings.WebSearchConfigController
import me.rerere.momoly.ui.statistics.AndroidStatisticsDataSource
import me.rerere.momoly.ui.statistics.StatisticsController
import me.rerere.momoly.ui.translator.AndroidTranslatorDataSource
import me.rerere.momoly.ui.translator.TranslatorController
import me.rerere.momoly.ui.worldbook.AndroidWorldBookDataSource
import me.rerere.momoly.ui.worldbook.AndroidWorldInfoSettingsDataSource
import me.rerere.momoly.ui.worldbook.WorldBookDataController
import me.rerere.momoly.ui.worldbook.WorldInfoSettingsController

/** 跨页面复用的类型化 Feature 入口；具体 Store/Repository 只在 Android 适配器中出现。 */
internal class AppFeatureControllers(
    context: Context,
    chatRepository: ChatDataRepository,
    apiConfigRepository: ApiConfigRepository,
) {
    private val appContext = context.applicationContext

    val apiConfig = ApiConfigController(AndroidApiConfigDataSource(appContext))
    val characterLibrary = CharacterLibraryController(AndroidCharacterLibraryDataSource(appContext))
    val characterEditor = CharacterEditorController(AndroidCharacterEditorDataSource(appContext, apiConfigRepository))
    val chatSearch = ChatSearchController(AndroidChatSearchDataSource(appContext, chatRepository))
    val chatUserProfile = ChatUserProfileController(AndroidChatUserProfileDataSource(appContext))
    val extensions = ExtensionSettingsController(AndroidExtensionSettingsDataSource(appContext))
    val presets = PresetDataController(AndroidPresetDataSource(appContext))
    val regex = RegexLibraryController(AndroidRegexLibraryDataSource(appContext))
    val settings = SettingsDataController(AndroidSettingsDataSource(appContext))
    val dataManagement = DataManagementController(AndroidDataManagementDataSource(appContext))
    val defaultModels = DefaultModelController(AndroidDefaultModelDataSource(appContext))
    val diagnostics = DiagnosticsController(AndroidDiagnosticsDataSource(appContext))
    val statistics = StatisticsController(AndroidStatisticsDataSource(appContext))
    val translator = TranslatorController(AndroidTranslatorDataSource(appContext))
    val tts = TtsConfigController(AndroidTtsConfigDataSource(appContext))
    val webSearch = WebSearchConfigController(AndroidWebSearchConfigDataSource(appContext))
    val worldBooks = WorldBookDataController(AndroidWorldBookDataSource(appContext))
    val worldInfoSettings = WorldInfoSettingsController(AndroidWorldInfoSettingsDataSource(appContext))
}
