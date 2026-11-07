package me.rerere.momoly.ui.chat

import androidx.compose.runtime.Composable
import me.rerere.momoly.data.settings.ThemeMode
import me.rerere.momoly.ui.api.ApiConfigScreen
import me.rerere.momoly.ui.character.CharacterListScreen
import me.rerere.momoly.ui.settings.AboutScreen
import me.rerere.momoly.ui.settings.FontSizeScreen
import me.rerere.momoly.ui.settings.SettingsScreen
import me.rerere.momoly.ui.settings.TtsConfigScreen
import me.rerere.momoly.ui.settings.WebSearchConfigScreen

/** 渲染覆盖聊天页的全屏页面。 */
@Composable
internal fun ChatDestinationHost(
    destination: ChatDestination,
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onNavigate: (ChatDestination) -> Unit,
    onOpenSearchSession: (String, Long) -> Unit,
) {
    when (destination) {
        ChatDestination.Home -> error("Home is rendered by the root host")
        ChatDestination.Settings -> {
            SettingsScreen(
                onBack = onBack,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onNavigateToCharacters = { onNavigate(ChatDestination.Characters) },
                onNavigateToApiConfig = { onNavigate(ChatDestination.ApiConfig) },
                onNavigateToFontSize = { onNavigate(ChatDestination.FontSize) },
                onNavigateToTts = { onNavigate(ChatDestination.Tts) },
                onNavigateToAbout = { onNavigate(ChatDestination.About) },
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
        ChatDestination.Characters -> {
            CharacterListScreen(onBack = {
                onBack()
                onAction(ChatAction.RefreshCurrentCard)
            })
        }
        ChatDestination.ApiConfig -> {
            ApiConfigScreen(onBack = {
                onBack()
                onAction(ChatAction.ReloadApiConfig)
            })
        }
        ChatDestination.Tts -> { TtsConfigScreen(onBack = onBack) }
        ChatDestination.About -> { AboutScreen(onBack = onBack) }
        ChatDestination.Search -> {
            SearchScreen(
                charFile = state.conversation.current?.charFile.orEmpty(),
                onBack = onBack,
                onOpenSession = onOpenSearchSession,
            )
        }
        ChatDestination.WebSearch -> {
            WebSearchConfigScreen(onBack = {
                onBack()
                onAction(ChatAction.ReloadSearchConfig)
            })
        }
    }
}
