package me.rerere.momoly.data.generation

import me.rerere.momoly.data.api.ApiConfigRepository
import me.rerere.momoly.data.api.ApiRequestException
import me.rerere.momoly.data.api.ChatApi
import me.rerere.momoly.data.api.ApiProvider
import me.rerere.momoly.data.api.ModelType
import me.rerere.momoly.data.api.Modality
import me.rerere.momoly.data.api.StreamEnd
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import me.rerere.momoly.domain.GenerationCancelled
import me.rerere.momoly.domain.GenerationEvent
import me.rerere.momoly.domain.GenerationGateway
import me.rerere.momoly.domain.GenerationRequestException
import me.rerere.momoly.domain.GenerationStreamRequest

/** Maps the provider-neutral generation contract to the existing network adapters. */
class NetworkGenerationGateway(
    private val apiConfigRepository: ApiConfigRepository,
    private val streamChat: suspend (ApiProvider, String, GenerationStreamRequest, (String, String) -> Unit) -> StreamEnd =
        { provider, modelId, request, onDelta ->
            ChatApi.streamChat(provider, modelId, request.messages, request.params, request.tools,
                request.isCancelled, onDelta)
        },
) : GenerationGateway {
    // Only process-local, short-lived failure memory; editing a provider makes it a new key.
    private val failedUntil = ConcurrentHashMap<Pair<ApiProvider, String>, Long>()

    override suspend fun stream(
        request: GenerationStreamRequest,
        onEvent: (GenerationEvent) -> Unit,
    ) = try {
        streamAvailable(request, onEvent)
    } catch (_: ChatApi.Stopped) {
        throw GenerationCancelled()
    } catch (error: ApiRequestException) {
        throw GenerationRequestException(
            snapshot = error.snapshot,
            cause = error.cause as? Exception ?: error,
        )
    }

    private suspend fun streamAvailable(
        request: GenerationStreamRequest,
        onEvent: (GenerationEvent) -> Unit,
    ): StreamEnd {
        val config = apiConfigRepository.load()
        val requested = config.providers.find { it.id == request.providerId }
        val taskType = requested?.model(request.modelId)?.type ?: ModelType.CHAT
        val needsImages = request.messages.any { it.images.isNotEmpty() }
        val candidates = config.providers.filter { it.enabled }.flatMap { provider ->
            provider.models.filter { model ->
                model.id.isNotBlank() && model.type == taskType &&
                    (taskType != ModelType.CHAT || provider.type != "gradio") &&
                    (request.allowFallback || (provider.id == request.providerId && model.id == request.modelId))
            }.map { provider to it.id }
        }.distinctBy { (provider, modelId) -> provider.id to modelId }
        check(candidates.isNotEmpty()) { "没有已启用且适合本次请求的模型，请在模型设置中配置并启用。" }
        val now = System.currentTimeMillis()
        failedUntil.entries.removeIf { it.value <= now || it.key !in candidates }
        val ordered = candidates.sortedWith(
            compareBy<Pair<ApiProvider, String>> { request.allowFallback && (failedUntil[it] ?: 0L) > now }
                // Capability labels are editable hints, not a reliable deny-list.
                .thenBy { (provider, modelId) -> needsImages && Modality.IMAGE !in provider.model(modelId)!!.inputModalities }
                .thenBy { (provider, modelId) -> provider.id != request.providerId || modelId != request.modelId },
        )
        var lastFailure: Exception? = null
        for ((provider, modelId) in ordered) {
            if (request.isCancelled()) throw GenerationCancelled()
            var emitted = false
            var selected = false
            fun announceModel() {
                if (!selected) {
                    selected = true
                    onEvent(GenerationEvent.ModelSelected(provider.id, modelId))
                }
            }
            try {
                val end = streamChat(provider, modelId, request) { content, reasoning ->
                    if (request.isCancelled()) throw GenerationCancelled()
                    if (content.isNotEmpty() || reasoning.isNotEmpty()) {
                        emitted = true
                        announceModel()
                    }
                    if (content.isNotEmpty()) onEvent(GenerationEvent.ContentDelta(content))
                    if (reasoning.isNotEmpty()) onEvent(GenerationEvent.ReasoningDelta(reasoning))
                }
                if (request.isCancelled()) throw GenerationCancelled()
                check(emitted || end.toolCalls.isNotEmpty() || end.generatedImages.isNotEmpty()) {
                    "模型返回了空结果"
                }
                announceModel()
                failedUntil.remove(provider to modelId)
                return end
            } catch (error: Exception) {
                if (error is CancellationException || error is GenerationCancelled || error is ChatApi.Stopped) throw error
                val cause = (error as? ApiRequestException)?.cause
                if (cause is CancellationException || cause is GenerationCancelled || cause is ChatApi.Stopped) throw cause
                if (request.isCancelled()) throw GenerationCancelled()
                // Never splice two models' replies or replay an ongoing tool round.
                if (emitted || !request.allowFallback) throw error
                if (error !is ApiRequestException && error !is java.io.IOException && error !is IllegalStateException) throw error
                failedUntil[provider to modelId] = System.currentTimeMillis() + 60_000L
                lastFailure = error
            }
        }
        val failure = checkNotNull(lastFailure)
        if (needsImages) {
            val message = "图片已保存在聊天中，但已启用模型未能完成图片请求。请检查识图模型和接口配置。最后错误：${failure.message.orEmpty()}"
            if (failure is ApiRequestException) throw ApiRequestException(failure.snapshot, IllegalStateException(message, failure))
            throw IllegalStateException(message, failure)
        }
        throw failure
    }
}
