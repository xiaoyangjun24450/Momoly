package me.rerere.momoly.data.settings

enum class DefaultModelRole(val storageKey: String) {
    CHAT("chat"),
    TRANSLATION("translation"),
}

object DefaultModelPrompts {
    const val TRANSLATION =
        "Translate the user's text into {language}. Preserve meaning, names, tone, paragraphs, and formatting. " +
            "Output only the translation without commentary."
}
