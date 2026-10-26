package com.codepad

fun nextMatch(text: String, query: String, fromIndex: Int): Int {
    if (query.isEmpty()) return -1
    val start = fromIndex.coerceIn(0, text.length)
    val found = text.indexOf(query, start)
    return if (found >= 0) found else text.indexOf(query)
}
fun countMatches(text: String, query: String): Int {
    if (query.isEmpty()) return 0
    var count = 0
    var position = 0
    while (position <= text.length - query.length) {
        val next = text.indexOf(query, position)
        if (next < 0) break
        count++
        position = next + query.length
    }
    return count
}
fun cursorLocation(text: String, offset: Int): Pair<Int, Int> {
    val prefix = text.take(offset.coerceIn(0, text.length))
    return (prefix.count { it == '\n' } + 1) to (prefix.length - prefix.lastIndexOf('\n'))
}
