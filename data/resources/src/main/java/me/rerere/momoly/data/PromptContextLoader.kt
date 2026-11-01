package me.rerere.momoly.data

import android.content.Context
import me.rerere.momoly.core.diagnostics.SafeLog
import kotlinx.coroutines.CancellationException
import me.rerere.momoly.data.character.CharacterCard
import me.rerere.momoly.data.character.CharacterRepository
import me.rerere.momoly.data.character.CharRegex
import me.rerere.momoly.data.preset.PresetRepository
import me.rerere.momoly.data.preset.StPreset
import me.rerere.momoly.data.preset.toCharRegex
import me.rerere.momoly.data.regex.RegexSetRepository
import me.rerere.momoly.data.worldbook.WorldBook
import me.rerere.momoly.data.worldbook.WorldBookRepository

/** 一次性加载某个角色生成所需的静态上下文，避免状态分批切换。 */
object PromptContextLoader {
    private const val TAG = "PromptContextLoader"

    enum class ContentType { CHARACTER, WORLD_BOOK, PRESET, REGEX }

    data class LoadFailure(
        val type: ContentType,
        val name: String,
        val error: Exception,
    )

    data class Loaded(
        val charFile: String,
        val card: CharacterCard?,
        val worldBooks: List<WorldBook>,
        val preset: StPreset?,
        val regexSetScripts: List<CharRegex> = emptyList(),
        val failures: List<LoadFailure> = emptyList(),
    )

    suspend fun load(context: Context, charFile: String): Loaded {
        if (charFile.isBlank()) return Loaded(charFile, null, emptyList(), null)
        val failures = mutableListOf<LoadFailure>()
        val card = try {
            CharacterRepository.load(context, charFile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failures += LoadFailure(ContentType.CHARACTER, charFile, error)
            SafeLog.error(TAG, "character_card_load_failed", error)
            null
        }
            ?: return Loaded(charFile, null, emptyList(), null, failures = failures)
        // 全角色共用同一份规则书，不写入角色的世界归属或导出载荷。
        val defaultBook = WorldBookRepository.ensureDefaultBook(context)
        val linkedBooks = card.enabledWorldBookIds
            .filter { it.isNotBlank() }
            .filter { it != defaultBook.id }
            .distinct()
            .mapNotNull { id ->
                try {
                    WorldBookRepository.loadById(context, id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failures += LoadFailure(ContentType.WORLD_BOOK, id, error)
                    SafeLog.error(TAG, "world_book_load_failed", error)
                    null
                }
            }
        val books = listOf(defaultBook) + linkedBooks
        val preset = try {
            // 没有关联预设的已有角色也使用默认聊天方式；自选预设继续按原关联读取。
            if (card.linkedPresetId.isBlank()) {
                val name = PresetRepository.ensureDefaultPreset(context)
                PresetRepository.load(context, name)
            } else {
                PresetRepository.loadById(context, card.linkedPresetId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failures += LoadFailure(ContentType.PRESET, card.linkedPresetId, error)
            SafeLog.error(TAG, "preset_load_failed", error)
            null
        }
        // 关联的正则集。global=true 的集走全局路径（ChatViewModel 单独加载并对所有聊天生效），
        // 这里跳过，否则两条路径都收一份、同一条脚本会套两遍
        val regexSetScripts = card.enabledRegexIds
            .filter { it.isNotBlank() }
            .distinct()
            .flatMap { setId ->
                try {
                    RegexSetRepository.loadById(context, setId)
                        .takeIf { !it.global }
                        ?.scripts?.map { it.toCharRegex() }
                        .orEmpty()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failures += LoadFailure(ContentType.REGEX, setId, error)
                    SafeLog.error(TAG, "regex_set_load_failed", error)
                    emptyList()
                }
            }
        return Loaded(charFile, card, books, preset, regexSetScripts, failures)
    }
}
