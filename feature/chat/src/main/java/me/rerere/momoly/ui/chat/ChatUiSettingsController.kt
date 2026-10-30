package me.rerere.momoly.ui.chat

import me.rerere.momoly.data.settings.NavButtonsMode
import me.rerere.momoly.data.settings.Preferences

data class ChatUiSettings(
    val fontScale: Float,
    val showChatBarCharacterName: Boolean,
    val showChatBarModelName: Boolean,
    val showChatBarProvider: Boolean,
    val showUserAvatar: Boolean,
    val showUserName: Boolean,
    val showUserTimestamp: Boolean,
    val showUserActions: Boolean,
    val showModelIcon: Boolean,
    val showModelName: Boolean,
    val showModelTimestamp: Boolean,
    val showTokenUsage: Boolean,
    val showTokenSpeed: Boolean,
    val showGenerationTime: Boolean,
    val htmlCssRendering: Boolean,
    val javascriptSupport: Boolean,
    val mathRendering: Boolean,
    val userMarkdown: Boolean,
    val thinkingMarkdown: Boolean,
    val characterMarkdown: Boolean,
    val autoCollapseCode: Boolean,
    val codeCollapseLines: Int,
    val autoCollapseThinking: Boolean,
    val confirmRegenerate: Boolean,
    val confirmDeleteCurrentVersion: Boolean,
    val confirmDeleteAllVersions: Boolean,
    val navButtonsMode: NavButtonsMode,
    val showChatListDate: Boolean,
    val newChatOnCharSwitch: Boolean,
    val newChatOnDeleteTopic: Boolean,
    val newChatOnLaunch: Boolean,
    val enterToSend: Boolean,
    val sidebarHaptic: Boolean,
    val longPressHaptic: Boolean,
)

interface ChatUiSettingsDataSource {
    /** 偏好页已移除，快照不再投影它；保留接口以免改动已有数据源实现 */
    fun preferences(): Preferences
    fun fontScale(): Float
}

/**
 * 聊天界面的一次性渲染快照。
 *
 * 偏好设置页已移除，所有显示开关改为固定值（仅字号跟随后台设置），因此 [load] 不再读取
 * 持久化偏好——旧版本可能残留任意值，必须无视，否则用户升级后会看到和被删掉的开关不符的界面。
 */
class ChatUiSettingsController(
    private val dataSource: ChatUiSettingsDataSource,
) {
    fun load(): ChatUiSettings = ChatUiSettings(
        fontScale = dataSource.fontScale(),
        // 聊天栏：只保留角色名
        showChatBarCharacterName = true,
        showChatBarModelName = false,
        showChatBarProvider = false,
        // 用户消息：只有头像，操作按钮常显，不带名字与时间
        showUserAvatar = true,
        showUserName = false,
        showUserTimestamp = false,
        showUserActions = true,
        // AI 消息头信息与统计全部关闭，只留角色卡头像（见 ChatMessageItems）
        showModelIcon = false,
        showModelName = false,
        showModelTimestamp = false,
        showTokenUsage = false,
        showTokenSpeed = false,
        showGenerationTime = false,
        // 渲染设置默认全开（代码块折叠阈值固定 5 行）
        htmlCssRendering = true,
        javascriptSupport = true,
        mathRendering = true,
        userMarkdown = true,
        thinkingMarkdown = true,
        characterMarkdown = true,
        autoCollapseCode = true,
        codeCollapseLines = 5,
        autoCollapseThinking = true,
        // 确认与新建会话沿用删除偏好页时的默认行为
        confirmRegenerate = false,
        confirmDeleteCurrentVersion = true,
        confirmDeleteAllVersions = true,
        navButtonsMode = NavButtonsMode.ON_SCROLL,
        showChatListDate = false,
        newChatOnCharSwitch = false,
        newChatOnDeleteTopic = false,
        newChatOnLaunch = true,
        enterToSend = false,
        sidebarHaptic = true,
        longPressHaptic = true,
    )
}
