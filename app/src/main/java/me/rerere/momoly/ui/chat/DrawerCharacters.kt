package me.rerere.momoly.ui.chat

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.momoly.data.chat.ChatSession
import me.rerere.momoly.domain.chat.ChatDataRepository
import me.rerere.momoly.ui.character.CharacterLibraryController

/** 抽屉角色卡列表的一行：角色信息 + 该角色最近一条聊天的最新消息预览 */
internal data class DrawerCharacter(
    val fileName: String,
    val displayName: String,
    val avatar: Bitmap?,
    val sessionId: String?,
    val lastMessage: String,
    val pinned: Boolean,
    val updatedAt: Long,
)

/**
 * 组装抽屉角色卡列表。应用约定一个角色对应一个聊天入口：
 * 每个角色只取最近一次会话（同角色的旧会话不再单独成行），列表点击即回到该会话。
 */
internal class DrawerCharacterController(
    private val characterLibrary: CharacterLibraryController,
    private val chatRepository: ChatDataRepository,
) {
    suspend fun load(sessions: List<ChatSession>): List<DrawerCharacter> = withContext(Dispatchers.IO) {
        val library = characterLibrary.load()
        // 每个角色取最近一次会话；卡片已删除但聊天还在的会话也会落在这里。
        // charFile 为空的会话没有对应角色卡，不进入角色卡列表。
        val latestSessionByChar = sessions
            .filter { it.charFile.isNotBlank() }
            .groupBy { it.charFile }
            .mapValues { (_, group) -> group.maxByOrNull { it.updatedAt }!! }
        val previews = chatRepository.latestMessagePreviews(
            latestSessionByChar.values.map { it.id },
        )
        fun previewOf(session: ChatSession?): String =
            session?.let { previews[it.id] }?.replace('\n', ' ')?.trim().orEmpty()

        // 角色卡库里的每个角色都有一行，即使还没有聊天（点击后开新会话）
        val libraryRows = library.names.map { fileName ->
            val session = latestSessionByChar[fileName]
            DrawerCharacter(
                fileName = fileName,
                displayName = library.cards[fileName]?.name?.takeIf { it.isNotBlank() } ?: fileName,
                avatar = characterLibrary.thumbnail(fileName),
                sessionId = session?.id,
                lastMessage = previewOf(session),
                pinned = session?.pinned == true,
                updatedAt = session?.updatedAt ?: 0L,
            )
        }
        // 卡片已删除但聊天还在：保留入口，否则这些聊天从抽屉里再也进不去
        val libraryFileNames = library.names.toSet()
        val orphanRows = latestSessionByChar
            .filterKeys { it !in libraryFileNames }
            .map { (fileName, session) ->
                DrawerCharacter(
                    fileName = fileName,
                    displayName = session.charName,
                    avatar = null,
                    sessionId = session.id,
                    lastMessage = previewOf(session),
                    pinned = session.pinned,
                    updatedAt = session.updatedAt,
                )
            }
        // 有聊天的角色按置顶、更新时间排序；没有聊天的角色按角色卡库顺序排在后面
        val withChat = (libraryRows.filter { it.sessionId != null } + orphanRows)
            .sortedWith(
                compareByDescending<DrawerCharacter> { it.pinned }
                    .thenByDescending { it.updatedAt }
            )
        withChat + libraryRows.filter { it.sessionId == null }
    }
}
