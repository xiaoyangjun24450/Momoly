package me.rerere.momoly.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Earth
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Paperclip
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.WandSparkles
import com.composables.icons.lucide.X
import me.rerere.momoly.R
import me.rerere.momoly.data.api.ReasoningLevel
import me.rerere.momoly.extension.QuickReply
import me.rerere.momoly.ui.api.ProviderIcon
import me.rerere.momoly.ui.components.AppIconButton
import me.rerere.momoly.ui.components.Space12
import me.rerere.momoly.ui.components.Space8
import me.rerere.momoly.ui.components.noRippleClickable
import me.rerere.momoly.ui.components.reasoningIcon
import me.rerere.momoly.ui.theme.WeChatTypingText
import me.rerere.momoly.ui.theme.weChatColors

private val ChatHeaderHeight = 44.dp
private val ChatInputBarInset = 8.dp

/** 聊天返回首页；只保留搜索操作，位置和时间由共享世界状态提供。 */
@Composable
internal fun ChatTopBar(
    title: String,
    typing: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    time: String = "08:00",
    characterLocation: String = "家",
) {
    val colors = weChatColors()
    Column(Modifier.fillMaxWidth().background(colors.headerBackground).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(ChatHeaderHeight)) {
            AppIconButton(
                icon = Lucide.ChevronLeft,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                tint = colors.iconTint,
                size = 40.dp,
                iconSize = 24.dp,
            )
            Text(title, Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                color = colors.incomingText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            AppIconButton(
                icon = Lucide.Search,
                contentDescription = stringResource(R.string.search_chats),
                onClick = onSearch,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                tint = colors.iconTint,
                size = 40.dp,
                iconSize = 22.dp,
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.chat_character_location, characterLocation),
                color = colors.secondaryText, fontSize = 12.sp)
            Text(time, color = colors.secondaryText, fontSize = 12.sp)
        }
        if (typing) {
            WeChatTypingText(stringResource(R.string.chat_typing_indicator),
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.headerDivider)
    }
}

/**
 * 微信式输入栏：灰底、白色圆角输入框、绿色发送键；附件/模型/搜索/思考/图片生成收进“+”面板，
 * 输入行保持干净的一行，避免和微信原生的输入体验产生割裂。
 *
 * 参数签名与旧版保持一致：ChatContent 只负责接线，视觉重构不牵动调用方。
 */
@Composable
internal fun ChatBottomArea(
    state: TextFieldState,
    attachments: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    showAttachment: Boolean,
    onToggleAttachment: () -> Unit,
    editing: Boolean = false,
    onCancelEdit: () -> Unit = {},
    onExpand: () -> Unit = {},
    onSend: () -> Unit,
    currentModelId: String = "",
    reasoning: ReasoningLevel = ReasoningLevel.AUTO,
    imageGenerationEnabled: Boolean = false,
    generating: Boolean = false,
    searchEnabled: Boolean = false,
    searchProvider: String = "",
    builtInSearchEnabled: Boolean = false,
    onStop: () -> Unit = {},
    onSelectModel: () -> Unit = {},
    onSelectReasoning: () -> Unit = {},
    onOpenImageGenerationSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCamera: () -> Unit = {},
    onGallery: () -> Unit = {},
    onFile: () -> Unit = {},
    quickReplies: List<QuickReply> = emptyList(),
    onQuickReply: (QuickReply) -> Unit = {},
    enterToSend: Boolean = true,
    replyTo: me.rerere.momoly.data.chat.MessageQuote? = null,
    onCancelReply: () -> Unit = {},
) {
    val colors = weChatColors()
    val hasContent = state.text.isNotBlank() || attachments.isNotEmpty()

    Column(Modifier.fillMaxWidth().background(colors.inputBarBackground)) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ChatInputBarInset, vertical = 6.dp),
        ) {
            replyTo?.let { quote ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChatQuote(quote, Modifier.weight(1f))
                    AppIconButton(Lucide.X, stringResource(R.string.cancel_reply), onCancelReply)
                }
            }
            // 快捷回复行：微信式小胶囊，横向滚动，位于输入行上方
            if (quickReplies.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(bottom = Space8),
                    horizontalArrangement = Arrangement.spacedBy(Space8),
                ) {
                    itemsIndexed(quickReplies) { _, qr ->
                        Text(
                            text = qr.label,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.incomingText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.panelTile)
                                .clickable { onQuickReply(qr) }
                                .padding(horizontal = Space12, vertical = 6.dp),
                        )
                    }
                }
            }
            // 编辑态提示：说明当前发送会原地更新已有消息，右侧 X 可以退出编辑
            if (editing) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.SquarePen, null, Modifier.size(14.dp), tint = colors.sendButton)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.editing_in_progress),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.sendButton,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Lucide.X,
                        stringResource(R.string.cancel),
                        Modifier.size(28.dp).noRippleClickable { onCancelEdit() }.padding(7.dp),
                        tint = colors.iconTint,
                    )
                }
            }
            // 附件预览：位于输入框上方，可横向滚动
            if (attachments.isNotEmpty()) {
                InputAttachmentRow(
                    attachments = attachments,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            // 输入行：+、输入框、发送键三者底部对齐——多行输入时按钮贴着底边，不随文字高度上浮
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                AppIconButton(
                    icon = if (showAttachment) Lucide.X else Lucide.Plus,
                    contentDescription = stringResource(
                        if (showAttachment) R.string.close else R.string.chat_tools,
                    ),
                    onClick = onToggleAttachment,
                    modifier = Modifier.padding(bottom = 4.dp),
                    tint = colors.iconTint,
                    size = 36.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.width(4.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.inputFieldBackground),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        state = state,
                        textStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = colors.incomingText),
                        cursorBrush = SolidColor(colors.sendButton),
                        // 限高走 lineLimits 而非 heightIn：BTF2 在测量阶段自己滚动内部内容，
                        // 外部限高只会裁掉超出部分
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                        // 回车发送：软键盘用 Send 动作；硬件 Enter（非 Shift）在 modifier 里拦截为发送，Shift+Enter 保留换行
                        keyboardOptions = if (enterToSend) {
                            KeyboardOptions(imeAction = ImeAction.Send)
                        } else {
                            KeyboardOptions.Default
                        },
                        onKeyboardAction = if (enterToSend) ({ onSend() }) else null,
                        modifier = Modifier.weight(1f)
                            .padding(start = Space12, end = 4.dp, top = 9.dp, bottom = 9.dp)
                            .heightIn(min = 22.dp)
                            .then(if (enterToSend) Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && !event.isShiftPressed) {
                                    onSend()
                                    true
                                } else false
                            } else Modifier),
                        decorator = TextFieldDecorator { inner ->
                            // CenterStart：空态占位文本在字段内垂直居中，与右侧展开图标对齐
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (state.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.input_hint),
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                        color = colors.secondaryText,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    // 全屏编辑入口：微信没有这个动作，收进输入框右缘，弱化成次要图标
                    AppIconButton(
                        icon = Lucide.Maximize,
                        contentDescription = stringResource(R.string.expand),
                        onClick = onExpand,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = colors.secondaryText,
                        size = 32.dp,
                        iconSize = 18.dp,
                    )
                }
                Spacer(Modifier.width(Space8))
                Box(
                    Modifier
                        .padding(bottom = 4.dp)
                        .width(56.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (generating || hasContent) colors.sendButton else colors.panelTile)
                        .clickable { if (hasContent) onSend() else if (generating) onStop() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (generating && !hasContent) {
                        Icon(
                            Lucide.Square,
                            stringResource(R.string.stop),
                            Modifier.size(14.dp),
                            tint = colors.onSendButton,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.send),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = if (hasContent) colors.onSendButton else colors.secondaryText,
                        )
                    }
                }
            }
            // 微信式工具面板：拍照/相册/文件/模型 + 联网搜索/思考预算/图片生成，每行四列
            AnimatedVisibility(visible = showAttachment) {
                Column(Modifier.fillMaxWidth().padding(top = Space8)) {
                    Row(Modifier.fillMaxWidth()) {
                        ChatToolCell(stringResource(R.string.take_photo), onCamera) {
                            Icon(Lucide.Camera, null, Modifier.size(28.dp), tint = colors.iconTint)
                        }
                        ChatToolCell(stringResource(R.string.gallery), onGallery) {
                            Icon(Lucide.Image, null, Modifier.size(28.dp), tint = colors.iconTint)
                        }
                        ChatToolCell(stringResource(R.string.file), onFile) {
                            Icon(Lucide.Paperclip, null, Modifier.size(28.dp), tint = colors.iconTint)
                        }
                        ChatToolCell(stringResource(R.string.select_model), onSelectModel) {
                            if (currentModelId.isNotBlank()) {
                                ProviderIcon(currentModelId, size = 30.dp)
                            } else {
                                Icon(Lucide.Package, null, Modifier.size(28.dp), tint = colors.iconTint)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        ChatToolCell(stringResource(R.string.web_search), onOpenSearch) {
                            // 内置搜索用地球图标；外部搜索显示所选提供商品牌图标
                            if (!builtInSearchEnabled && searchEnabled && searchProvider.isNotBlank()) {
                                ProviderIcon(searchProvider, size = 30.dp)
                            } else {
                                Icon(Lucide.Earth, null, Modifier.size(28.dp), tint = colors.iconTint)
                            }
                        }
                        ChatToolCell(stringResource(R.string.thinking_budget), onSelectReasoning) {
                            Icon(reasoningIcon(reasoning), null, Modifier.size(28.dp), tint = colors.iconTint)
                        }
                        if (imageGenerationEnabled) {
                            ChatToolCell(stringResource(R.string.image_generation_settings), onOpenImageGenerationSettings) {
                                Icon(Lucide.WandSparkles, null, Modifier.size(28.dp), tint = colors.iconTint)
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 微信工具面板的一格：56dp 白色圆角方块图标 + 12sp 文字，整格可点。 */
@Composable
private fun RowScope.ChatToolCell(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val colors = weChatColors()
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = Space8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(colors.panelTile),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
