package me.rerere.momoly.ui.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import me.rerere.momoly.domain.InterruptedReplyDraft
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatReplySchedulerTest {
    @Test fun resetWaitsForAdmittedPublicationThenDeletesHistoryAndDraft() = runTest {
        val release = CompletableDeferred<Unit>()
        val rows = mutableListOf<String>()
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("s", { rows += "user"; true }, { _, _ -> "已经开始保存的气泡。还没发送的草稿" }, { _, text, _, _ ->
            release.await(); rows += text
        })
        advanceTimeBy(3_000); runCurrent()
        val removal = backgroundScope.launch { scheduler.removeSessions(setOf("s")) { rows.clear() } }
        runCurrent()
        assertFalse(removal.isCompleted)
        release.complete(Unit); runCurrent()
        assertTrue(removal.isCompleted)
        advanceTimeBy(10_000); runCurrent()
        assertTrue(rows.isEmpty())
        scheduler.submit("s", { true }, { draft, _ -> assertNull(draft); "全新的回复" }, { _, text, _, _ -> rows += text })
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf("全新的回复"), rows)
    }

    @Test fun removingOtherContactDoesNotStopCurrentReply() = runTest {
        val rows = mutableListOf<String>()
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("a", { true }, { _, _ -> "正在回复的联系人" }, { _, text, _, _ -> rows += text })
        scheduler.removeSessions(setOf("b")) {}
        assertTrue(scheduler.busy)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf("正在回复的联系人"), rows)
    }

    @Test fun stoppingQueuedReplyKeepsUnsentDraftForNextInput() = runTest {
        val outputs = mutableListOf<String>()
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("s", { true }, { _, _ -> "已经送达的第一句。还没发送的第二句" }, { _, text, _, _ -> outputs += text })
        advanceTimeBy(3_000); runCurrent()
        scheduler.stop()
        advanceTimeBy(10_000); runCurrent()
        assertFalse(scheduler.busy)
        assertEquals(listOf("已经送达的第一句"), outputs)
        scheduler.submit("s", { true }, { draft, _ ->
            assertEquals("还没发送的第二句", draft?.text)
            "..."
        }, { _, text, _, _ -> outputs += text })
        advanceTimeBy(3_000); runCurrent()
        assertEquals("...", outputs.last())
    }

    @Test fun replacingSelectedVersionPublishesOnlyOneMessage() = runTest {
        val outputs = mutableListOf<String>()
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("s", { true }, { _, _ -> "替换这条消息。仍然属于同一版本" },
            { _, text, _, _ -> outputs += text }, waitForInput = false, splitReply = { false })
        advanceTimeBy(1_500); runCurrent()
        assertEquals(listOf("替换这条消息。仍然属于同一版本"), outputs)
        assertFalse(scheduler.busy)
    }

    @Test fun waitsThreeSecondsFromLastInputAndPersistsEveryInput() = runTest {
        val inputs = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        var requests = 0
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        fun send(text: String) = scheduler.submit("s", { inputs += text; true }, { _, _ -> requests++; "好的给你带常温水" }, { _, text, _, _ -> outputs += text })
        send("换成水")
        advanceTimeBy(2_000); runCurrent()
        send("要常温的")
        advanceTimeBy(2_999); runCurrent()
        assertEquals(0, requests)
        assertEquals(listOf("换成水", "要常温的"), inputs)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, requests)
        assertEquals(listOf("好的给你带常温水"), outputs)
        assertFalse(scheduler.busy)
    }

    @Test fun interruptionCarriesOnlyUnsentTailAndNeverAutomaticallyReplaysIt() = runTest {
        val outputs = mutableListOf<String>()
        var draft: InterruptedReplyDraft? = null
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("s", { true }, { _, _ -> "我出门了。给你买咖啡。还是老样子吗？" }, { _, text, _, _ -> outputs += text })
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf("我出门了"), outputs)
        scheduler.submit("s", { true }, { pending, _ -> draft = pending; "好的改成常温水" }, { _, text, _, _ -> outputs += text })
        advanceTimeBy(3_000); runCurrent()
        assertEquals("给你买咖啡\n还是老样子吗", draft?.text)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf("我出门了", "好的改成常温水"), outputs)
    }

    @Test fun uncooperativeOldResponseCannotPublishOrClearNewBusyState() = runTest {
        val release = CompletableDeferred<Unit>()
        val outputs = mutableListOf<String>()
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("s", { true }, { _, partial ->
            partial("尚未发出的旧文字")
            withContext(NonCancellable) { release.await() }
            "不该出现的迟到回复"
        }, { _, text, _, _ -> outputs += text })
        advanceTimeBy(3_000); runCurrent()
        scheduler.submit("s", { true }, { draft, _ ->
            assertEquals("尚未发出的旧文字", draft?.text)
            "新的有效回复"
        }, { _, text, _, _ -> outputs += text })
        release.complete(Unit); runCurrent()
        assertTrue(scheduler.busy)
        assertTrue(outputs.isEmpty())
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf("新的有效回复"), outputs)
    }

    @Test fun admittedPublicationFinishesBeforeInputAndIsNotIncludedInDraft() = runTest {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var draft: InterruptedReplyDraft? = null
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, { throw it }, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("s", { true }, { _, _ -> "第一句已送达。第二句未发送" }, { _, text, _, _ ->
            release.await(); events += text
        })
        advanceTimeBy(3_000); runCurrent()
        scheduler.submit("s", { events += "用户新消息"; true }, { pending, _ -> draft = pending; "新的回复内容" }, { _, text, _, _ -> events += text })
        release.complete(Unit); runCurrent()
        assertEquals(listOf("第一句已送达", "用户新消息"), events)
        advanceTimeBy(3_000); runCurrent()
        assertEquals("第二句未发送", draft?.text)
    }

    @Test fun failedReplacementRetainsDraftAndAnotherSessionCannotReadIt() = runTest {
        val errors = mutableListOf<Throwable>()
        val scheduler = ChatReplyScheduler<String>(backgroundScope, { it }, errors::add, { testScheduler.currentTime }, { 1_500 }, StandardTestDispatcher(testScheduler))
        scheduler.submit("a", { true }, { _, partial -> partial("旧的未发内容"); throw IllegalStateException("offline") }, { _, _, _, _ -> })
        advanceTimeBy(3_000); runCurrent()
        assertEquals(1, errors.size)
        scheduler.submit("b", { true }, { draft, _ -> assertNull(draft); "别的角色回复" }, { _, _, _, _ -> })
        advanceTimeBy(3_000); runCurrent()
        scheduler.submit("a", { true }, { draft, _ -> assertEquals("旧的未发内容", draft?.text); "重试后的回复" }, { _, _, _, _ -> })
        advanceTimeBy(3_000); runCurrent()
        assertFalse(scheduler.busy)
    }
}
