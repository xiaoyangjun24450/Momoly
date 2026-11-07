package me.rerere.momoly.ui.chat

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import me.rerere.momoly.R
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.ui.theme.ProvideWeChatColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.TimeoutRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalTestApi::class)
class QuotedReplyUiTest {
    @get:Rule(order = 0) val timeout = TimeoutRule.seconds(90)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun menuExposesCopySelectCopyAndReply() {
        var replied = false
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent {
            MessageMenu({}, {}, {}, { replied = true })
        }
        compose.onNodeWithText(context.getString(R.string.copy)).assertExists()
        compose.onNodeWithText(context.getString(R.string.select_copy)).assertExists()
        compose.onNodeWithText("重新生成").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.edit)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.delete_current_version)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.reply_message)).performClick()
        assertTrue(replied)
    }

    @Test fun openingHasSeparateBubblesAndLongPressSelectsOnlyThatSentence() {
        var selected = ""
        compose.setContent {
            ProvideWeChatColors {
                AIMsg(ChatMessage("assistant", "你好呀，我正在准备出门。今天想聊些什么？"), null, {},
                    opening = true, onLongPressPart = { selected = it })
            }
        }
        compose.onNodeWithText("你好呀").assertExists()
        compose.onNodeWithText("今天想聊些什么").assertExists()
        compose.onNodeWithText("我正在准备出门").performTouchInput { longClick() }
        assertEquals("我正在准备出门", selected)
    }
}
