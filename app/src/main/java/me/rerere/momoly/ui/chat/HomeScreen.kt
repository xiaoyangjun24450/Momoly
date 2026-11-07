package me.rerere.momoly.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import me.rerere.momoly.ui.components.appClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.momoly.R
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.data.settings.ThemeMode
import me.rerere.momoly.data.world.WorldContext
import me.rerere.momoly.di.LocalAppContainer
import me.rerere.momoly.ui.character.rememberContactImporter
import me.rerere.momoly.ui.components.AppIconButton
import me.rerere.momoly.ui.settings.SettingsScreen
import me.rerere.momoly.ui.theme.*

internal enum class HomeTab { Contacts, Map, Worlds, Settings }
private enum class ContactOperation { Reset, Delete }

@Composable
internal fun HomeRoute(
    sessions: List<ChatSession>,
    selection: HomeWorldSelection,
    worldContext: WorldContext,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNavigate: (ChatDestination) -> Unit,
    onOpenCharacter: (String, String) -> Unit,
    onClearContactHistory: suspend (String) -> Unit,
    onProfileSaved: () -> Unit,
) {
    val container = LocalAppContainer.current
    val universe by container.worldLife.worlds.collectAsState()
    val lifeWorld = universe.worlds[selection.name]
    var profileFile by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val profileController = container.features.chatUserProfile
    var homeProfile by remember(selection.name) { mutableStateOf<ChatUserProfile?>(null) }
    var profileRevision by remember { mutableIntStateOf(0) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val controller = remember(container) {
        DrawerCharacterController(container.features.characterLibrary, container.chatRepository)
    }
    var contacts by remember { mutableStateOf<List<DrawerCharacter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Contacts) }
    val addContact = rememberContactImporter { refresh++; tab = HomeTab.Contacts }
    val scope = rememberCoroutineScope()
    var selectedContact by remember { mutableStateOf<DrawerCharacter?>(null) }
    var operation by remember { mutableStateOf<ContactOperation?>(null) }
    var managing by remember { mutableStateOf(false) }
    LaunchedEffect(sessions, refresh) {
        loading = true
        try {
            contacts = controller.load(sessions)
            withContext(Dispatchers.IO) { profileController.initializeWorlds(contacts.map { it.worldName }) }
            profileRevision++
            onProfileSaved()
            resolveHomeWorld(selection.name, contacts.map { it.worldName }.distinct())?.let(selection::select)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Toast.makeText(context, resources.getString(R.string.list_load_failed_fmt, error.message.orEmpty()), Toast.LENGTH_LONG).show()
        } finally { loading = false }
    }
    LaunchedEffect(selection.name, profileRevision, loading) {
        if (loading) return@LaunchedEffect
        try {
            homeProfile = withContext(Dispatchers.IO) { profileController.load(selection.name.orEmpty()) }
            if (homeProfile?.readFailed == true) Toast.makeText(context, R.string.world_profile_recovery, Toast.LENGTH_LONG).show()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Toast.makeText(context, R.string.world_profile_load_failed, Toast.LENGTH_LONG).show() }
    }
    if (editingProfile && homeProfile != null) {
        WorldProfileEditor(world = selection.name.orEmpty(), initial = homeProfile!!, controller = profileController,
            onBack = { editingProfile = false }, onSaved = {
                editingProfile = false
                profileRevision++
                onProfileSaved()
            })
        return
    }
    BackHandler(enabled = tab != HomeTab.Contacts) { tab = HomeTab.Contacts }
    HomePage(
        contacts = contacts,
        selectedWorld = selection.name,
        worldContext = worldContext,
        tab = tab,
        loading = loading,
        onTab = { tab = it },
        profile = homeProfile,
        onEditProfile = { editingProfile = true },
        onAddContact = addContact,
        onSelectWorld = { container.worldLife.switchWorld(it); selection.select(it); tab = HomeTab.Contacts },
        onOpenCharacter = { container.worldLife.markRead(it.fileName); onOpenCharacter(it.fileName, it.displayName) },
        onLongPressContact = { selectedContact = it; operation = null },
        map = { me.rerere.momoly.ui.world.WorldMapPage(lifeWorld, homeProfile?.name.orEmpty()) },
        worldControls = { me.rerere.momoly.ui.world.WorldLifeControls(container.worldLife,lifeWorld) },
        unread = lifeWorld?.actors?.mapValues { it.value.unread }.orEmpty(),
        settings = {
            SettingsScreen(
                onBack = { tab = HomeTab.Contacts },
                embedded = true,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onNavigateToCharacters = { onNavigate(ChatDestination.Characters) },
                onNavigateToApiConfig = { onNavigate(ChatDestination.ApiConfig) },
                onNavigateToFontSize = { onNavigate(ChatDestination.FontSize) },
                onNavigateToTts = { onNavigate(ChatDestination.Tts) },
                onNavigateToAbout = { onNavigate(ChatDestination.About) },
            )
        },
    )
    profileFile?.let { file -> me.rerere.momoly.ui.world.CharacterLifeProfile(lifeWorld,file) { profileFile=null } }
    selectedContact?.let { contact ->
        val selectedOperation = operation
        AlertDialog(
            onDismissRequest = { if (!managing) selectedContact = null },
            title = { Text(if (selectedOperation == null) contact.displayName else stringResource(
                if (selectedOperation == ContactOperation.Reset) R.string.home_reset_contact else R.string.home_delete_contact)) },
            text = {
                if (selectedOperation == null) {
                    Column {
                        TextButton(onClick = { profileFile=contact.fileName; selectedContact=null },modifier=Modifier.fillMaxWidth()) { Text("角色资料") }
                        TextButton(onClick = { operation = ContactOperation.Reset }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.home_reset_contact))
                        }
                        val isDefault = contact.fileName == container.features.characterLibrary.defaultCardName()
                        TextButton(onClick = { operation = ContactOperation.Delete }, enabled = !isDefault,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.home_delete_contact))
                        }
                        if (isDefault) Text(stringResource(R.string.home_default_contact_reset_only))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(if (selectedOperation == ContactOperation.Reset)
                            R.string.home_reset_contact_confirm else R.string.home_delete_contact_confirm, contact.displayName))
                        if (managing) CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            },
            confirmButton = {
                if (selectedOperation != null) TextButton(enabled = !managing, onClick = {
                    managing = true
                    scope.launch {
                        try {
                            withContext(NonCancellable) {
                                container.worldLife.forgetConversation(contact.fileName)
                                onClearContactHistory(contact.fileName)
                                if (selectedOperation == ContactOperation.Delete) {
                                    check(contact.fileName != container.features.characterLibrary.defaultCardName())
                                    container.features.characterLibrary.delete(contact.fileName,
                                        deleteChats = false, deleteAssociations = false)
                                }
                            }
                            container.worldLife.syncCards()
                            container.worldLife.resume()
                            selectedContact = null
                            Toast.makeText(context, resources.getString(if (selectedOperation == ContactOperation.Reset)
                                R.string.home_contact_reset_done else R.string.home_contact_deleted), Toast.LENGTH_SHORT).show()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Toast.makeText(context, resources.getString(R.string.home_contact_operation_failed,
                                error.message.orEmpty()), Toast.LENGTH_LONG).show()
                        } finally { managing = false; refresh++ }
                    }
                }) { Text(stringResource(if (selectedOperation == ContactOperation.Reset)
                    R.string.home_reset_contact else R.string.home_delete_contact), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !managing, onClick = { selectedContact = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
internal fun HomePage(
    contacts: List<DrawerCharacter>,
    selectedWorld: String?,
    worldContext: WorldContext,
    tab: HomeTab,
    loading: Boolean = false,
    onTab: (HomeTab) -> Unit,
    onAddContact: () -> Unit,
    onSelectWorld: (String) -> Unit,
    onOpenCharacter: (DrawerCharacter) -> Unit,
    onLongPressContact: (DrawerCharacter) -> Unit = {},
    settings: @Composable () -> Unit = {},
    profile: ChatUserProfile? = null,
    onEditProfile: () -> Unit = {},
    map: @Composable () -> Unit = {},
    worldControls: @Composable () -> Unit = {},
    unread: Map<String,Int> = emptyMap(),
) {
    val colors = weChatColors()
    val visibleContacts = remember(contacts, selectedWorld) { contacts.filter { it.worldName == selectedWorld } }
    val worlds = remember(contacts) { contacts.groupingBy { it.worldName }.eachCount() }
    Scaffold(
        modifier = Modifier.testTag("home_screen"),
        containerColor = colors.chatBackground,
        topBar = {
            if (tab == HomeTab.Map) {
                Spacer(Modifier.fillMaxWidth().statusBarsPadding())
            } else {
            Column(Modifier.fillMaxWidth().background(colors.headerBackground).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val avatarLabel = stringResource(R.string.world_profile_open,
                        worldContext.worldName.ifBlank { stringResource(R.string.home_unassigned_world) })
                    Box(Modifier.size(48.dp).testTag("home_user_avatar")
                        .semantics { contentDescription = avatarLabel }
                        .appClickable(enabled = profile != null, onClick = onEditProfile), contentAlignment = Alignment.Center) {
                        WeChatAvatar(profile?.avatar, profile?.name.orEmpty(), size = 44.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(worldContext.worldName.ifBlank { stringResource(R.string.home_unassigned_world) },
                            fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.incomingText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Lucide.MapPin, null, Modifier.size(14.dp), tint = colors.secondaryText)
                            Text(stringResource(R.string.home_user_location, worldContext.userLocation),
                                fontSize = 12.sp, color = colors.secondaryText)
                            Spacer(Modifier.width(10.dp))
                            Icon(Lucide.Clock, null, Modifier.size(14.dp), tint = colors.secondaryText)
                            Text(worldContext.time, fontSize = 12.sp, color = colors.secondaryText)
                        }
                    }
                    AppIconButton(Lucide.Plus, stringResource(R.string.home_add_contact), onAddContact,
                        modifier = Modifier.testTag("home_add_contact"), tint = colors.iconTint)
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.headerDivider)
                worldControls()
            }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = colors.inputBarBackground, tonalElevation = 0.dp) {
                HomeTab.entries.forEach { item ->
                    val label = if(item==HomeTab.Map) "地图" else stringResource(when (item) {
                        HomeTab.Contacts -> R.string.home_contacts
                        HomeTab.Map -> R.string.home_worlds
                        HomeTab.Worlds -> R.string.home_worlds
                        HomeTab.Settings -> R.string.settings
                    })
                    NavigationBarItem(
                        modifier = Modifier.testTag("home_tab_${item.name}"),
                        selected = tab == item,
                        onClick = { onTab(item) },
                        icon = { Icon(when (item) {
                            HomeTab.Contacts -> Lucide.Users
                            HomeTab.Map -> Lucide.MapPin
                            HomeTab.Worlds -> Lucide.Earth
                            HomeTab.Settings -> Lucide.Settings
                        }, null, Modifier.size(23.dp)) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.sendButton, selectedTextColor = colors.sendButton,
                            unselectedIconColor = colors.iconTint, unselectedTextColor = colors.secondaryText,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                HomeTab.Contacts -> if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.sendButton)
                } else if (visibleContacts.isEmpty()) {
                    Text(stringResource(R.string.home_no_contacts), color = colors.secondaryText,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag("home_contacts_list")) {
                        items(visibleContacts, key = { it.fileName }) { contact ->
                            ContactRow(contact, onClick = { onOpenCharacter(contact) },
                                onLongClick = { onLongPressContact(contact) }, unread=unread[contact.fileName] ?: 0,
                                userName=profile?.name.orEmpty())
                        }
                    }
                }
                HomeTab.Worlds -> if (worlds.isEmpty()) {
                    Text(stringResource(R.string.home_no_worlds), color = colors.secondaryText,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(worlds.keys.toList(), key = { it }) { world ->
                            Row(Modifier.fillMaxWidth().background(colors.listBackground)
                                .clickable { onSelectWorld(world) }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Lucide.Earth, null, Modifier.size(28.dp), tint = colors.sendButton)
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    Text(world.ifBlank { stringResource(R.string.home_unassigned_world) },
                                        color = colors.incomingText, fontSize = 16.sp)
                                    Text(stringResource(R.string.home_contact_count, worlds.getValue(world)),
                                        color = colors.secondaryText, fontSize = 12.sp)
                                }
                                if (world == selectedWorld) {
                                    Text(stringResource(R.string.home_current_world), color = colors.sendButton, fontSize = 12.sp)
                                } else Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = colors.secondaryText)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = colors.listDivider)
                        }
                    }
                }
                HomeTab.Map -> map()
                HomeTab.Settings -> settings()
            }
        }
    }
}

@Composable
private fun ContactRow(contact: DrawerCharacter, onClick: () -> Unit, onLongClick: () -> Unit, unread: Int = 0, userName: String = "") {
    val colors = weChatColors()
    val yesterday = stringResource(R.string.time_yesterday)
    val time = remember(contact.updatedAt, yesterday) {
        if (contact.updatedAt > 0) weChatTimeLabel(contact.updatedAt, yesterday) else ""
    }
    Column(Modifier.fillMaxWidth().background(colors.listBackground)
        .testTag("home_contact_${contact.fileName}").combinedClickable(onClick = onClick,
            onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.home_manage_contact))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            WeChatAvatar(contact.avatar, contact.displayName, size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.displayName + if(unread>0) " · $unread 条新消息" else "", Modifier.weight(1f), color = colors.incomingText,
                        fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(time, color = colors.timeText, fontSize = 11.sp)
                }
                Text(me.rerere.momoly.domain.RegexEngine.applyForDisplay(contact.lastMessage, emptyList(),
                    userName=userName, charName=contact.displayName).ifBlank { stringResource(R.string.no_messages_yet) },
                    color = colors.secondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 0.5.dp, color = colors.listDivider)
    }
}
