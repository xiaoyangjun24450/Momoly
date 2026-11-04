package me.rerere.momoly.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import me.rerere.momoly.MomolyApplication
import me.rerere.momoly.data.settings.ThemeMode

/** Route-level entry point. ChatContent owns rendering; ChatViewModel owns screen state. */
@Composable
fun ChatScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    startAtSettings: Boolean = false,
) {
    val application = LocalContext.current.applicationContext as MomolyApplication
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_START) application.container.worldLife.foreground(true)
            if(event==Lifecycle.Event.ON_STOP) application.container.worldLife.foreground(false)
        }
        owner.lifecycle.addObserver(observer)
        application.container.worldLife.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); application.container.worldLife.foreground(false) }
    }
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
        onClearContactHistory = viewModel::clearContactHistory,
        frontendRpc = viewModel::frontendRpc,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        startAtSettings = startAtSettings,
    )
}
