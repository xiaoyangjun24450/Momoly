package me.rerere.momoly.data.generation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rerere.momoly.data.api.*
import me.rerere.momoly.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONObject

class NetworkGenerationGatewayTest {
    @Test
    fun actualHttpRequestContainsImage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream")
                .body("data: {\"choices\":[{\"delta\":{\"content\":\"看到了图片\"}}]}\n\ndata: [DONE]\n\n").build())
            val configured = provider("first").copy(baseUrl = server.url("/v1").toString())
            val gateway = NetworkGenerationGateway(repository(configured))
            val messages = listOf(ApiMessage("user", "看看图片", images = listOf(ApiImage("image/jpeg", "/9j/AA=="))))
            val events = mutableListOf<GenerationEvent>()
            gateway.stream(request().copy(messages = messages), events::add)
            val sent = server.takeRequest()
            val json = JSONObject(requireNotNull(sent.body).utf8())
            val user = json.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            assertEquals("看看图片", user.getJSONObject(0).getString("text"))
            assertEquals("data:image/jpeg;base64,/9j/AA==", user.getJSONObject(1).getJSONObject("image_url").getString("url"))
            assertTrue(events.contains(GenerationEvent.ContentDelta("看到了图片")))
        } finally { server.close() }
    }

    private fun provider(id: String, type: ModelType = ModelType.CHAT, enabled: Boolean = true) =
        ApiProvider(id = id, enabled = enabled, models = listOf(ModelInfo(id = id, type = type)))

    private fun repository(vararg providers: ApiProvider) = object : ApiConfigRepository {
        override fun load() = ApiConfig(providers.toList())
        override fun save(config: ApiConfig) = Unit
    }

    private fun request(allowFallback: Boolean = true, cancelled: () -> Boolean = { false }) =
        GenerationStreamRequest("first", "first", listOf(ApiMessage("user", "hello")),
            null, emptyList(), cancelled, allowFallback)

    @Test
    fun quotaFailureSelectsEnabledChatModelAndTemporarilyDeprioritizesFailure() = runBlocking {
        val attempts = mutableListOf<String>()
        val events = mutableListOf<GenerationEvent>()
        val gateway = NetworkGenerationGateway(repository(
            provider("image", ModelType.IMAGE), provider("disabled", enabled = false),
            provider("first"), provider("second"),
        )) { provider, _, _, delta ->
            attempts += provider.id
            if (provider.id == "first") throw ApiRequestException(ApiRequestSnapshot(),
                IllegalStateException("You have exceeded your ZeroGPU runs limit"))
            delta("answer", "")
            StreamEnd()
        }
        gateway.stream(request(), events::add)
        assertEquals(listOf("first", "second"), attempts)
        assertEquals(GenerationEvent.ModelSelected("second", "second"), events.first())
        attempts.clear()
        gateway.stream(request(), {})
        assertEquals(listOf("second"), attempts)
    }

    @Test
    fun partialContentAndReasoningFailuresNeverSwitchModels() = runBlocking {
        for (reasoning in listOf(false, true)) {
            val attempts = mutableListOf<String>()
            val gateway = NetworkGenerationGateway(repository(provider("first"), provider("second"))) { p, _, _, delta ->
                attempts += p.id
                if (reasoning) delta("", "thinking") else delta("partial", "")
                throw IOException("connection lost")
            }
            val failure = runCatching { gateway.stream(request(), {}) }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertEquals(listOf("first"), attempts)
        }
    }

    @Test
    fun cancellationAndStopNeverTryAnotherModel() = runBlocking {
        for (error in listOf(CancellationException("cancelled"), ChatApi.Stopped(), GenerationCancelled(),
            ApiRequestException(ApiRequestSnapshot(), CancellationException("wrapped cancellation")))) {
            val attempts = mutableListOf<String>()
            val gateway = NetworkGenerationGateway(repository(provider("first"), provider("second"))) { p, _, _, _ ->
                attempts += p.id
                throw error
            }
            val failure = runCatching { gateway.stream(request(), {}) }.exceptionOrNull()
            assertTrue(failure is CancellationException || failure is GenerationCancelled)
            assertEquals(listOf("first"), attempts)
        }
        var calls = 0
        val gateway = NetworkGenerationGateway(repository(provider("first"))) { _, _, _, _ ->
            calls++; StreamEnd()
        }
        assertTrue(runCatching { gateway.stream(request(cancelled = { true }), {}) }.exceptionOrNull() is GenerationCancelled)
        assertEquals(0, calls)
    }

    @Test
    fun allUnavailableTriesEachOnceAndSurfacesLastFailure() = runBlocking {
        val attempts = mutableListOf<String>()
        val gateway = NetworkGenerationGateway(repository(provider("first"), provider("second"))) { p, _, _, _ ->
            attempts += p.id
            throw IOException("unavailable ${p.id}")
        }
        val failure = runCatching { gateway.stream(request(), {}) }.exceptionOrNull()
        assertEquals(listOf("first", "second"), attempts)
        assertEquals("unavailable second", failure?.message)
    }

    @Test
    fun emptyResultFallsBackAndToolContinuationRemainsPinned() = runBlocking {
        val attempts = mutableListOf<String>()
        val gateway = NetworkGenerationGateway(repository(provider("first"), provider("second"))) { p, _, _, delta ->
            attempts += p.id
            if (p.id == "second") delta("answer", "")
            StreamEnd()
        }
        gateway.stream(request(), {})
        assertEquals(listOf("first", "second"), attempts)
        attempts.clear()
        assertNotNull(runCatching { gateway.stream(request(allowFallback = false), {}) }.exceptionOrNull())
        assertEquals(listOf("first"), attempts)
    }

    @Test
    fun imageGenerationNeverFallsBackToChatAndDisabledSourceIsNotCalled() = runBlocking {
        val attempts = mutableListOf<String>()
        val gateway = NetworkGenerationGateway(repository(
            provider("first", ModelType.IMAGE, enabled = false), provider("chat"), provider("image", ModelType.IMAGE),
        )) { p, _, _, _ ->
            attempts += p.id
            StreamEnd(generatedImages = listOf(GeneratedImage(byteArrayOf(1))))
        }
        gateway.stream(request(), {})
        assertEquals(listOf("image"), attempts)
    }

    @Test
    fun imageAttachmentsPreferModelsWithImageInput() = runBlocking {
        val attempts = mutableListOf<String>()
        val vision = provider("vision").copy(models = listOf(ModelInfo(id = "vision",
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE))))
        val gateway = NetworkGenerationGateway(repository(provider("first"), vision)) { p, _, _, delta ->
            attempts += p.id
            delta("image description", "")
            StreamEnd()
        }
        gateway.stream(request().copy(messages = listOf(ApiMessage("user", "describe",
            images = listOf(ApiImage("image/png", "test"))))), {})
        assertEquals(listOf("vision"), attempts)
    }

    @Test
    fun imageOnlyRequestIsSentEvenWhenCapabilityLabelIsMissing() = runBlocking {
        val image = ApiImage("image/jpeg", "base64-image")
        var received: GenerationStreamRequest? = null
        val gateway = NetworkGenerationGateway(repository(provider("first"))) { _, _, request, delta ->
            received = request
            delta("我看见一只猫", "")
            StreamEnd()
        }
        gateway.stream(request().copy(messages = listOf(ApiMessage("user", "", images = listOf(image)))), {})
        assertEquals(listOf(image), received!!.messages.single().images)
        assertEquals("", received.messages.single().content)
    }

    @Test
    fun failedVisionModelCanFallBackToUnlabelledModelWithoutDroppingImage() = runBlocking {
        val attempts = mutableListOf<String>()
        val vision = provider("vision").copy(models = listOf(ModelInfo(id = "vision",
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE))))
        val gateway = NetworkGenerationGateway(repository(provider("first"), vision)) { p, _, request, delta ->
            attempts += p.id
            assertEquals("image", request.messages.single().images.single().base64)
            if (p.id == "vision") throw IOException("quota exhausted")
            delta("seen", "")
            StreamEnd()
        }
        gateway.stream(request().copy(messages = listOf(ApiMessage("user", "", images = listOf(ApiImage("image/png", "image"))))), {})
        assertEquals(listOf("vision", "first"), attempts)
    }
}
