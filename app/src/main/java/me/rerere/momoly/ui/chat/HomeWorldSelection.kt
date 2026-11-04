package me.rerere.momoly.ui.chat

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal class HomeWorldSelection(context: Context) {
    val preferences = context.getSharedPreferences("home_world", Context.MODE_PRIVATE)
    var name by mutableStateOf(preferences.getString("selected_name", null))
        private set

    fun reload() { name = preferences.getString("selected_name", null) }

    fun select(world: String) {
        me.rerere.momoly.data.settings.WorldLifeStore.select(appContext,world)
        name = world
        preferences.edit().putString("selected_name", world).apply()
    }
    private val appContext = context.applicationContext
}

@Composable
internal fun rememberHomeWorldSelection(): HomeWorldSelection {
    val context = LocalContext.current.applicationContext
    val selection = remember(context) { HomeWorldSelection(context) }
    DisposableEffect(selection) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> selection.reload() }
        selection.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { selection.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return selection
}

internal fun resolveHomeWorld(selected: String?, worlds: List<String>): String? =
    selected?.takeIf { it in worlds } ?: worlds.firstOrNull { it.isNotBlank() } ?: worlds.firstOrNull()

internal fun needsWorldConfirmation(current: String?, incoming: String): Boolean =
    !current.isNullOrBlank() && current != incoming
