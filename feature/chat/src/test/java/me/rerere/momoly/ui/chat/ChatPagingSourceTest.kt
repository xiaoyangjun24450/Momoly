package me.rerere.momoly.ui.chat

import androidx.paging.PagingData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatPagingSourceTest {
    @Test fun searchLoadsContextAndRepeatedSelectionsReloadTheRequestedPage() = runTest {
        val session = ChatSession(id = "chat", messages = (1L..300L).map {
            ChatMessage(role = "assistant", ts = it, isHidden = it == 20L)
        })
        val calls = mutableListOf<Pair<String, Int?>>()
        val repository = object : InMemoryChatRepository(session) {
            override fun messagesPaged(sessionId: String, initialKey: Int?) =
                flowOf(PagingData.empty<ChatMessage>()).also { calls += sessionId to initialKey }
        }
        val selected = MutableStateFlow<String?>("chat")
        val target = MutableStateFlow<ChatPagingTarget?>(ChatPagingTarget("chat", 60, 1))
        val missing = mutableListOf<Long>()
        val job = launch { chatPagingSource(repository, selected, target) { missing += it.timestamp }.collect() }
        runCurrent()
        assertEquals("chat" to 28, calls.last())
        target.value = ChatPagingTarget("chat", 60, 2)
        runCurrent()
        assertEquals(2, calls.size)
        target.value = ChatPagingTarget("chat", 280, 3)
        runCurrent()
        assertEquals("chat" to 248, calls.last())
        target.value = ChatPagingTarget("chat", 20, 4)
        runCurrent()
        assertEquals(listOf(20L), missing)
        assertEquals("chat" to 240, calls.last())
        target.value = ChatPagingTarget("other", 60, 5)
        runCurrent()
        assertEquals("chat" to 240, calls.last())
        job.cancel()
    }
}
