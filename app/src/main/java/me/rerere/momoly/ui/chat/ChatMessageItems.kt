package me.rerere.momoly.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import kotlin.math.roundToInt
import me.rerere.momoly.R
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.MsgFile
import me.rerere.momoly.data.character.CharRegex
import me.rerere.momoly.ui.components.Space4
import me.rerere.momoly.ui.components.Space8
import me.rerere.momoly.ui.components.appClickable
import me.rerere.momoly.ui.components.noRippleClickable
import me.rerere.momoly.ui.theme.WeChatAvatar
import me.rerere.momoly.ui.theme.WeChatBubble
import me.rerere.momoly.ui.theme.WeChatTimeLabel
import me.rerere.momoly.ui.theme.weChatColors
import me.rerere.momoly.ui.theme.weChatMessageTextStyle

/** 微信气泡最宽约占屏宽 72%：再宽读者要横向扫视，阅读反而变累 */
private const val MaxBubbleFraction = 0.72f

/** 把内容的最大宽度限制为父约束的 [fraction]（微信气泡用 [MaxBubbleFraction]） */
private fun Modifier.maxWidthFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val cap = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).roundToInt() else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(maxWidth = cap))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** 字号与行高等比缩放：只缩字号会让行距在放大时被字身挤掉、缩小时反而变松 */
internal fun TextStyle.scaledBy(scale: Float): TextStyle = copy(
    fontSize = fontSize * scale,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
)

/**
 * 用户消息（自己）：右侧绿泡 + 头像。
 * 微信不显示名字/时间戳，操作入口也全部收进长按菜单，所以这里没有常显的复制/重答/更多按钮行。
 */
@Composable
internal fun UserMsg(
    text: String,
    timeLabel: String?,
    onLongPress: () -> Unit,
    images: List<String> = emptyList(),
    files: List<MsgFile> = emptyList(),
    scale: Float = 1.0f,
    avatarBitmap: android.graphics.Bitmap? = null,
    userName: String = "",
    renderPrefs: RenderPrefs = RenderPrefs(),
    replyTo: me.rerere.momoly.data.chat.MessageQuote? = null,
) {
    // 头像兜底要显示首字，没有用户名时用本地化的“用户”，避免气泡换成写死的英文
    val fallbackUserName = stringResource(R.string.role_user)
    // 附件不是气泡：只有这条消息没有正文时，附件块才需要自己承接长按，否则长按菜单点不出来
    val attachmentsMenu =
        if (text.isBlank()) Modifier.appClickable(onClick = {}, onLongClick = onLongPress) else Modifier
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (timeLabel != null) WeChatTimeLabel(timeLabel)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Space8),
            ) {
                if (images.isNotEmpty() || files.isNotEmpty()) {
                    // 微信里图片/文件先于文字单独成块，放气泡上方并与气泡右缘对齐
                    Box(attachmentsMenu) {
                        MessageAttachmentRow(
                            images = images,
                            files = files,
                            modifier = Modifier.maxWidthFraction(MaxBubbleFraction),
                        )
                    }
                }
                if (text.isNotBlank()) {
                    WeChatBubble(
                        fromMe = true,
                        modifier = Modifier.maxWidthFraction(MaxBubbleFraction),
                        onLongPress = onLongPress,
                    ) {
                        // fillWidth=false 让气泡按内容收缩，短消息不会撑成整行宽
                        MessageContent(
                            content = text,
                            isStreaming = false,
                            textStyle = weChatMessageTextStyle(fromMe = true, scale = scale),
                            renderPrefs = renderPrefs,
                            fillWidth = false,
                        )
                    }
                }
                replyTo?.let { ChatQuote(it, Modifier.maxWidthFraction(MaxBubbleFraction)) }
            }
            Spacer(Modifier.width(Space8))
            WeChatAvatar(
                bitmap = avatarBitmap,
                label = userName.ifBlank { fallbackUserName },
            )
        }
    }
}

/**
 * 助手消息（对方）：左侧白泡 + 头像。
 *
 * 思考过程（深度思考/联网搜索时间线）按规格不再显示；生成中的半截正文由 ChatContent 整条过滤，
 * 因此这里只渲染已经结束的消息，没有 isStreaming 参数，也就没有“等待正文”的呼吸点分支。
 * 复制/重答/朗读/更多和模型名/统计全部收进长按菜单，不再常显图标行。
 */
@Composable
internal fun AIMsg(
    msg: ChatMessage,
    timeLabel: String?,
    onLongPress: () -> Unit,
    onPrevAlt: () -> Unit = {},
    onNextAlt: () -> Unit = {},
    scale: Float = 1.0f,
    regexScripts: List<CharRegex> = emptyList(),
    depth: Int? = null,
    userName: String = "",
    charName: String = "",
    charAvatarBitmap: android.graphics.Bitmap? = null,
    renderPrefs: RenderPrefs = RenderPrefs(),
    chatMessagesJson: String = "[]",
    frontendContextJson: () -> String = { "{}" },
    localVariablesJson: String = "{}",
    globalVariablesJson: String = "{}",
    onSetInputText: (String) -> Unit = {},
    onSetChatMessage: (Int, String) -> Unit = { _, _ -> },
    onSelectChatMessageSwipe: (Int, Int) -> Unit = { _, _ -> },
    onReplaceVariables: (String, Map<String, String>) -> Unit = { _, _ -> },
    rpcCall: FrontendRpcCall = { method, _ -> error("Frontend RPC method is unavailable: $method") },
    opening: Boolean = false,
    onLongPressPart: ((String) -> Unit)? = null,
) {
    // 有生成图片时图片就是整条回复（沿用旧行为），此时附件块自己承接长按
    val showText = msg.images.isEmpty()
    val attachmentsMenu =
        if (showText) Modifier else Modifier.appClickable(onClick = {}, onLongClick = onLongPress)
    Column(Modifier.fillMaxWidth()) {
        if (timeLabel != null) WeChatTimeLabel(timeLabel)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            WeChatAvatar(bitmap = charAvatarBitmap, label = charName)
            Spacer(Modifier.width(Space8))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space8),
            ) {
                if (msg.images.isNotEmpty()) {
                    // 生成图片就是回复主体，独立于文字气泡并左对齐；有图片时不再渲染正文（沿用旧行为）
                    Box(attachmentsMenu) {
                        GeneratedImageRow(images = msg.images, aspectRatio = msg.imageAspectRatio)
                    }
                }
                if (msg.files.isNotEmpty()) {
                    Box(attachmentsMenu) {
                        MessageAttachmentRow(images = emptyList(), files = msg.files)
                    }
                }
                // 只停在思考阶段的消息没有正文，空气泡没有意义，直接不渲染
                val parts = remember(msg.content, opening) {
                    if (opening) me.rerere.momoly.domain.ChatReplyText.split(msg.content).ifEmpty { listOf(msg.content) }
                    else listOf(msg.content)
                }
                if (showText && msg.content.isNotBlank()) {
                    for (part in parts) {
                        WeChatBubble(
                            fromMe = false,
                            modifier = Modifier.maxWidthFraction(MaxBubbleFraction),
                            onLongPress = { onLongPressPart?.invoke(part) ?: onLongPress() },
                        ) {
                            MessageContent(
                                content = part,
                                isStreaming = false,
                                textStyle = weChatMessageTextStyle(fromMe = false, scale = scale),
                                regexScripts = regexScripts,
                                depth = depth,
                                userName = userName,
                                charName = charName,
                                renderPrefs = renderPrefs,
                                chatMessagesJson = chatMessagesJson,
                                frontendContextJson = frontendContextJson,
                                localVariablesJson = localVariablesJson,
                                globalVariablesJson = globalVariablesJson,
                                onSetInputText = onSetInputText,
                                onSetChatMessage = onSetChatMessage,
                                onSelectChatMessageSwipe = onSelectChatMessageSwipe,
                                onReplaceVariables = onReplaceVariables,
                                rpcCall = rpcCall,
                                // 微信气泡按内容收缩：不 fillMaxWidth，但保留 Markdown 段落间距
                                fillWidth = false,
                                blockSpacing = 6.dp,
                            )
                        }
                    }
                }
                // 引用来源胶囊卡不是思考过程，保留；聚合本条消息全部搜索调用并按 URL 去重
                val citations = remember(msg.searches) {
                    msg.searches.flatMap { it.items }.distinctBy { it.url }
                }
                if (citations.isNotEmpty()) {
                    var showCitations by rememberSaveable { mutableStateOf(false) }
                    CitationsPill(items = citations, scale = scale) { showCitations = true }
                    if (showCitations) {
                        CitationsSheet(items = citations, scale = scale) { showCitations = false }
                    }
                }
                if (msg.alts.size > 1) {
                    AlternativeSwitcher(
                        msg = msg,
                        scale = scale,
                        onPrev = onPrevAlt,
                        onNext = onNextAlt,
                    )
                }
            }
        }
    }
}

/** 多版本切换：气泡下方一行小号 ‹ 1/2 ›，保持微信式的轻量灰字 */
@Composable
private fun AlternativeSwitcher(
    msg: ChatMessage,
    scale: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val canPrev = msg.altIdx > 0
    val canNext = msg.altIdx < msg.alts.lastIndex
    val tint = weChatColors().secondaryText
    val iconSize = (16f * scale).dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Lucide.ChevronLeft,
            null,
            modifier = Modifier
                .testTag("previous_alternative_${msg.ts}")
                .noRippleClickable(enabled = canPrev) { onPrev() }
                .padding(Space4)
                .size(iconSize),
            tint = tint.copy(alpha = if (canPrev) 1f else 0.35f),
        )
        Text(
            text = "${msg.altIdx + 1}/${msg.alts.size}",
            style = MaterialTheme.typography.labelSmall.scaledBy(scale),
            color = tint,
        )
        Icon(
            Lucide.ChevronRight,
            null,
            modifier = Modifier
                .testTag("next_alternative_${msg.ts}")
                .noRippleClickable(enabled = canNext) { onNext() }
                .padding(Space4)
                .size(iconSize),
            tint = tint.copy(alpha = if (canNext) 1f else 0.35f),
        )
    }
}
