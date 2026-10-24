package me.rerere.momoly.ui.components

fun interface HapticSettingsDataSource {
    fun longPressEnabled(): Boolean
}

class HapticSettingsController(
    private val dataSource: HapticSettingsDataSource,
) {
    fun longPressEnabled(): Boolean = dataSource.longPressEnabled()
}
