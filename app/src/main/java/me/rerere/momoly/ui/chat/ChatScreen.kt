package me.rerere.momoly.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import me.rerere.momoly.MomolyApplication
import me.rerere.momoly.data.settings.ThemeMode

/** Route-level entry point. ChatContent owns rendering; ChatViewModel owns screen state. */
@Composable
fun ChatScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    solidBackground: Boolean = false,
    onSolidBackgroundChange: (Boolean) -> Unit = {},
    startAtSettings: Boolean = false,
) {
    val application = LocalContext.current.applicationContext as MomolyApplication
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(application, application.container),
    )
    ChatContent(
        state = viewModel.uiState,
        inputState = viewModel.inputState,
        pagedMessages = viewModel.pagedMessages,
        effects = viewModel.effects,
        frontendEvents = viewModel.frontendEvents,
        onAction = viewModel::dispatch,
        frontendRpc = viewModel::frontendRpc,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        solidBackground = solidBackground,
        onSolidBackgroundChange = onSolidBackgroundChange,
        startAtSettings = startAtSettings,
    )
}
