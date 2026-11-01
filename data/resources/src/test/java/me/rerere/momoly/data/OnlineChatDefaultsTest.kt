package me.rerere.momoly.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import me.rerere.momoly.data.character.CharacterRepository
import me.rerere.momoly.data.preset.PresetRepository
import me.rerere.momoly.data.worldbook.WorldBookEntry
import me.rerere.momoly.data.worldbook.WorldBookRepository
import me.rerere.momoly.domain.PromptBuilder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnlineChatDefaultsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CharacterRepository.charsDir(context).deleteRecursively()
        PresetRepository.presetsDir(context).deleteRecursively()
        WorldBookRepository.worldDir(context).deleteRecursively()
        context.getSharedPreferences("preset_repo", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun fillsEmptyDefaultWithoutChangingItsIdOrLaterUserEdits() = runBlocking {
        val file = File(PresetRepository.presetsDir(context), "Default.json")
        file.writeText("""{"momoly_id":"existing-default"}""")

        PresetRepository.ensureDefaultPreset(context, "Default")
        val installed = PresetRepository.load(context, "Default")
        assertEquals("existing-default", installed.id)
        assertTrue(installed.prompts.first { it.identifier == "main" }.forbidOverrides)
        assertFalse(installed.prompts.first { it.identifier == "dialogueExamples" }.enabled)

        val edited = installed.copy(
            temperature = 0.6f,
            prompts = installed.prompts.map { it.copy(content = "我的规则") },
        )
        PresetRepository.save(context, edited)
        PresetRepository.ensureDefaultPreset(context, "Another name")

        val reloaded = PresetRepository.load(context, "Default")
        assertEquals(0.6f, reloaded.temperature, 0.001f)
        assertTrue(reloaded.prompts.all { it.content == "我的规则" })
    }

    @Test
    fun everyCharacterGetsSharedRulesAlongsideItsOwnWorld() = runBlocking {
        val world = WorldBookRepository.create(context, "校园世界")
        WorldBookRepository.saveEntries(context, world.name, listOf(
            WorldBookEntry(0, emptyList(), "图书馆", "图书馆 18:00 关门", constant = true),
        ))
        CharacterRepository.create(context, "林夏")
        CharacterRepository.create(context, "沈言")
        CharacterRepository.updateJson(context, "林夏") {
            it.put("enabled_world_book_ids", JSONArray().put(world.id))
            it.put("system_prompt", "冲突主提示：请写小说")
            it.put("post_history_instructions", "冲突尾部提示：请写动作")
            it.put("mes_example", "冲突示例：她走到你身边")
        }

        val first = PromptContextLoader.load(context, "林夏")
        val second = PromptContextLoader.load(context, "沈言")
        assertTrue(first.failures.isEmpty())
        assertEquals(2, first.worldBooks.size)
        assertEquals(OnlineChatDefaults.WORLD_BOOK_ID, second.worldBooks.single().id)
        assertEquals(first.preset?.id, second.preset?.id)
        assertEquals(listOf(world.id), CharacterRepository.load(context, "林夏").enabledWorldBookIds)

        // 使用真实组装器验证规则、角色信息和世界事实会一起进入模型上下文。
        val built = PromptBuilder.build(
            card = first.card,
            userName = "用户",
            preset = first.preset,
            worldBooks = first.worldBooks,
        )
        val text = (built.preHistory.map { it.content } + built.postHistory.map { it.content } +
            built.depthInjections.map { it.content }).joinToString("\n")
        assertTrue(text.contains("林夏"))
        assertTrue(text.contains("中文活人感"))
        assertTrue(text.contains("图书馆 18:00 关门"))
        assertFalse(text.contains("冲突主提示"))
        assertFalse(text.contains("冲突尾部提示"))
        assertFalse(text.contains("冲突示例"))

        val custom = PresetRepository.create(context, "自选预设")
        CharacterRepository.updateJson(context, "沈言") { it.put("linked_preset_id", custom.id) }
        val withCustom = PromptContextLoader.load(context, "沈言")
        assertEquals(custom.id, withCustom.preset?.id)
        assertEquals(OnlineChatDefaults.WORLD_BOOK_ID, withCustom.worldBooks.single().id)
    }

    @Test
    fun sharedBookRetainsEditsAndDoesNotDuplicateManualAssociation() = runBlocking {
        val first = WorldBookRepository.ensureDefaultBook(context)
        val edited = first.entries.values.map { it.copy(content = "自定义共用规则", enabled = false) }
        WorldBookRepository.saveEntries(context, first.name, edited)
        assertTrue(WorldBookRepository.rename(context, first.name, "我的共用规则"))
        CharacterRepository.create(context, "林夏")
        CharacterRepository.updateJson(context, "林夏") {
            it.put("enabled_world_book_ids", JSONArray().put(first.id))
        }

        val loaded = PromptContextLoader.load(context, "林夏")
        val shared = loaded.worldBooks.single()
        assertEquals(first.id, shared.id)
        assertEquals("我的共用规则", shared.name)
        assertEquals("自定义共用规则", shared.entries.getValue(0).content)
        assertFalse(shared.entries.getValue(0).enabled)
        assertEquals(1, WorldBookRepository.listNames(context).size)
    }

    @Test
    fun existingCustomPresetAndSameNamedWorldBookArePreserved() = runBlocking {
        val title = OnlineChatDefaults.readWorldBook(context).getString("name")
        val userBook = WorldBookRepository.create(context, title)
        val custom = JSONObject().put("momoly_id", "custom-default").put("temperature", 0.3)
        File(PresetRepository.presetsDir(context), "Custom.json").writeText(custom.toString())

        PresetRepository.ensureDefaultPreset(context, "Custom")
        val shared = WorldBookRepository.ensureDefaultBook(context)
        assertEquals(custom.toString(), JSONObject(PresetRepository.exportJsonBytes(context, "Custom").decodeToString()).toString())
        assertEquals(userBook.id, WorldBookRepository.load(context, title).id)
        assertTrue(shared.name != title)
        WorldBookRepository.delete(context, shared.name)
        WorldBookRepository.clear(context)
        assertEquals(listOf(shared.name), WorldBookRepository.listNames(context))
    }
}
