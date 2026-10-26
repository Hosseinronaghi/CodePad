package com.codepad
data class SearchHit(val start: Int, val end: Int)
data class SearchResult(val hits: List<SearchHit>, val error: String? = null)
data class ReplaceResult(val text: String, val count: Int, val error: String? = null)
fun findMatches(text: String, pattern: String, regex: Boolean, ignoreCase: Boolean = false): SearchResult {
    if (pattern.isEmpty()) return SearchResult(emptyList())
    return try {
        val expression = if (regex) pattern else Regex.escape(pattern)
        val flags = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val matcher = Regex(expression, flags)
        SearchResult(matcher.findAll(text).take(100_000).map { SearchHit(it.range.first, it.range.last + 1) }.toList())
    } catch (e: IllegalArgumentException) { SearchResult(emptyList(), e.message ?: "Invalid regular expression") }
}
fun replaceMatches(text: String, pattern: String, replacement: String, regex: Boolean, ignoreCase: Boolean = false): ReplaceResult {
    if (pattern.isEmpty()) return ReplaceResult(text, 0)
    return try {
        val expression = Regex(if (regex) pattern else Regex.escape(pattern), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        val count = expression.findAll(text).count()
        val result = if (regex) expression.replace(text, replacement) else expression.replace(text) { replacement }
        ReplaceResult(result, count)
    } catch (e: IllegalArgumentException) {
        ReplaceResult(text, 0, e.message ?: "Invalid replacement")
    } catch (e: IndexOutOfBoundsException) {
        ReplaceResult(text, 0, e.message ?: "Invalid capture group")
    }
}
fun nextHit(hits: List<SearchHit>, offset: Int): SearchHit? = hits.firstOrNull { it.start >= offset } ?: hits.firstOrNull()
