package me.rerere.momoly.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PencilLine
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Smile
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.momoly.R
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.di.LocalAppContainer
import me.rerere.momoly.ui.components.AppIconButton
import me.rerere.momoly.ui.components.AppTextArea
import me.rerere.momoly.ui.components.Space12
import me.rerere.momoly.ui.components.Space16
import me.rerere.momoly.ui.components.Space8
import me.rerere.momoly.ui.components.appClickable
import me.rerere.momoly.ui.theme.WeChatAvatar
import me.rerere.momoly.ui.theme.weChatColors
import me.rerere.momoly.ui.theme.weChatTimeLabel

/** 微信会话行的几何：48dp 头像，文本列与头像间距 12dp，分隔线对齐文本左边缘 */
private val DrawerRowStartPadding = 12.dp
private val DrawerAvatarSize = 48.dp
private val DrawerAvatarTextGap = 12.dp
private val DrawerDividerInset = DrawerRowStartPadding + DrawerAvatarSize + DrawerAvatarTextGap

/** 根据当前小时返回对应的问候语字符串资源 ID */
private fun greetingResId(): Int {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 0..5, in 22..23 -> R.string.greeting_night
        in 6..8 -> R.string.greeting_morning
        in 9..11 -> R.string.greeting_late_morning
        in 12..13 -> R.string.greeting_noon
        in 14..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening  // 18-21
    }
}

@Composable
fun ChatDrawerContent(
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onCharList: () -> Unit = {},
    onSearch: () -> Unit = {},
    currentCharFile: String = "",
    sessions: List<ChatSession> = emptyList(),
    refreshKey: Int = 0,
    onOpenCharacter: (fileName: String, displayName: String) -> Unit = { _, _ -> },
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onPinSession: (String, Boolean) -> Unit = { _, _ -> },
    onRegenerateTitle: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    longPressHaptic: Boolean = true,
    onUserProfileChanged: (String, String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val profileController = LocalAppContainer.current.features.chatUserProfile
    val initialProfile = remember(profileController) { profileController.load() }

    var userName by remember { mutableStateOf(initialProfile.name) }
    var personaDescription by remember { mutableStateOf(initialProfile.description) }
    var avatarBitmap by remember { mutableStateOf(initialProfile.avatar) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }

    // 角色卡列表：角色库提供卡名/头像，聊天库提供每个角色最近会话的最后一条消息
    val container = LocalAppContainer.current
    val characterController = remember(container) {
        DrawerCharacterController(container.features.characterLibrary, container.chatRepository)
    }
    var characters by remember { mutableStateOf<List<DrawerCharacter>>(emptyList()) }
    var charactersLoaded by remember { mutableStateOf(false) }
    // refreshKey 在抽屉每次打开时递增：角色卡的增删改发生在抽屉关闭期间
    LaunchedEffect(sessions, refreshKey) {
        characters = withContext(Dispatchers.IO) { characterController.load(sessions) }
        charactersLoaded = true
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                profileController.saveAvatar(uri)?.let { avatarBitmap = it }
            }
        }
    }

    val colors = weChatColors()
    // 微信搜索框浅色 #F7F7F7、深色 #2C2C2E，正好是这两个令牌；用色板自身判断明暗，不看主题
    val searchFieldBackground = if (colors.listBackground.luminance() < 0.5f) {
        colors.inputFieldBackground
    } else {
        colors.inputBarBackground
    }
    // 时间分隔里的“昨天”由资源提供，其余（时刻/星期/日期）交给 weChatTimeLabel
    val yesterdayLabel = stringResource(R.string.time_yesterday)

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text(stringResource(R.string.edit_avatar)) },
            text = {
                Column(Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space12)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space12)) {
                        WeChatAvatar(
                            bitmap = avatarBitmap,
                            label = userName.ifBlank { "user" },
                            size = 48.dp,
                        )
                        Text(userName.ifBlank { "user" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }

                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Lucide.ImagePlus, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Space8))
                        Text(stringResource(R.string.pick_from_gallery))
                    }

                    if (avatarBitmap != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    profileController.deleteAvatar()
                                    avatarBitmap = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Lucide.Trash2, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(Space8))
                            Text(stringResource(R.string.remove_avatar),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }

                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (showNameDialog) {
        var editName by remember { mutableStateOf(userName) }
        val editDesc = remember { TextFieldState(personaDescription) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.edit_username)) },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space12),
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.edit_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextArea(
                        state = editDesc,
                        label = stringResource(R.string.persona_description_label),
                        minLines = 3, maxLines = 8,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = editName.trim()
                    val savedName = trimmed.ifBlank { userName }
                    userName = savedName
                    personaDescription = editDesc.text.toString().trim()
                    onUserProfileChanged(savedName, personaDescription)
                    showNameDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // 微信会话列表：整块白/深色底铺满抽屉，不再用外层大 padding 的卡片观感
    Column(
        Modifier.width(300.dp).fillMaxSize()
            .background(colors.listBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── 用户资料 ──
        Row(
            Modifier.fillMaxWidth()
                .padding(start = Space16, end = Space8, top = 12.dp, bottom = Space8),
            horizontalArrangement = Arrangement.spacedBy(Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(DrawerAvatarSize)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showAvatarDialog = true },
            ) {
                WeChatAvatar(
                    bitmap = avatarBitmap,
                    label = userName.ifBlank { "user" },
                    size = DrawerAvatarSize,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(userName.ifBlank { "user" },
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = colors.incomingText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(greetingResId()),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.secondaryText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AppIconButton(
                icon = Lucide.PencilLine,
                contentDescription = stringResource(R.string.edit),
                onClick = { showNameDialog = true },
                size = 32.dp,
                iconSize = 16.dp,
                tint = colors.iconTint,
            )
        }

        // ── 搜索入口 ──
        Row(
            Modifier.padding(start = Space12, end = Space12, bottom = Space8)
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(searchFieldBackground)
                .clickable { onSearch() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Search, null, Modifier.size(16.dp), tint = colors.secondaryText)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.search_chats),
                fontSize = 14.sp, color = colors.secondaryText, maxLines = 1)
        }

        // ── 会话列表 ──
        LazyColumn(Modifier.weight(1f)) {
            if (charactersLoaded && characters.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_characters),
                        fontSize = 13.sp,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                }
            } else {
                itemsIndexed(characters, key = { _, character -> character.fileName }) { index, character ->
                    // 重命名会话对话框的初始标题与旧会话列表一致：会话标题 → 首条用户消息
                    val session = sessions.firstOrNull { it.id == character.sessionId }
                    val defaultTitle = stringResource(R.string.new_chat)
                    val renameTitle = session?.title?.takeIf { it.isNotBlank() }
                        ?: session?.messages?.firstOrNull { it.role == "user" }?.content
                            ?.replace('\n', ' ')?.take(24)
                        ?: defaultTitle
                    CharacterCardRow(
                        character = character,
                        selected = character.fileName == currentCharFile,
                        renameTitle = renameTitle,
                        yesterdayLabel = yesterdayLabel,
                        onOpen = { onOpenCharacter(character.fileName, character.displayName) },
                        onRenameSession = onRenameSession,
                        onPinSession = onPinSession,
                        onRegenerateTitle = onRegenerateTitle,
                        onDeleteSession = onDeleteSession,
                        longPressHaptic = longPressHaptic,
                        showDivider = index != characters.lastIndex,
                    )
                }
            }
        }

        // ── 底部入口：角色卡 / 设置 ──
        HorizontalDivider(color = colors.listDivider)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space8, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                icon = Lucide.Smile,
                contentDescription = stringResource(R.string.characters),
                onClick = onCharList,
                tint = colors.iconTint,
                iconSize = 22.dp,
            )
            Spacer(Modifier.weight(1f))
            AppIconButton(
                icon = Lucide.Settings,
                contentDescription = stringResource(R.string.settings),
                onClick = onSettings,
                tint = colors.iconTint,
                iconSize = 22.dp,
            )
        }
    }
}

/** 微信会话列表行：头像 + 角色名 + 最新消息预览 + 右侧时间；有会话时长按弹出会话操作菜单 */
@Composable
private fun CharacterCardRow(
    character: DrawerCharacter,
    selected: Boolean,
    renameTitle: String,
    yesterdayLabel: String,
    onOpen: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onPinSession: (String, Boolean) -> Unit,
    onRegenerateTitle: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    longPressHaptic: Boolean,
    showDivider: Boolean = true,
) {
    val colors = weChatColors()
    var showMenu by remember { mutableStateOf(false) }
    val sessionId = character.sessionId
    val onLongClick: (() -> Unit)? = if (sessionId != null) {
        { showMenu = true }
    } else {
        null
    }
    val displayName = character.displayName.ifBlank {
        stringResource(R.string.default_character)
    }
    // updatedAt 为 0 表示这个角色还没开过会话，微信列表里这类行右侧不显示时间
    val timeLabel = if (character.updatedAt > 0) {
        weChatTimeLabel(character.updatedAt, yesterdayLabel)
    } else {
        ""
    }
    Box {
        Column(
            Modifier.fillMaxWidth()
                // 选中态就是微信列表那层浅灰，保持行的扁平感，不再用圆角高亮块
                .background(if (selected) colors.listDivider else colors.listBackground)
                .appClickable(
                    onClick = onOpen,
                    onLongClick = onLongClick,
                    hapticFeedbackEnabled = longPressHaptic,
                ),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = DrawerRowStartPadding, end = Space12, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeChatAvatar(
                    bitmap = character.avatar,
                    label = displayName,
                    size = DrawerAvatarSize,
                )
                Column(
                    Modifier.weight(1f).padding(start = DrawerAvatarTextGap),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (character.pinned) {
                            Icon(Lucide.Pin, stringResource(R.string.pin), Modifier.size(12.dp),
                                tint = colors.secondaryText)
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            displayName,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = colors.incomingText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (timeLabel.isNotEmpty()) {
                            Spacer(Modifier.width(Space8))
                            Text(timeLabel,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = colors.timeText,
                                maxLines = 1)
                        }
                    }
                    Text(
                        me.rerere.momoly.domain.RegexEngine.applyForDisplay(character.lastMessage, emptyList(),
                            userName=LocalAppContainer.current.features.chatUserProfile.loadName(character.worldName),
                            charName=character.displayName).ifBlank { stringResource(R.string.no_messages_yet) },
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showDivider) {
                // 微信的分隔线从文字左边缘起，不切到头像
                HorizontalDivider(
                    modifier = Modifier.padding(start = DrawerDividerInset),
                    thickness = 0.5.dp,
                    color = colors.listDivider,
                )
            }
        }
        if (sessionId != null) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    leadingIcon = { Icon(Lucide.PencilLine, null, Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onRenameSession(sessionId, renameTitle)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (character.pinned) R.string.unpin else R.string.pin)) },
                    leadingIcon = { Icon(Lucide.Pin, null, Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onPinSession(sessionId, !character.pinned)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.regenerate_title)) },
                    leadingIcon = { Icon(Lucide.RefreshCw, null, Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onRegenerateTitle(sessionId)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Lucide.Trash2, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        showMenu = false
                        onDeleteSession(sessionId)
                    },
                )
            }
        }
    }
}
