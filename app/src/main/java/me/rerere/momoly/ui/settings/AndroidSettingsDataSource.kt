package me.rerere.momoly.ui.settings

import android.content.Context
import me.rerere.momoly.data.api.ApiConfigStore
import me.rerere.momoly.data.settings.DefaultModelRole
import me.rerere.momoly.data.settings.DefaultModelStore
import me.rerere.momoly.data.settings.FontSizeStore
import me.rerere.momoly.data.settings.LanguageStore
import me.rerere.momoly.data.settings.Preferences
import me.rerere.momoly.data.settings.PreferencesStore
import me.rerere.momoly.data.settings.PromptLogStore
import me.rerere.momoly.data.settings.UserAvatarStore
import me.rerere.momoly.data.settings.UserProfileStore

internal class AndroidSettingsDataSource(
    private val context: Context,
) : SettingsDataSource {
    override fun language(): String = LanguageStore.getLanguage(context)
    override fun languageLabel(code: String): String = LanguageStore.getLabel(code)
    override fun setLanguage(code: String) = LanguageStore.setLanguage(context, code)
    override fun markLanguageChangePending() = LanguageStore.markPendingChange(context)
    override fun fontPreview(): FontPreviewData = FontPreviewData(
        userName = UserProfileStore.getName(context),
        avatar = UserAvatarStore.load(context),
        modelSpec = DefaultModelStore.get(context, DefaultModelRole.CHAT.storageKey).model
            .ifBlank { ApiConfigStore.loadConfig(context).currentModel },
    )
    override fun saveFontScale(scale: Float) = FontSizeStore.setScale(context, scale)
    override fun promptLogEnabled(): Boolean = PromptLogStore.isEnabled(context)
    override fun setPromptLogEnabled(enabled: Boolean) = PromptLogStore.setEnabled(context, enabled)
    override fun preferences(): Preferences = PreferencesStore.get(context)
    override fun savePreferences(preferences: Preferences) = PreferencesStore.set(context, preferences)
}
