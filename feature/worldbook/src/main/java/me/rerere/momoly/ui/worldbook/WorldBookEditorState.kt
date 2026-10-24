package me.rerere.momoly.ui.worldbook

import kotlinx.serialization.Serializable
import me.rerere.momoly.data.worldbook.WorldBook
import me.rerere.momoly.data.worldbook.WorldBookEntry

@Serializable
data class WorldBookEditorState(
    val book: WorldBook,
    val editingEntry: WorldBookEntry? = null,
)
