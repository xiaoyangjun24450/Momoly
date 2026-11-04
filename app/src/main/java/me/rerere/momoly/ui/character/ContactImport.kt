package me.rerere.momoly.ui.character

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import me.rerere.momoly.data.character.CharacterCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
private data class LifeImportQuestion(val card: CharacterCard, val answer: CompletableDeferred<Boolean>)

/** 首页和角色管理共用：确认世界之前不创建联系人及附属资源。 */
@Composable
internal fun rememberContactImporter(onImported: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = LocalAppContainer.current
    val controller = container.features.characterLibrary
    val selection = rememberHomeWorldSelection()
    val scope = rememberCoroutineScope()
    val imported by rememberUpdatedState(onImported)
    var question by remember { mutableStateOf<WorldImportQuestion?>(null) }
    var preview by remember { mutableStateOf<LifeImportQuestion?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                val card = controller.importPreview(uri, confirmWorld = { world ->
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
                }, confirmPreview = { card ->
                    withContext(Dispatchers.Main.immediate) {
                        val pending = LifeImportQuestion(card, CompletableDeferred())
                        preview = pending
                        try { pending.answer.await() } finally { pending.answer.cancel(); preview = null }
                    }
                })
                selection.select(card.worldName)
                container.worldLife.syncCards()
                container.worldLife.switchWorld(card.worldName)
                imported()
                Toast.makeText(context, R.string.contact_added, Toast.LENGTH_SHORT).show()
            } catch (_: CharacterImportCancelledException) {
                // 明确取消：没有资源落盘，也不是导入错误。
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                me.rerere.momoly.core.diagnostics.SafeLog.error("ContactImport","contact_import_failed",error)
                failure=resources.getString(R.string.contact_import_failed, error.message.orEmpty())
            } finally { busy = false }
        }
    }
    failure?.let { message -> AlertDialog(onDismissRequest={failure=null},title={Text("导入未完成")},text={Text(message)},
        confirmButton={TextButton(onClick={failure=null}){Text("知道了")}}) }
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
    preview?.let { pending ->
        AlertDialog(onDismissRequest = { pending.answer.complete(false) },
            title = { Text("添加 ${pending.card.name}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("此时此刻")
                Text(pending.card.life?.initialTime.orEmpty().replace('T',' '))
                Text(pending.card.life?.background.orEmpty())
                Text("\n前尘往事")
                Text(pending.card.life?.history.orEmpty())
            } },
            confirmButton = { TextButton(onClick = { pending.answer.complete(true) }) { Text("确认添加") } },
            dismissButton = { TextButton(onClick = { pending.answer.complete(false) }) { Text("取消") } })
    }
    return { if (!busy) launcher.launch("*/*") }
}
