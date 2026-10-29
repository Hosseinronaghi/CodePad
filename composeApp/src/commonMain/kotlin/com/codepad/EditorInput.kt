package com.codepad

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Inserts a matching closing bracket only after a single-character user insertion. */
fun completeBracket(previous: TextFieldValue, updated: TextFieldValue, enabled: Boolean): TextFieldValue {
    if (!enabled || !previous.selection.collapsed || !updated.selection.collapsed) return updated
    if (updated.text.length != previous.text.length + 1) return updated
    val insertedIndex = updated.selection.start - 1
    if (insertedIndex !in updated.text.indices) return updated
    if (updated.text.removeRange(insertedIndex, insertedIndex + 1) != previous.text) return updated
    val matching = when (updated.text[insertedIndex]) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        else -> return updated
    }
    if (updated.text.getOrNull(insertedIndex + 1) == matching) return updated
    val completed = updated.text.substring(0, insertedIndex + 1) + matching +
        updated.text.substring(insertedIndex + 1)
    return updated.copy(text = completed, selection = TextRange(insertedIndex + 1))
}
