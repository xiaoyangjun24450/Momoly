package me.rerere.momoly.ui.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.momoly.R
import me.rerere.momoly.ui.components.SettingsSubPage
import me.rerere.momoly.ui.theme.WeChatAvatar

/** 草稿绑定打开时的世界，头像选择和复制在保存前都不写入资料。 */
@Composable
internal fun WorldProfileEditor(
    world: String,
    initial: ChatUserProfile,
    controller: ChatUserProfileController,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var name by rememberSaveable(world) { mutableStateOf(initial.name) }
    var description by rememberSaveable(world) { mutableStateOf(initial.description) }
    var avatarMode by rememberSaveable(world) { mutableStateOf("keep") }
    var selectedUri by rememberSaveable(world) { mutableStateOf<String?>(null) }
    var copyWorld by rememberSaveable(world) { mutableStateOf<String?>(null) }
    var avatar by remember(world) { mutableStateOf(initial.avatar) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var copyChoices by remember { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val worldLabel = world.ifBlank { stringResource(R.string.home_unassigned_world) }
    val back = { if (!saving) onBack() }
    BackHandler(onBack = back)

    LaunchedEffect(avatarMode, selectedUri, copyWorld) {
        loading = true
        try {
            avatar = when (avatarMode) {
                "pick" -> controller.previewAvatar(Uri.parse(requireNotNull(selectedUri)))
                    ?: error("Unable to decode image")
                "copy" -> withContext(Dispatchers.IO) { controller.load(requireNotNull(copyWorld)).avatar }
                "remove" -> null
                else -> initial.avatar
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            avatarMode = "keep"
            Toast.makeText(context, R.string.world_profile_image_failed, Toast.LENGTH_LONG).show()
        } finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri.toString()
            avatarMode = "pick"
        }
    }
    SettingsSubPage(
        title = stringResource(R.string.world_profile_title),
        onBack = back,
    ) {
        Text(stringResource(R.string.world_profile_scope, worldLabel), style = MaterialTheme.typography.bodyMedium)
        if (initial.readFailed) Text(stringResource(R.string.world_profile_recovery), color = MaterialTheme.colorScheme.error)
        else if (!initial.configured) Text(stringResource(R.string.world_profile_first_time),
            style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WeChatAvatar(avatar, name, size = 64.dp)
            Column {
                TextButton(enabled = !saving && !loading, onClick = { picker.launch("image/*") }) {
                    Text(stringResource(R.string.pick_from_gallery))
                }
                if (avatar != null) TextButton(enabled = !saving && !loading, onClick = { avatarMode = "remove" }) {
                    Text(stringResource(R.string.remove_avatar))
                }
            }
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !saving,
            label = { Text(stringResource(R.string.world_profile_name)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("world_profile_name"))
        OutlinedTextField(value = description, onValueChange = { description = it }, enabled = !saving,
            label = { Text(stringResource(R.string.persona_description_label)) }, minLines = 5, maxLines = 10,
            modifier = Modifier.fillMaxWidth().testTag("world_profile_description"))
        Text(stringResource(R.string.world_profile_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(enabled = !saving && !loading, onClick = {
            scope.launch {
                try {
                    copyChoices = withContext(Dispatchers.IO) { controller.worlds().filter { it != world } }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    Toast.makeText(context, R.string.world_profile_load_failed, Toast.LENGTH_LONG).show()
                }
            }
        }) { Text(stringResource(R.string.world_profile_copy)) }
        Button(enabled = !saving && !loading && name.isNotBlank(), modifier = Modifier.fillMaxWidth()
            .testTag("world_profile_save"), onClick = {
            saving = true
            scope.launch {
                try {
                    controller.saveWorld(world, name, description, avatar, avatarMode != "keep")
                    onSaved()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.world_profile_save_failed, Toast.LENGTH_LONG).show()
                } finally { saving = false }
            }
        }) { Text(stringResource(if (saving) R.string.world_profile_saving else R.string.save)) }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
    }
    copyChoices?.let { choices ->
        AlertDialog(onDismissRequest = { copyChoices = null },
            title = { Text(stringResource(R.string.world_profile_copy)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    if (choices.isEmpty()) item { Text(stringResource(R.string.world_profile_no_copy)) }
                    items(choices.size) { index ->
                        val source = choices[index]
                        TextButton(onClick = {
                            copyChoices = null
                            loading = true
                            scope.launch {
                                try {
                                    val copied = withContext(Dispatchers.IO) { controller.load(source) }
                                    name = copied.name
                                    description = copied.description
                                    copyWorld = source
                                    avatarMode = "copy"
                                    avatar = copied.avatar
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    Toast.makeText(context, R.string.world_profile_load_failed, Toast.LENGTH_LONG).show()
                                } finally { loading = false }
                            }
                        }) { Text(source.ifBlank { stringResource(R.string.home_unassigned_world) }) }
                    }
                }
            }, confirmButton = {}, dismissButton = {
                TextButton(onClick = { copyChoices = null }) { Text(stringResource(R.string.cancel)) }
            })
    }
}
