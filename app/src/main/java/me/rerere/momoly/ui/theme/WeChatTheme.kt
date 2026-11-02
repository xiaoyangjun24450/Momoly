package me.rerere.momoly.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.momoly.ui.components.appClickableNoIndication
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/*
 * 微信视觉规范（聊天页 / 会话列表共用）。
 *
 * 微信与 M3 是两套设计语言：聊天背景、气泡、顶栏和输入栏的色值都按微信取，不跟随 M3
 * 的 primary/surface 语义。深色判断统一用 MaterialTheme 背景亮度，跟随 ThemeStore 的
 * 强制明暗（不能用 isSystemInDarkTheme，那会无视用户选择）。
 */

/** 微信浅色配色（聊天背景 #EDEDED、我的气泡 #95EC69、发送键 #07C160）。 */
internal val WeChatLightColors = WeChatColors(
    chatBackground = Color(0xFFEDEDED),
    headerBackground = Color(0xFFEDEDED),
    headerDivider = Color(0xFFD8D8D8),
    incomingBubble = Color(0xFFFFFFFF),
    outgoingBubble = Color(0xFF95EC69),
    incomingText = Color(0xFF191919),
    outgoingText = Color(0xFF191919),
    secondaryText = Color(0xFF999999),
    timeText = Color(0xFFA6A6A6),
    divider = Color(0xFFD9D9D9),
    inputBarBackground = Color(0xFFF7F7F7),
    inputFieldBackground = Color(0xFFFFFFFF),
    sendButton = Color(0xFF07C160),
    onSendButton = Color(0xFFFFFFFF),
    iconTint = Color(0xFF191919),
    panelBackground = Color(0xFFF7F7F7),
    panelTile = Color(0xFFFFFFFF),
    avatarFallback = Color(0xFFCBD2D6),
    onAvatarFallback = Color(0xFFFFFFFF),
    listBackground = Color(0xFFFFFFFF),
    listDivider = Color(0xFFEDEDED),
)

/** 微信深色配色（背景 #111111、我的气泡 #3EB575、气泡文字仍为深色）。 */
internal val WeChatDarkColors = WeChatColors(
    chatBackground = Color(0xFF111111),
    headerBackground = Color(0xFF1E1E1E),
    headerDivider = Color(0xFF2A2A2A),
    incomingBubble = Color(0xFF2C2C2E),
    outgoingBubble = Color(0xFF3EB575),
    incomingText = Color(0xFFE9E9E9),
    outgoingText = Color(0xFF0B0B0B),
    secondaryText = Color(0xFF8A8A8A),
    timeText = Color(0xFF7A7A7A),
    divider = Color(0xFF2A2A2A),
    inputBarBackground = Color(0xFF1E1E1E),
    inputFieldBackground = Color(0xFF2C2C2E),
    sendButton = Color(0xFF3EB575),
    onSendButton = Color(0xFF0B0B0B),
    iconTint = Color(0xFFCFCFCF),
    panelBackground = Color(0xFF1E1E1E),
    panelTile = Color(0xFF2C2C2E),
    avatarFallback = Color(0xFF4A4A4C),
    onAvatarFallback = Color(0xFFFFFFFF),
    listBackground = Color(0xFF191919),
    listDivider = Color(0xFF262626),
)

/** 微信聊天页/会话列表用到的全部颜色。 */
data class WeChatColors(
    val chatBackground: Color,
    val headerBackground: Color,
    val headerDivider: Color,
    val incomingBubble: Color,
    val outgoingBubble: Color,
    val incomingText: Color,
    val outgoingText: Color,
    val secondaryText: Color,
    val timeText: Color,
    val divider: Color,
    val inputBarBackground: Color,
    val inputFieldBackground: Color,
    val sendButton: Color,
    val onSendButton: Color,
    val iconTint: Color,
    val panelBackground: Color,
    val panelTile: Color,
    val avatarFallback: Color,
    val onAvatarFallback: Color,
    val listBackground: Color,
    val listDivider: Color,
)

val LocalWeChatColors = staticCompositionLocalOf { WeChatLightColors }

@Composable
fun weChatColors(): WeChatColors = LocalWeChatColors.current

/** 在聊天页根部包一层：全屏页/抽屉里的微信组件都从这里取色。 */
@Composable
fun ProvideWeChatColors(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    CompositionLocalProvider(
        LocalWeChatColors provides if (dark) WeChatDarkColors else WeChatLightColors,
        content = content,
    )
}

/** 微信消息正文：16sp/23sp，颜色随气泡方向（深色下我的气泡仍是深色字）。 */
@Composable
fun weChatMessageTextStyle(fromMe: Boolean, scale: Float = 1f): TextStyle {
    val colors = weChatColors()
    return TextStyle(
        fontSize = 16.sp * scale,
        lineHeight = 23.sp * scale,
        color = if (fromMe) colors.outgoingText else colors.incomingText,
    )
}

/** 微信头像：圆角方形（4dp），无图时用名字首字 + 固定灰底。 */
@Composable
fun WeChatAvatar(
    bitmap: Bitmap?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = weChatColors()
    // asImageBitmap 每次重组都会新建包装对象，画到 GPU 上等于换了一张图；记住它避免重复上传纹理
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier.size(size).clip(RoundedCornerShape(4.dp)).background(colors.avatarFallback),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = label.take(1).ifBlank { "?" },
                color = colors.onAvatarFallback,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * 气泡底色用 `drawWithCache` + 路径缓存画出来，不用自定义 [Shape] 去 `clip`：
 * 自定义路径裁切会给每个气泡建一个带 path-clip 的图层；即便只裁圆角矩形，也是一个图层。
 * 气泡正文四周都有内边距，本来就不需要裁切内容，所以这里既省掉裁切，也省掉每帧重建 Path。
 */
private fun Modifier.weChatBubbleBackground(fromMe: Boolean, color: Color): Modifier = drawWithCache {    val radius = 6.dp.toPx()
    val tailW = 6.dp.toPx()
    val tailH = 10.dp.toPx()
    val tailTop = 13.dp.toPx()
    val path = Path()
    if (fromMe) {
        path.addRoundRect(RoundRect(0f, 0f, size.width - tailW, size.height, CornerRadius(radius)))
        path.moveTo(size.width - tailW, tailTop)
        path.lineTo(size.width, tailTop + tailH / 2f)
        path.lineTo(size.width - tailW, tailTop + tailH)
    } else {
        path.addRoundRect(RoundRect(tailW, 0f, size.width, size.height, CornerRadius(radius)))
        path.moveTo(tailW, tailTop)
        path.lineTo(0f, tailTop + tailH / 2f)
        path.lineTo(tailW, tailTop + tailH)
    }
    path.close()
    onDrawBehind { drawPath(path, color) }
}

/** 微信气泡容器：负责底色、圆角尾巴、长按和内边距，正文/附件都放进来。 */
@Composable
fun WeChatBubble(
    fromMe: Boolean,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = weChatColors()
    val bubbleColor = if (fromMe) colors.outgoingBubble else colors.incomingBubble
    Column(
        modifier
            .weChatBubbleBackground(fromMe = fromMe, color = bubbleColor)
            .then(
                if (onLongPress != null) {
                    // 微信气泡按压没有 Material 波纹（波纹会从圆角处溢出来），长按弹菜单即可
                    Modifier.appClickableNoIndication(onLongClick = onLongPress)
                } else {
                    Modifier
                },
            )
            .padding(
                start = if (fromMe) 12.dp else 18.dp,
                end = if (fromMe) 18.dp else 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        content = content,
    )
}

/** 居中的时间分隔（微信在消息组之间显示灰字时间）。 */
@Composable
fun WeChatTimeLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = weChatColors().timeText,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/**
 * 微信顶栏的“对方正在输入中…”：句尾省略号逐个出现。
 *
 * 用 450ms 一次的协程切换省略号个数，而不是 infiniteTransition 逐帧动画：后者在整个生成期间
 * 以 60fps 持续重组顶栏并让整窗重绘，配合流式更新会明显拖慢聊天页。
 */
@Composable
fun WeChatTypingText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(450)
            dots = if (dots >= 3) 1 else dots + 1
        }
    }
    Text(
        text = "$text${".".repeat(dots)}",
        modifier = modifier,
        color = color ?: weChatColors().secondaryText,
        fontSize = 12.sp,
        maxLines = 1,
    )
}

/** 相隔超过 5 分钟（或首条消息）才显示一次时间分隔，和微信一致。 */
fun shouldShowWeChatTime(previousTimestamp: Long?, timestamp: Long): Boolean =
    previousTimestamp == null || timestamp - previousTimestamp > 5 * 60 * 1000L

/**
 * 微信时间文案：当天 `HH:mm`、昨天加“昨天”、一周内用星期、更早用日期。
 * [yesterdayLabel] 由调用方传入本地化字符串（组件层拿不到 Compose 资源时也方便测试）。
 */
fun weChatTimeLabel(
    timestamp: Long,
    yesterdayLabel: String,
    now: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
): String {
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val current = Calendar.getInstance().apply { timeInMillis = now }
    fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val clock = SimpleDateFormat("HH:mm", locale).format(Date(timestamp))
    if (sameDay(target, current)) return clock
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }
    if (sameDay(target, yesterday)) return "$yesterdayLabel $clock"
    if (now - timestamp in 0 until 7L * 24 * 60 * 60 * 1000) {
        return SimpleDateFormat("EEEE HH:mm", locale).format(Date(timestamp))
    }
    val sameYear = target.get(Calendar.YEAR) == current.get(Calendar.YEAR)
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(
        locale,
        if (sameYear) "MdHm" else "yMdHm",
    )
    return SimpleDateFormat(pattern, locale).format(Date(timestamp))
}
