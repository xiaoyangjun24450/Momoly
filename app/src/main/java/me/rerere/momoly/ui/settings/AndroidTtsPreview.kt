package me.rerere.momoly.ui.settings

import android.content.Context
import me.rerere.momoly.data.speech.TTSProviderSetting
import me.rerere.momoly.data.speech.TtsEngine

internal class AndroidTtsPreview(
    context: Context,
    setting: () -> TTSProviderSetting,
) : TtsPreview {
    private val engine = TtsEngine(context, setting)

    override fun speak(text: String, onComplete: () -> Unit) = engine.speak(text, onComplete)
    override fun release() = engine.release()
}
