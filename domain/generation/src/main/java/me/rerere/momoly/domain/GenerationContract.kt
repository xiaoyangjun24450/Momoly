package me.rerere.momoly.domain

import me.rerere.momoly.data.api.ApiMessage
import me.rerere.momoly.data.api.ApiRequestSnapshot
import me.rerere.momoly.data.api.ApiToolCall
import me.rerere.momoly.data.api.GenParams
import me.rerere.momoly.data.api.StreamEnd
import me.rerere.momoly.data.api.ToolSpec
import me.rerere.momoly.data.chat.MsgSearch

data class GenerationStreamRequest(
    val providerId: String,
    val modelId: String,
    val messages: List<ApiMessage>,
    val params: GenParams?,
    val tools: List<ToolSpec>,
    val isCancelled: () -> Boolean,
)

sealed interface GenerationEvent {
    data class ContentDelta(val content: String) : GenerationEvent
    data class ReasoningDelta(val reasoning: String) : GenerationEvent
}

class GenerationCancelled : Exception()

class GenerationRequestException(
    val snapshot: ApiRequestSnapshot,
    cause: Exception,
) : Exception(cause.message, cause)

fun interface GenerationGateway {
    suspend fun stream(
        request: GenerationStreamRequest,
        onEvent: (GenerationEvent) -> Unit,
    ): StreamEnd
}

interface GenerationToolExecutor {
    fun describe(call: ApiToolCall): MsgSearch?
    suspend fun execute(call: ApiToolCall): Pair<String, MsgSearch?>
}
