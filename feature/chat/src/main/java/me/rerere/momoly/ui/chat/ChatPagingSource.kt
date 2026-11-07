package me.rerere.momoly.ui.chat

import androidx.paging.PagingData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.domain.chat.ChatDataRepository

private const val CHAT_PAGE_SIZE = 60

internal data class ChatPagingTarget(val sessionId: String, val timestamp: Long, val requestId: Long)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun chatPagingSource(
    repository: ChatDataRepository,
    sessionIds: Flow<String?>,
    targets: Flow<ChatPagingTarget?> = flowOf(null),
    onTargetMissing: suspend (ChatPagingTarget) -> Unit = {},
): Flow<PagingData<ChatMessage>> = combine(sessionIds, targets) { id, target ->
    id to target?.takeIf { it.sessionId == id }
}.flatMapLatest { (id, target) ->
    if (id == null) {
        flowOf(PagingData.empty())
    } else {
        flow {
            emit(PagingData.empty())
            val count = repository.messageCount(id)
            val position = target?.let { repository.messagePosition(id, it.timestamp) }
            if (target != null && position == null) onTargetMissing(target)
            val initialKey = if (position != null) (position - CHAT_PAGE_SIZE / 2).coerceAtLeast(0)
                else (count - CHAT_PAGE_SIZE).takeIf { it > 0 }
            emitAll(repository.messagesPaged(id, initialKey))
        }
    }
}
