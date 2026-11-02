package me.rerere.momoly.ui.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.TimeoutRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalTestApi::class)
class MessageFrontendContextTest {
    @get:Rule(order = 0) val timeout = TimeoutRule.seconds(90)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun textAndMarkdownNeverBuildWebContextEvenOnRecomposition() {
        val size = mutableStateOf(16.sp)
        val context: () -> String = { error("Text messages must not serialize web context") }
        compose.setContent {
            Column {
                MessageContent(
                    "普通文字消息", false, TextStyle(fontSize = size.value),
                    frontendContextJson = context,
                )
                MessageContent(
                    "**bold** and [link](https://example.com)", false, TextStyle(fontSize = size.value),
                    frontendContextJson = context,
                )
                MessageContent(
                    "<div>shown as source</div>", false, TextStyle(fontSize = size.value),
                    renderPrefs = RenderPrefs(markdown = false, htmlCss = false),
                    frontendContextJson = context,
                )
            }
        }
        compose.runOnIdle { size.value = 18.sp }
        compose.waitForIdle()
    }

    @Test
    fun htmlReusesContextButRefreshesWhenItsSourceChanges() {
        var calls = 0
        val size = mutableStateOf(16.sp)
        val source = mutableStateOf<() -> String>({ calls++; "{\"userName\":\"before\"}" })
        compose.setContent {
            MessageContent(
                "<div>Hello {{user}}</div>", false, TextStyle(fontSize = size.value),
                frontendContextJson = source.value,
            )
        }
        compose.runOnIdle {
            assertEquals(1, calls)
            size.value = 18.sp
        }
        compose.runOnIdle {
            assertEquals(1, calls)
            source.value = { calls++; "{\"userName\":\"after\"}" }
        }
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test
    fun markdownChangingToHtmlBuildsContextOnlyWhenNeeded() {
        var calls = 0
        val content = mutableStateOf("**plain markdown**")
        val source: () -> String = { calls++; "{}" }
        compose.setContent {
            MessageContent(content.value, false, TextStyle(fontSize = 16.sp), frontendContextJson = source)
        }
        compose.runOnIdle {
            assertEquals(0, calls)
            content.value = "<div>HTML reply</div>"
        }
        compose.runOnIdle { assertEquals(1, calls) }
    }
}
