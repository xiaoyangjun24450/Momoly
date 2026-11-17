package me.rerere.momoly.ui.api

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import me.rerere.momoly.data.api.*
import me.rerere.momoly.ui.theme.MomolyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.TimeoutRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
@OptIn(ExperimentalTestApi::class)
class ApiConnectionPageTest {
    @get:Rule(order = 0) val timeout = TimeoutRule.seconds(90)
    @get:Rule(order = 1) val compose = createComposeRule()
    private val source = MemorySource()
    private val runtime = FakeRuntime()

    private fun open() {
        compose.setContent { MomolyTheme { ApiConnectionPage(ApiConfigController(source), runtime, {}) } }
    }

    @Test fun defaultConnectionOpensSearchableModelsAndSavesOnlyAfterSelection() {
        source.config = ApiConfig(listOf(ApiProvider(name = "OpenAI", baseUrl = "https://api.openai.com/v1", enabled = false)))
        open()
        compose.onNodeWithTag("api_address").assertTextContains(ApiService.DEEPSEEK.baseUrl)
        compose.onNodeWithTag("api_connect").assertIsNotEnabled()
        compose.onNodeWithTag("api_key").performTextInput("test-key")
        compose.onNodeWithTag("api_connect").performScrollTo().performClick()
        compose.onNodeWithTag("api_model_search").assertIsDisplayed().performTextInput("beta")
        compose.onNodeWithTag("api_model_alpha").assertDoesNotExist()
        assertEquals("", source.config.currentModel)
        compose.onNodeWithTag("api_model_beta").performClick()
        compose.runOnIdle {
            assertEquals("beta", source.config.currentModel.substringAfter("::"))
            val active = source.config.providers.single { it.enabled }
            assertEquals("https://api.deepseek.com", active.baseUrl)
            assertEquals("test-key", active.apiKey)
            assertEquals(listOf("beta"), active.models.map { it.id })
        }
        compose.onNodeWithTag("api_model_search").assertDoesNotExist()
    }

    @Test fun pendingConnectionCannotDuplicateAndFailureKeepsSavedModel() {
        source.config = ApiConfig(listOf(ApiProvider(id = "old", baseUrl = "https://example.com/v1",
            apiKey = "old-key", models = listOf(ModelInfo("saved")))), "old::saved")
        val original = source.config
        runtime.pending = CompletableDeferred()
        open()
        compose.onNodeWithTag("api_connect").performScrollTo().performClick()
        compose.onNodeWithTag("api_connect").assertIsNotEnabled()
        compose.onNodeWithTag("api_address").assertIsNotEnabled()
        compose.runOnIdle { runtime.pending!!.completeExceptionally(IllegalStateException("HTTP 401: sensitive body")) }
        compose.waitForIdle()
        compose.onNodeWithTag("api_model_search").assertDoesNotExist()
        compose.onNodeWithText("Authentication or access failed. Check that the key matches the service and region.").assertExists()
        compose.runOnIdle { assertEquals(original, source.config); assertEquals(1, runtime.calls) }
    }

    @Test fun presetChangeClearsCredentialsAndKeepsTwoEditableFields() {
        open()
        compose.onNodeWithTag("api_key").performTextInput("deepseek-key")
        compose.onNodeWithTag("api_presets", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Doubao").performClick()
        compose.onNodeWithTag("api_address").assertTextContains(ApiService.DOUBAO.baseUrl)
        compose.onNodeWithTag("api_connect").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        assertEquals("", source.config.currentModel)
    }

    private class MemorySource : ApiConfigDataSource {
        var config = ApiConfig()
        override fun load() = config
        override fun save(config: ApiConfig) { this.config = config }
        override fun consumeRecoveryNotice() = false
    }
    private class FakeRuntime : ApiRuntime {
        var calls = 0
        var pending: CompletableDeferred<List<ModelInfo>>? = null
        override suspend fun models(provider: ApiProvider): List<ModelInfo> {
            calls++
            return pending?.await() ?: listOf(ModelInfo("alpha"), ModelInfo("beta"))
        }
        override suspend fun balance(provider: ApiProvider): String = error("unused")
        override suspend fun testNonStreaming(provider: ApiProvider, modelId: String): String = error("unused")
        override suspend fun testStreaming(provider: ApiProvider, modelId: String, onDelta: (String) -> Unit) = error("unused")
        override suspend fun testToolCall(provider: ApiProvider, modelId: String): ApiToolTestResult = error("unused")
    }
}
