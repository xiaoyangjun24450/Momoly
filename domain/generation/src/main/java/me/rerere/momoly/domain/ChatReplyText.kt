package me.rerere.momoly.domain

/** Local display formatting only; no second model request and no character-count truncation. */
object ChatReplyText {
    private val protected = Regex(
        "```[\\s\\S]*?(?:```|$)|`[^`\\n]+`|https?://[^\\s，。！？；、<>\"“”]+|" +
            "www\\.[^\\s，。！？；、<>]+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}|" +
            "\\[[^]\\n]*]\\([^\\s)]+\\)|[0-9]+(?:[.,:/-][0-9]+)+",
    )
    private val delimiters = "，,。.!！?？;；…\n\r"
    private val pairs = mapOf('“' to '”', '「' to '」', '『' to '』', '(' to ')', '（' to '）', '[' to ']', '{' to '}')

    fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val spans = protected.findAll(text).associateBy { it.range.first }
        val parts = mutableListOf<String>()
        val buffer = StringBuilder()
        val closers = ArrayDeque<Char>()
        fun flush() {
            buffer.toString().trim().takeIf(String::isNotEmpty)?.let(parts::add)
            buffer.setLength(0)
        }
        var index = 0
        while (index < text.length) {
            val span = spans[index]
            if (span != null) {
                val value = if (span.value.startsWith("http") || span.value.startsWith("www.")) {
                    span.value.trimEnd('.', ',', '!', '?', ';')
                } else span.value
                buffer.append(value)
                index += value.length
                continue
            }
            val char = text[index++]
            when {
                char == '"' -> {
                    if (closers.lastOrNull() == char) closers.removeLast() else closers.addLast(char)
                    buffer.append(char)
                }
                char in pairs -> { closers.addLast(pairs.getValue(char)); buffer.append(char) }
                closers.lastOrNull() == char -> { closers.removeLast(); buffer.append(char) }
                closers.isEmpty() && char in delimiters -> flush()
                else -> buffer.append(char)
            }
        }
        flush()
        val merged = mutableListOf<String>()
        for (part in parts) {
            if (merged.isNotEmpty() && merged.last().codePointCount(0, merged.last().length) < 3) {
                merged[merged.lastIndex] += part
            } else merged += part
        }
        if (merged.size > 1 && merged.last().codePointCount(0, merged.last().length) < 3) {
            val tail = merged.removeAt(merged.lastIndex)
            merged[merged.lastIndex] += tail
        }
        return if (merged.size <= 6) merged else merged.take(5) + merged.drop(5).joinToString("\n")
    }

    fun gapMillis(text: String, millisPerCharacter: Long = 750): Long =
        text.codePoints().filter { !Character.isWhitespace(it) }.count().coerceAtLeast(1) *
            millisPerCharacter.coerceIn(500, 1_000)
}

data class InterruptedReplyDraft(val turnId: String, val text: String)
