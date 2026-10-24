package me.rerere.momoly.ui.chat

import android.content.Context
import me.rerere.momoly.data.settings.FontSizeStore
import me.rerere.momoly.data.settings.Preferences
import me.rerere.momoly.data.settings.PreferencesStore

internal class AndroidChatUiSettingsDataSource(
    private val context: Context,
) : ChatUiSettingsDataSource {
    override fun preferences(): Preferences = PreferencesStore.get(context)

    override fun fontScale(): Float = FontSizeStore.getScale(context)
}
