package me.rerere.momoly.ui.character

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.momoly.R
import me.rerere.momoly.data.character.CharacterImportCancelledException
import me.rerere.momoly.di.LocalAppContainer
import me.rerere.momoly.ui.chat.needsWorldConfirmation
import me.rerere.momoly.ui.chat.rememberHomeWorldSelection

private data class WorldImportQuestion(val world: String, val answer: CompletableDeferred<Boolean>)

/** 首页和角色管理共用：确认世界之前不创建联系人及附属资源。 */
@Composable
internal fun rememberContactImporter(onImported: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val controller = LocalAppContainer.current.features.characterLibrary
    val selection = rememberHomeWorldSelection()
    val scope = rememberCoroutineScope()
    val imported by rememberUpdatedState(onImported)
    var question by remember { mutableStateOf<WorldImportQuestion?>(null) }
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                val card = controller.import(uri) { world ->
                    withContext(Dispatchers.Main.immediate) {
                        if (!needsWorldConfirmation(selection.name, world)) true else {
                            val pending = WorldImportQuestion(world, CompletableDeferred())
                            question = pending
                            try { pending.answer.await() } finally {
                                pending.answer.cancel()
                                question = null
                            }
                        }
                    }
                }
                selection.select(card.worldName)
                imported()
                Toast.makeText(context, R.string.contact_added, Toast.LENGTH_SHORT).show()
            } catch (_: CharacterImportCancelledException) {
                // 明确取消：没有资源落盘，也不是导入错误。
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, context.getString(R.string.contact_import_failed, error.message.orEmpty()), Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    question?.let { pending ->
        AlertDialog(
            onDismissRequest = { pending.answer.complete(false) },
            title = { Text(stringResource(R.string.contact_other_world_title)) },
            text = { Text(stringResource(R.string.contact_other_world_message, pending.world)) },
            confirmButton = {
                TextButton(onClick = { pending.answer.complete(true) }) {
                    Text(stringResource(R.string.contact_go_to_world))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending.answer.complete(false) }) {
                    Text(stringResource(R.string.contact_cancel_add))
                }
            },
        )
    }
    return { if (!busy) launcher.launch("*/*") }
}
