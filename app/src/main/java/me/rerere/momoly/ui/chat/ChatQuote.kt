package me.rerere.momoly.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.momoly.R
import me.rerere.momoly.data.chat.MessageQuote
import me.rerere.momoly.ui.theme.weChatColors

/** Quotes are plain text snapshots: never render their HTML, scripts or macros. */
@Composable
internal fun ChatQuote(quote: MessageQuote, modifier: Modifier = Modifier) {
    val colors = weChatColors()
    val author = quote.author.ifBlank { stringResource(if (quote.role == "user") R.string.role_user else R.string.role_assistant) }
    Column(modifier.testTag("message_quote").clip(RoundedCornerShape(6.dp))
        .background(colors.panelTile).padding(8.dp)) {
        Text(author, color = colors.secondaryText, fontSize = 12.sp, maxLines = 1)
        Text(quote.content + if (quote.truncated) "…" else "", color = colors.secondaryText,
            fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
