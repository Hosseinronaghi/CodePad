package com.codepad

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorInputTest {
    @Test fun insertsMatchingParenthesis() {
        val previous = TextFieldValue("abc", selection = TextRange(3))
        val typed = TextFieldValue("abc(", selection = TextRange(4))
        val result = completeBracket(previous, typed, true)
        assertEquals("abc()", result.text)
        assertEquals(4, result.selection.start)
    }
    @Test fun leavesPasteUnchanged() {
        val previous = TextFieldValue("", selection = TextRange(0))
        val pasted = TextFieldValue("({", selection = TextRange(2))
        assertEquals(pasted, completeBracket(previous, pasted, true))
    }
    @Test fun disabledIsNoop() {
        val previous = TextFieldValue("", selection = TextRange(0))
        val typed = TextFieldValue("[", selection = TextRange(1))
        assertEquals(typed, completeBracket(previous, typed, false))
    }
}
