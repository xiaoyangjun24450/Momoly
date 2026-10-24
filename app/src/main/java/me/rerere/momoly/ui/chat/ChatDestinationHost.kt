package me.rerere.momoly.ui.chat

import androidx.compose.runtime.Composable
import me.rerere.momoly.data.settings.ThemeMode
import me.rerere.momoly.domain.GenerationActionGuard
import me.rerere.momoly.ui.api.ApiConfigScreen
import me.rerere.momoly.ui.character.CharacterListScreen
import me.rerere.momoly.ui.extension.ExtensionsScreen
import me.rerere.momoly.ui.preset.PresetListScreen
import me.rerere.momoly.ui.regex.RegexListScreen
import me.rerere.momoly.ui.settings.AboutScreen
import me.rerere.momoly.ui.settings.DataManagementScreen
import me.rerere.momoly.ui.settings.DefaultModelPage
import me.rerere.momoly.ui.settings.FontSizeScreen
import me.rerere.momoly.ui.settings.LogsScreen
import me.rerere.momoly.ui.settings.PreferencesScreen
import me.rerere.momoly.ui.settings.PromptLogScreen
import me.rerere.momoly.ui.settings.CrashReportScreen
import me.rerere.momoly.ui.settings.SettingsScreen
import me.rerere.momoly.ui.settings.SystemLogScreen
import me.rerere.momoly.ui.settings.TtsConfigScreen
import me.rerere.momoly.ui.settings.WebSearchConfigScreen
import me.rerere.momoly.ui.statistics.StatisticsScreen
import me.rerere.momoly.ui.translator.TranslatorScreen
import me.rerere.momoly.ui.worldbook.WorldBookListScreen

/** 渲染覆盖聊天页的全屏页面。 */
@Composable
internal fun ChatDestinationHost(
    destination: ChatDestination,
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    solidBackground: Boolean,
    onSolidBackgroundChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNavigate: (ChatDestination) -> Unit,
    onOpenSearchSession: (String) -> Unit,
) {
    when (destination) {
        ChatDestination.Translator -> {
            TranslatorScreen(
                onBack = onBack,
                apiConfig = state.model.apiConfig,
                fallbackModelSpec = state.model.displaySpec.orEmpty(),
            )
        }
        ChatDestination.Statistics -> { StatisticsScreen(onBack = onBack) }
        ChatDestination.Search -> {
            SearchScreen(
                charFile = state.conversation.current?.charFile.orEmpty(),
                onBack = onBack,
                onOpenSession = onOpenSearchSession,
            )
        }
        ChatDestination.FontSize -> {
            FontSizeScreen(
                onBack = {
                    onBack()
                    onAction(ChatAction.ReloadUiSettings)
                },
                currentScale = state.settings.fontScale,
            )
        }
        ChatDestination.Preferences -> {
            PreferencesScreen(
                onBack = {
                    onBack()
                    onAction(ChatAction.ReloadUiSettings)
                },
                solidBackground = solidBackground,
                onSolidBackgroundChange = onSolidBackgroundChange,
            )
        }
        ChatDestination.Logs -> {
            LogsScreen(
                onBack = onBack,
                onOpenSystemLog = { onNavigate(ChatDestination.SystemLog) },
                onOpenPromptLog = { onNavigate(ChatDestination.PromptLog) },
            )
        }
        ChatDestination.SystemLog -> { SystemLogScreen(onBack = onBack) }
        ChatDestination.PromptLog -> { PromptLogScreen(onBack = onBack) }
        ChatDestination.CrashReport -> { CrashReportScreen(onBack = onBack) }
        ChatDestination.DataMgmt -> {
            DataManagementScreen(
                onBack = {
                    onBack()
                    onAction(ChatAction.RefreshAfterDataManagement)
                },
                destructiveActionsEnabled = GenerationActionGuard.allowsMutation(state.generation.running),
            )
        }
        ChatDestination.ApiConfig -> {
            ApiConfigScreen(onBack = {
                onBack()
                onAction(ChatAction.ReloadApiConfig)
            })
        }
        ChatDestination.WorldBooks -> {
            WorldBookListScreen(onBack = {
                onBack()
                onAction(ChatAction.ReloadPromptData)
            })
        }
        ChatDestination.Characters -> {
            CharacterListScreen(onBack = {
                onBack()
                onAction(ChatAction.RefreshCurrentCard)
            })
        }
        ChatDestination.Presets -> {
            PresetListScreen(onBack = {
                onBack()
                onAction(ChatAction.ReloadPromptData)
            })
        }
        ChatDestination.Settings -> {
            SettingsScreen(
                onBack = onBack,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onNavigateToPresets = { onNavigate(ChatDestination.Presets) },
                onNavigateToCharacters = { onNavigate(ChatDestination.Characters) },
                onNavigateToWorldBooks = { onNavigate(ChatDestination.WorldBooks) },
                onNavigateToApiConfig = { onNavigate(ChatDestination.ApiConfig) },
                onNavigateToDataManagement = { onNavigate(ChatDestination.DataMgmt) },
                onNavigateToFontSize = { onNavigate(ChatDestination.FontSize) },
                onNavigateToPreferences = { onNavigate(ChatDestination.Preferences) },
                onNavigateToLogs = { onNavigate(ChatDestination.Logs) },
                onNavigateToCrashReport = { onNavigate(ChatDestination.CrashReport) },
                onNavigateToExtensions = { onNavigate(ChatDestination.Extensions) },
                onNavigateToDefaultModel = { onNavigate(ChatDestination.DefaultModel) },
                onNavigateToWebSearch = { onNavigate(ChatDestination.WebSearch) },
                onNavigateToTts = { onNavigate(ChatDestination.Tts) },
                onNavigateToRegex = { onNavigate(ChatDestination.Regex) },
                onNavigateToAbout = { onNavigate(ChatDestination.About) },
            )
        }
        ChatDestination.Extensions -> {
            ExtensionsScreen(onBack = {
                onBack()
                onAction(ChatAction.RefreshExtensionSlots)
            })
        }
        ChatDestination.About -> { AboutScreen(onBack = onBack) }
        ChatDestination.DefaultModel -> { DefaultModelPage(onBack = onBack) }
        ChatDestination.WebSearch -> {
            WebSearchConfigScreen(onBack = {
                onBack()
                onAction(ChatAction.ReloadSearchConfig)
            })
        }
        ChatDestination.Tts -> { TtsConfigScreen(onBack = onBack) }
        ChatDestination.Regex -> {
            RegexListScreen(onBack = {
                onBack()
                onAction(ChatAction.ReloadPromptData)
            })
        }
    }
}
