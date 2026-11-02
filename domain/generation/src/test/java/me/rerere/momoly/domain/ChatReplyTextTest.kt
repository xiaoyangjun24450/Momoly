package me.rerere.momoly.domain

import org.junit.Assert.*
import org.junit.Test

class ChatReplyTextTest {
    @Test fun splitsCommasAndMergesShortFragmentsWithoutLosingTheTail() {
        assertEquals(listOf("我出门了", "顺便给你买水", "要常温的吗"), ChatReplyText.split("我出门了，顺便给你买水。要常温的吗？"))
        assertEquals(listOf("她说他来了"), ChatReplyText.split("她说，他来了。"))
        assertEquals(listOf("好"), ChatReplyText.split("好！"))
        val text = (1..12).joinToString("。") { "这是第${it}句话" }
        val parts = ChatReplyText.split(text)
        assertEquals(6, parts.size)
        assertEquals(text.replace("。", ""), parts.joinToString("").replace("\n", ""))
    }

    @Test fun protectsNumbersLinksCodeAndQuotedContent() {
        val parts = ChatReplyText.split("时间是18:00，金额3.14。网址https://example.com/a?q=1.2，代码`a,b();`。她说“你好，再见”。")
        assertTrue("时间是18:00" in parts)
        assertTrue("金额3.14" in parts)
        assertTrue("网址https://example.com/a?q=1.2" in parts)
        assertTrue("代码`a,b();`" in parts)
        assertTrue("她说“你好，再见”" in parts)
        assertEquals(listOf("```js\na,b();\n```"), ChatReplyText.split("```js\na,b();\n```"))
    }

    @Test fun neverHardCutsUnpunctuatedTextAndBoundsDelays() {
        val long = "很长的没有标点的正文".repeat(100)
        assertEquals(listOf(long), ChatReplyText.split(long))
        assertEquals(1_500L, ChatReplyText.gapMillis("好"))
        assertEquals(6_000L, ChatReplyText.gapMillis(long))
        assertTrue(ChatReplyText.split("，。！？").isEmpty())
    }
}
