package me.rerere.momoly.ui.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rerere.momoly.data.api.*
import org.junit.Assert.*
import org.junit.Test

class ApiConnectionTest {
    @Test fun connectionNormalizesInputsAndOnlyReturnsUniqueChatModels() = runBlocking {
        val runtime = FakeRuntime(listOf(ModelInfo("z"), ModelInfo("a"), ModelInfo("a"),
            ModelInfo("image", type = ModelType.IMAGE), ModelInfo("")))
        val result = connectApi(runtime, " https://example.com/v1/ ", " test-key ", "saved")
        assertEquals("https://example.com/v1", runtime.request!!.baseUrl)
        assertEquals("test-key", runtime.request!!.apiKey)
        assertEquals("openai", runtime.request!!.type)
        assertFalse(runtime.request!!.useResponseApi)
        assertEquals(listOf("a", "z"), result.models.map { it.id })
    }

    @Test fun invalidInputsNeverSendCredentials() = runBlocking {
        val runtime = FakeRuntime(listOf(ModelInfo("chat")))
        listOf("example.com", "file:///tmp/key", "https://secret@example.com", "https://example.com?key=x",
            "https://example.com/v1/chat/completions", "https://example.com/v1/models").forEach {
            try { connectApi(runtime, it, "test-key", "saved"); fail("Accepted $it") }
            catch (e: ApiConnectionException) { assertEquals(ApiConnectionProblem.ADDRESS, e.problem) }
        }
        try { connectApi(runtime, "https://example.com", " ", "saved"); fail() }
        catch (e: ApiConnectionException) { assertEquals(ApiConnectionProblem.KEY, e.problem) }
        assertNull(runtime.request)
    }

    @Test fun emptyListsAndCancellationAreNotReportedAsSuccessfulConnections() = runBlocking {
        try { connectApi(FakeRuntime(emptyList()), "https://example.com", "test-key", "saved"); fail() }
        catch (e: ApiConnectionException) { assertEquals(ApiConnectionProblem.EMPTY, e.problem) }
        val cancelled = FakeRuntime(emptyList()).apply { failure = CancellationException("cancelled") }
        try { connectApi(cancelled, "https://example.com", "test-key", "saved"); fail() }
        catch (_: CancellationException) { }
    }

    @Test fun selectionCommitsOnlyChosenModelAndRetainsOtherCredentialsWithoutActivatingThem() {
        val old = ApiProvider(id = "old", apiKey = "old-key", models = listOf(ModelInfo("old-model")))
        val source = object : ApiConfigDataSource {
            var config = ApiConfig(listOf(old), "old::old-model")
            override fun load() = config
            override fun save(config: ApiConfig) { this.config = config }
            override fun consumeRecoveryNotice() = false
        }
        val controller = ApiConfigController(source)
        val candidate = ApiProvider(id = "new", models = listOf(ModelInfo("a"), ModelInfo("b")))
        try { controller.selectConnectedModel(candidate, "unknown"); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals("old::old-model", source.config.currentModel)
        val saved = controller.selectConnectedModel(candidate, "b")
        assertEquals("new::b", saved.currentModel)
        assertEquals(listOf("b"), saved.providers.last().models.map { it.id })
        assertEquals("old-key", saved.providers.first().apiKey)
        assertFalse(saved.providers.first().enabled)
        assertEquals("b", saved.chatModelSelection()!!.second.id)
    }

    private class FakeRuntime(val result: List<ModelInfo>) : ApiRuntime {
        var request: ApiProvider? = null
        var failure: Exception? = null
        override suspend fun models(provider: ApiProvider): List<ModelInfo> {
            request = provider
            failure?.let { throw it }
            return result
        }
        override suspend fun balance(provider: ApiProvider): String = error("unused")
        override suspend fun testNonStreaming(provider: ApiProvider, modelId: String): String = error("unused")
        override suspend fun testStreaming(provider: ApiProvider, modelId: String, onDelta: (String) -> Unit) = error("unused")
        override suspend fun testToolCall(provider: ApiProvider, modelId: String): ApiToolTestResult = error("unused")
    }
}
