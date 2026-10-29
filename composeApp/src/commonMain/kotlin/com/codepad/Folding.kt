package com.codepad

data class FoldBlock(val startLine: Int, val endLine: Int)

/** Conservative brace-based fold ranges; ignores unbalanced blocks. Indices are 0-based. */
fun detectFoldBlocks(text: String): List<FoldBlock> {
    val stack = ArrayDeque<Int>()
    val blocks = mutableListOf<FoldBlock>()
    var line = 0
    var inString = false
    var quote = '\u0000'
    var escaped = false
    var lineComment = false
    for (char in text) {
        if (char == '\n') { line++; lineComment = false; escaped = false; continue }
        if (lineComment) continue
        if (inString) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == quote) inString = false
            continue
        }
        if (char == '"' || char == '\'') { inString = true; quote = char; continue }
        if (char == '{') stack.addLast(line)
        if (char == '}' && stack.isNotEmpty()) {
            val start = stack.removeLast()
            if (line > start) blocks.add(FoldBlock(start, line))
        }
    }
    return blocks.sortedWith(compareBy<FoldBlock> { it.startLine }.thenByDescending { it.endLine })
}

/** A read-only folded view of text; original text is never mutated. */
fun foldedPreview(text: String, collapsedLines: Set<Int>): String {
    val lines = text.split("\n")
    val blocks = detectFoldBlocks(text).associateBy { it.startLine }
    val output = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val block = blocks[index]
        if (index in collapsedLines && block != null) {
            output.add(lines[index] + " … [${block.endLine - index} lines folded]")
            index = block.endLine + 1
        } else { output.add(lines[index]); index++ }
    }
    return output.joinToString("\n")
}
