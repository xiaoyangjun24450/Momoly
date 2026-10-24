package me.rerere.momoly.ui.settings

interface TtsPreview {
    fun speak(text: String, onComplete: () -> Unit)
    fun release()
}
