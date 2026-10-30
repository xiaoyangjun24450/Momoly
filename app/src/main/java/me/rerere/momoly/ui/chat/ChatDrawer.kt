package me.rerere.momoly.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bolt
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PencilLine
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Smile
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserPen
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
import me.rerere.momoly.ui.components.Space4
import me.rerere.momoly.ui.components.Space8
import me.rerere.momoly.ui.components.appClickable

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
    var avatarColor by remember { mutableStateOf(Color(initialProfile.avatarColor.toULong())) }
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

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text(stringResource(R.string.edit_avatar)) },
            text = {
                Column(Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space12)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space12)) {
                        AvatarCircle(
                            bitmap = avatarBitmap,
                            color = avatarColor,
                            icon = Lucide.UserPen,
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

    Column(
        Modifier.width(300.dp).fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Space16),
        verticalArrangement = Arrangement.spacedBy(Space16),
    ) {
        // ── 用户资料 ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { showAvatarDialog = true }) {
                AvatarCircle(bitmap = avatarBitmap, color = avatarColor,
                    icon = Lucide.UserPen, size = 40.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space4),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(userName.ifBlank { "user" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AppIconButton(
                        icon = Lucide.PencilLine,
                        contentDescription = stringResource(R.string.edit),
                        onClick = { showNameDialog = true },
                        size = 32.dp,
                        iconSize = 16.dp,
                    )
                }
                Text(stringResource(greetingResId()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // ── 搜索入口 ──
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onSearch() }
                .padding(horizontal = Space12, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Search, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.search_chats),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── 角色卡列表（原聊天记录位置） ──
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space4)) {
            item {
                Text(stringResource(R.string.characters),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space4))
            }
            if (charactersLoaded && characters.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_characters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp))
                }
            } else {
                items(characters, key = { it.fileName }) { character ->
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
                        onOpen = { onOpenCharacter(character.fileName, character.displayName) },
                        onRenameSession = onRenameSession,
                        onPinSession = onPinSession,
                        onRegenerateTitle = onRegenerateTitle,
                        onDeleteSession = onDeleteSession,
                        longPressHaptic = longPressHaptic,
                    )
                }
            }
        }

        // ── 底部导航 ──
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space8),
            verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                icon = Lucide.Smile,
                contentDescription = stringResource(R.string.characters),
                onClick = onCharList,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                iconSize = 22.dp,
            )
            Spacer(Modifier.weight(1f))
            AppIconButton(
                icon = Lucide.Bolt,
                contentDescription = stringResource(R.string.settings),
                onClick = onSettings,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                iconSize = 22.dp,
            )
        }
    }
}

/** 角色卡行：头像 + 角色名 + 最新消息预览；有会话时长按弹出会话操作菜单 */
@Composable
private fun CharacterCardRow(
    character: DrawerCharacter,
    selected: Boolean,
    renameTitle: String,
    onOpen: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onPinSession: (String, Boolean) -> Unit,
    onRegenerateTitle: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    longPressHaptic: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    val sessionId = character.sessionId
    val onLongClick: (() -> Unit)? = if (sessionId != null) {
        { showMenu = true }
    } else {
        null
    }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent
                )
                .appClickable(
                    onClick = onOpen,
                    onLongClick = onLongClick,
                    hapticFeedbackEnabled = longPressHaptic,
                )
                .padding(horizontal = Space8, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space8),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = character.avatar
                if (avatar != null) {
                    Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = stringResource(R.string.character_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Lucide.Smile, null, Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    character.displayName.ifBlank { stringResource(R.string.default_character) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    character.lastMessage.ifBlank { stringResource(R.string.no_messages_yet) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (character.pinned) {
                Icon(Lucide.Pin, stringResource(R.string.pin), Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun AvatarCircle(
    bitmap: android.graphics.Bitmap?,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.character_avatar),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(icon, null, Modifier.size(size * 0.6f),
                tint = Color.White)
        }
    }
}
