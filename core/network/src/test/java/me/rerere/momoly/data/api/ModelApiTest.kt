package me.rerere.momoly.data.api

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ModelApiTest {
    @Test fun discoveryDoesNotMislabelEmbeddingAudioOrVideoAsChat() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("""{"data":[
                {"id":"doubao-chat","domain":"LLM"},
                {"id":"seed-video","domain":"VideoGeneration"},
                {"id":"seed-image","domain":"ImageGeneration"},
                {"id":"vector","domain":"Embedding"},
                {"id":"text-embedding-3-small"},{"id":"qwen-tts"}
            ]}""").build())
            val result = ModelApi.listModels(ApiProvider(baseUrl = server.url("/v1").toString()))
            assertEquals(listOf("doubao-chat"), result.filter { it.type == ModelType.CHAT }.map { it.id })
        }
    }

    @Test fun openAiDiscoveryUsesBasePathBearerKeyAndReturnedModelIds() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("""{"data":[{"id":"chat-b"},{"id":"chat-a"},{}]}""").build())
            val result = ModelApi.listModels(ApiProvider(baseUrl = server.url("/compatible-mode/v1/").toString(), apiKey = "fake-key"))
            val request = server.takeRequest()
            assertTrue(request.requestLine.startsWith("GET /compatible-mode/v1/models "))
            assertEquals("Bearer fake-key", request.headers["Authorization"])
            assertEquals(listOf("chat-a", "chat-b"), result.map { it.id })
        }
    }
}
