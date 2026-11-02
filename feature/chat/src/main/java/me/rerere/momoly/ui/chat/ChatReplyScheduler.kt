package me.rerere.momoly.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.momoly.domain.ChatReplyText
import me.rerere.momoly.domain.InterruptedReplyDraft
import kotlin.random.Random

/** One owner for input persistence and bubble publication. Network work never holds the gate. */
internal class ChatReplyScheduler<T>(
    private val scope: CoroutineScope,
    private val textOf: (T) -> String,
    private val onFailure: (Throwable) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val gap: (String) -> Long = { ChatReplyText.gapMillis(it, Random.nextLong(500, 1_001)) },
    private val formatDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private class Turn(val sessionId: String, val revision: Long, val acceptedAt: Long) {
        var job: Job? = null
        var partial = ""
        var segments: List<String>? = null
        var sent = 0
    }
    private val gate = Mutex()
    private val drafts = mutableMapOf<String, InterruptedReplyDraft>()
    private var revision = 0L
    private var active: Turn? = null
    var busy by mutableStateOf(false)
        private set

    private fun rememberDraft(turn: Turn?) {
        if (turn == null) return
        val remaining = turn.segments?.drop(turn.sent)?.joinToString("\n") ?: turn.partial
        if (remaining.isNotBlank()) {
            drafts[turn.sessionId] = InterruptedReplyDraft(turn.revision.toString(), remaining.take(16_000))
        }
    }

    fun submit(
        sessionId: String,
        appendInput: suspend () -> Boolean,
        generate: suspend (InterruptedReplyDraft?, (String) -> Unit) -> T?,
        publish: suspend (T, String, Int, Boolean) -> Unit,
        completed: suspend () -> Unit = {},
        waitForInput: Boolean = true,
        splitReply: (T) -> Boolean = { true },
    ) {
        val previous = active
        val turn = Turn(sessionId, ++revision, now())
        active = turn
        busy = true
        previous?.job?.cancel()
        // Saving user input is never cancelled by a subsequent input.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val accepted = gate.withLock {
                    rememberDraft(previous)
                    appendInput()
                }
                if (revision != turn.revision) return@launch
                if (!accepted) { busy = false; return@launch }
                turn.job = scope.launch {
                    try {
                        if (waitForInput) delay((3_000L - (now() - turn.acceptedAt)).coerceAtLeast(0))
                        if (revision != turn.revision) return@launch
                        val draft = gate.withLock { drafts[sessionId] }
                        val result = generate(draft) { text ->
                            if (revision == turn.revision) turn.partial = text
                        } ?: return@launch
                        if (revision != turn.revision) return@launch
                        val segments = withContext(formatDispatcher) {
                            if (splitReply(result)) ChatReplyText.split(textOf(result)) else listOf(textOf(result))
                        }.ifEmpty { listOf(textOf(result)) } // Preserve image-only or punctuation-only replies.
                        turn.segments = segments
                        turn.partial = ""
                        for ((index, text) in segments.withIndex()) {
                            if (index == 0) delay((gap(text) - (now() - turn.acceptedAt)).coerceAtLeast(0))
                            else delay(gap(text))
                            gate.withLock {
                                if (revision != turn.revision) return@launch
                                // A publication admitted before an input finishes atomically before that input.
                                withContext(NonCancellable) {
                                    publish(result, text, index, index == segments.lastIndex)
                                    turn.sent++
                                    drafts.remove(sessionId)
                                }
                            }
                        }
                        if (revision == turn.revision) completed()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        if (revision == turn.revision) onFailure(error)
                    } finally {
                        if (revision == turn.revision) {
                            rememberDraft(turn)
                            busy = false
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (revision == turn.revision) { busy = false; onFailure(error) }
            }
        }
    }

    suspend fun removeSessions(sessionIds: Set<String>, remove: suspend () -> Unit) {
        if (active?.sessionId in sessionIds) {
            val previous = active
            revision++
            active = null
            busy = false
            previous?.job?.cancel()
        }
        // Finish any already admitted input/publication before deleting its history.
        gate.withLock {
            sessionIds.forEach(drafts::remove)
            remove()
        }
    }

    fun stop() {
        val previous = active
        revision++
        active = null
        busy = false
        previous?.job?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { gate.withLock { rememberDraft(previous) } }
    }
}
