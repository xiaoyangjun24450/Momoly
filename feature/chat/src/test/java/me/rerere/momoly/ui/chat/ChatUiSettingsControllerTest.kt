package me.rerere.momoly.ui.chat

import me.rerere.momoly.data.settings.NavButtonsMode
import me.rerere.momoly.data.settings.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiSettingsControllerTest {
    /** 偏好页移除后快照必须锁死：数据源里残留的旧偏好（这里故意全设成反面值）一概不生效。 */
    @Test
    fun loadIgnoresStoredPreferencesAndReturnsLockedSnapshot() {
        val source = FakeChatUiSettingsDataSource(
            preferences = Preferences(
                showChatBarCharacterName = false,
                showChatBarModelName = true,
                showChatBarProvider = true,
                showUserAvatar = false,
                showUserName = true,
                showUserTimestamp = true,
                showUserActions = false,
                showModelIcon = true,
                showModelName = true,
                showModelTimestamp = true,
                showTokenUsage = true,
                showTokenSpeed = true,
                showGenerationTime = true,
                htmlCssRendering = false,
                javascriptSupport = false,
                mathRendering = false,
                userMarkdown = false,
                thinkingMarkdown = false,
                characterMarkdown = false,
                autoCollapseCode = false,
                codeCollapseLines = 12,
                autoCollapseThinking = false,
                confirmRegenerate = true,
                confirmDeleteCurrentVersion = false,
                confirmDeleteAllVersions = false,
                navButtonsMode = NavButtonsMode.ALWAYS,
                showChatListDate = true,
                newChatOnCharSwitch = true,
                newChatOnDeleteTopic = true,
                newChatOnLaunch = false,
                enterToSend = true,
                sidebarHaptic = false,
                longPressHaptic = false,
            ),
        )

        val settings = ChatUiSettingsController(source).load()

        assertTrue(settings.showChatBarCharacterName)
        assertFalse(settings.showChatBarModelName)
        assertFalse(settings.showChatBarProvider)
        assertTrue(settings.showUserAvatar)
        assertFalse(settings.showUserName)
        assertFalse(settings.showUserTimestamp)
        assertTrue(settings.showUserActions)
        assertFalse(settings.showModelIcon)
        assertFalse(settings.showModelName)
        assertFalse(settings.showModelTimestamp)
        assertFalse(settings.showTokenUsage)
        assertFalse(settings.showTokenSpeed)
        assertFalse(settings.showGenerationTime)
        assertTrue(settings.htmlCssRendering)
        assertTrue(settings.javascriptSupport)
        assertTrue(settings.mathRendering)
        assertTrue(settings.userMarkdown)
        assertTrue(settings.thinkingMarkdown)
        assertTrue(settings.characterMarkdown)
        assertTrue(settings.autoCollapseCode)
        assertEquals(5, settings.codeCollapseLines)
        assertTrue(settings.autoCollapseThinking)
        assertFalse(settings.confirmRegenerate)
        assertTrue(settings.confirmDeleteCurrentVersion)
        assertTrue(settings.confirmDeleteAllVersions)
        assertEquals(NavButtonsMode.ON_SCROLL, settings.navButtonsMode)
        assertFalse(settings.showChatListDate)
        assertFalse(settings.newChatOnCharSwitch)
        assertFalse(settings.newChatOnDeleteTopic)
        assertTrue(settings.newChatOnLaunch)
        assertFalse(settings.enterToSend)
        assertTrue(settings.sidebarHaptic)
        assertTrue(settings.longPressHaptic)
    }

    /** 字号是唯一仍然动态的项，必须跟随后台字号设置。 */
    @Test
    fun loadFollowsFontScale() {
        val source = FakeChatUiSettingsDataSource()
        val controller = ChatUiSettingsController(source)

        assertEquals(1.0f, controller.load().fontScale, 0.0f)
        source.scale = 1.25f
        assertEquals(1.25f, controller.load().fontScale, 0.0f)
    }

    private class FakeChatUiSettingsDataSource(
        var preferences: Preferences = Preferences(),
        var scale: Float = 1.0f,
    ) : ChatUiSettingsDataSource {
        override fun preferences(): Preferences = preferences

        override fun fontScale(): Float = scale
    }
}
