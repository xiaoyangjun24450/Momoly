package me.rerere.momoly.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleStop
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.UserPen
import com.composables.icons.lucide.Volume2
import kotlin.math.roundToInt
import me.rerere.momoly.R
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.chat.MsgFile
import me.rerere.momoly.data.character.CharRegex
import me.rerere.momoly.ui.components.Space8
import me.rerere.momoly.ui.components.Space12
import me.rerere.momoly.ui.components.noRippleClickable

/** 把内容的最大宽度限制为父约束的 [fraction]（用户气泡不超过行宽的 ~80%） */
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

/** 圆形消息头像：有自定义图则显示图，否则用户侧回退 UserPen 图标。（AI 侧无图时不显示） */
@Composable
private fun MessageAvatar(
    bitmap: android.graphics.Bitmap?,
    size: Dp,
    fallbackIcon: Boolean,
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.character_avatar),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (fallbackIcon) {
            Icon(
                Lucide.UserPen,
                null,
                Modifier.size(size * 0.75f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 用户消息：偏好页移除后固定只显示用户头像 + 附件/气泡 + 操作按钮，不再显示名字与时间戳。
 */
@Composable
internal fun UserMsg(
    text: String,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onMore: () -> Unit,
    scale: Float = 1.0f,
    avatarBitmap: android.graphics.Bitmap? = null,
    images: List<String> = emptyList(),
    files: List<MsgFile> = emptyList(),
    renderPrefs: RenderPrefs = RenderPrefs(),
) {
    val s8 = (Space8.value * scale).dp
    val s12 = (Space12.value * scale).dp
    val iconSz = (18f * scale).dp
    val avatarSz = (24f * scale).dp
    val textStyle = MaterialTheme.typography.titleMedium.scaledBy(scale)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(s8)) {
        MessageAvatar(bitmap = avatarBitmap, size = avatarSz, fallbackIcon = true)
        MessageAttachmentRow(images = images, files = files)
        if (text.isNotBlank()) {
            // 气泡拥抱内容（markdown 走 fillWidth=false 让正文按内容撑宽，而不是固定铺满 80%）
            Box(Modifier.maxWidthFraction(0.8f).clip(RoundedCornerShape(s12)).background(MaterialTheme.colorScheme.primaryContainer).padding(s8)) {
                if (renderPrefs.markdown) {
                    MessageContent(content = text, isStreaming = false, textStyle = textStyle,
                        renderPrefs = renderPrefs, fillWidth = false)
                } else {
                    Text(text, style = textStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        // 用户操作按钮常显（偏好页已移除，不再有开关）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Copy, stringResource(R.string.copy),
                Modifier.noRippleClickable { onCopy() }
                    .padding(horizontal = s8, vertical = s8).size(iconSz),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Lucide.RotateCcw, stringResource(R.string.regenerate),
                Modifier.noRippleClickable { onRegenerate() }
                    .padding(horizontal = s8, vertical = s8).size(iconSz),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            // 末位插槽不带 end padding，让图形与气泡右缘对齐
            Icon(Lucide.EllipsisVertical, stringResource(R.string.more),
                Modifier.noRippleClickable { onMore() }
                    .padding(start = s8, top = s8, bottom = s8).size(iconSz),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * AI 消息：偏好页移除后固定只显示角色卡头像 + 思考时间线/正文/引用 + 操作与版本切换，
 * 不再显示模型图标、模型名、时间戳和 token/速度/耗时统计。
 */
@Composable
internal fun AIMsg(
    msg: ChatMessage,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onMore: () -> Unit,
    onPrevAlt: () -> Unit = {},
    onNextAlt: () -> Unit = {},
    onSpeak: () -> Unit = {},
    speaking: Boolean = false,
    scale: Float = 1.0f,
    regexScripts: List<CharRegex> = emptyList(),
    depth: Int? = null,
    userName: String = "",
    charName: String = "",
    charAvatarBitmap: android.graphics.Bitmap? = null,
    autoCollapseThinking: Boolean = true,
    thinkingMarkdown: Boolean = true,
    renderPrefs: RenderPrefs = RenderPrefs(),
    chatMessagesJson: String = "[]",
    frontendContextJson: String = "{}",
    localVariablesJson: String = "{}",
    globalVariablesJson: String = "{}",
    onSetInputText: (String) -> Unit = {},
    onSetChatMessage: (Int, String) -> Unit = { _, _ -> },
    onSelectChatMessageSwipe: (Int, Int) -> Unit = { _, _ -> },
    onReplaceVariables: (String, Map<String, String>) -> Unit = { _, _ -> },
    rpcCall: FrontendRpcCall = { method, _ -> error("Frontend RPC method is unavailable: $method") },
) {
    val s8 = (Space8.value * scale).dp
    val iconSz = (18f * scale).dp
    val avatarSz = (24f * scale).dp
    val textStyle = MaterialTheme.typography.titleMedium.scaledBy(scale)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(s8)) {
        // 角色卡头像（无卡面图时不占位）
        if (charAvatarBitmap != null) {
            MessageAvatar(bitmap = charAvatarBitmap, size = avatarSz, fallbackIcon = false)
        }
        // 深度思考 / 联网搜索时间线卡片
        ThoughtTimelineCard(
            msg = msg,
            isStreaming = isStreaming,
            scale = scale,
            autoCollapse = autoCollapseThinking,
            markdown = thinkingMarkdown,
            renderPrefs = renderPrefs,
        )
        GeneratedImageRow(images = msg.images, aspectRatio = msg.imageAspectRatio)
        if (msg.files.isNotEmpty()) {
            MessageAttachmentRow(images = emptyList(), files = msg.files)
        }
        if (msg.images.isEmpty()) {
            MessageContent(
                content = msg.content,
                isStreaming = isStreaming,
                textStyle = textStyle,
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
            )
        }
        // 引用胶囊卡：流式结束后显示在正文之后，点击弹出来源列表。
        // 聚合本条消息全部搜索调用的结果，按 URL 去重（多次搜索可能命中同一页面）
        val citations = remember(msg.searches) {
            msg.searches.flatMap { it.items }.distinctBy { it.url }
        }
        if (!isStreaming && citations.isNotEmpty()) {
            var showCitations by rememberSaveable { mutableStateOf(false) }
            CitationsPill(items = citations, scale = scale) { showCitations = true }
            if (showCitations) {
                CitationsSheet(items = citations, scale = scale) { showCitations = false }
            }
        }
        // 操作栏：流式生成中不显示，生成结束才出现
        if (!isStreaming) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 首位插槽不带 start padding，让图形与消息内容左缘对齐
                    Icon(Lucide.Copy, stringResource(R.string.copy),
                        Modifier.noRippleClickable { onCopy() }
                            .padding(top = s8, bottom = s8, end = s8).size(iconSz),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Lucide.RotateCcw, stringResource(R.string.regenerate),
                        Modifier.noRippleClickable { onRegenerate() }
                            .padding(horizontal = s8, vertical = s8).size(iconSz),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 朗读：播放中变停止图标，点击停止
                    Icon(
                        if (speaking) Lucide.CircleStop else Lucide.Volume2,
                        stringResource(if (speaking) R.string.stop_speaking else R.string.speak),
                        Modifier.noRippleClickable { onSpeak() }
                            .padding(horizontal = s8, vertical = s8).size(iconSz),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(Lucide.EllipsisVertical, stringResource(R.string.more),
                        Modifier.noRippleClickable { onMore() }
                            .padding(horizontal = s8, vertical = s8).size(iconSz),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                // 多版本左右切换（重新生成的历史版本 / 备选开场白）
                if (msg.alts.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val canPrev = msg.altIdx > 0
                        val canNext = msg.altIdx < msg.alts.lastIndex
                        Icon(Lucide.ChevronLeft, null,
                            Modifier.testTag("previous_alternative_${msg.ts}").noRippleClickable(enabled = canPrev) { onPrevAlt() }
                                .padding(horizontal = s8, vertical = s8).size(iconSz),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canPrev) 1f else 0.35f))
                        Text("${msg.altIdx + 1}/${msg.alts.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // 末位插槽不带 end padding，让图形与右缘对齐
                        Icon(Lucide.ChevronRight, null,
                            Modifier.testTag("next_alternative_${msg.ts}").noRippleClickable(enabled = canNext) { onNextAlt() }
                                .padding(start = s8, top = s8, bottom = s8).size(iconSz),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canNext) 1f else 0.35f))
                    }
                }
            }
        }
    }
}
