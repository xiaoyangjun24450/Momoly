package me.rerere.momoly.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import me.rerere.momoly.data.api.ReasoningLevel
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.settings.ThemeMode
import me.rerere.momoly.ui.theme.MomolyTheme
import me.rerere.momoly.ui.theme.ProvideWeChatColors
import me.rerere.momoly.ui.theme.WeChatTimeLabel
import me.rerere.momoly.ui.theme.weChatColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 无设备/模拟器时的外观验证：用 Robolectric 原生图形把聊天页关键组件绘制到软件画布，
 * 输出 PNG 到 app/build/screenshots/，供人工与 Lead 检查微信样式（颜色、气泡、顶栏、输入栏）。
 *
 * 不能用 compose 测试规则里的 captureToImage：它走 PixelCopy 等真实窗口绘制回调，
 * Robolectric 里永远等不到，只能在测试里手动 measure/layout 后 view.draw 到 Bitmap。
 * 中文字形在 Robolectric 字体下可能缺失，主要看布局与配色。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeChatUiScreenshotTest {
    private val outputDir = File("build/screenshots")

    @Test
    fun captureChatPage() {
        capture("wechat-chat-light.png", ThemeMode.LIGHT, typing = false)
    }

    @Test
    fun captureTypingIndicator() {
        capture("wechat-typing-light.png", ThemeMode.LIGHT, typing = true)
    }

    @Test
    fun captureChatPageDark() {
        capture("wechat-chat-dark.png", ThemeMode.DARK, typing = false)
    }

    private fun capture(name: String, themeMode: ThemeMode, typing: Boolean) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val content = ComposeView(activity).apply {
            setContent { SampleChatPage(themeMode = themeMode, typing = typing) }
        }
        activity.setContentView(content)
        shadowOf(Looper.getMainLooper()).idle()

        val width = 1080
        val height = 1920
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        content.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        outputDir.mkdirs()
        FileOutputStream(File(outputDir, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Composable
    private fun SampleChatPage(themeMode: ThemeMode, typing: Boolean) {
        MomolyTheme(themeMode = themeMode) {
            ProvideWeChatColors {
                Surface(Modifier.fillMaxSize(), color = weChatColors().chatBackground) {
                    Column(Modifier.fillMaxSize()) {
                        ChatTopBar(
                            title = "林夏",
                            typing = typing,
                            onBack = {},
                            onSearch = {},
                        )
                        Column(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WeChatTimeLabel("20:52")
                            UserMsg(
                                text = "今天有点累",
                                timeLabel = null,
                                onLongPress = {},
                                userName = "user",
                            )
                            AIMsg(
                                msg = ChatMessage(
                                    role = "assistant",
                                    ts = 2L,
                                    content = "今天怎么啦，事情很多吗？",
                                ),
                                timeLabel = null,
                                onLongPress = {},
                                charName = "林夏",
                            )
                            UserMsg(
                                text = "嗯，开了一天的会",
                                timeLabel = null,
                                onLongPress = {},
                                userName = "user",
                            )
                            AIMsg(
                                msg = ChatMessage(
                                    role = "assistant",
                                    ts = 3L,
                                    content = "那你先歇会儿吧，晚点想聊再找我。",
                                ),
                                timeLabel = null,
                                onLongPress = {},
                                charName = "林夏",
                            )
                        }
                        ChatBottomArea(
                            state = rememberTextFieldState("要不要去图书馆见？"),
                            attachments = emptyList(),
                            onRemoveAttachment = {},
                            showAttachment = false,
                            onToggleAttachment = {},
                            onSend = {},
                            currentModelId = "deepseek",
                            reasoning = ReasoningLevel.AUTO,
                            generating = typing,
                            onStop = {},
                        )
                    }
                }
            }
        }
    }
}
