package me.rerere.momoly.domain.chat

fun buildSearchSnippet(content: String, query: String): String {
    val flat = content.replace('\n', ' ')
    val index = flat.indexOf(query, ignoreCase = true)
    if (index < 0) return flat.take(50)
    val start = (index - 15).coerceAtLeast(0)
    val end = (index + query.length + 35).coerceAtMost(flat.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < flat.length) "…" else ""
    return "$prefix${flat.substring(start, end)}$suffix"
}
