package me.rerere.momoly.ui.components

import android.content.Context
import me.rerere.momoly.data.settings.PreferencesStore

internal class AndroidHapticSettingsDataSource(
    private val context: Context,
) : HapticSettingsDataSource {
    override fun longPressEnabled(): Boolean = PreferencesStore.get(context).longPressHaptic
}
