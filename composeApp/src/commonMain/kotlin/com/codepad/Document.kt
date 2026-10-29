package com.codepad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue

class EditorDocument(val id: Long, initialName: String, initialText: String = "", initialToken: String? = null) {
    var name by mutableStateOf(initialName)
    var field by mutableStateOf(TextFieldValue(initialText))
        private set
    var savedText by mutableStateOf(initialText)
        private set
    var fileToken by mutableStateOf(initialToken)
        private set
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    val isDirty: Boolean get() = this.field.text != savedText
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    fun edit(next: TextFieldValue) {
        if (next == field) return
        if (next.text != field.text) {
            if (undoStack.size >= 200) undoStack.removeFirst()
            undoStack.addLast(field)
            redoStack.clear()
        }
        field = next
        syncHistory()
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(field)
        field = undoStack.removeLast()
        syncHistory()
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(field)
        field = redoStack.removeLast()
        syncHistory()
    }
    fun markSaved(token: String, savedSnapshot: String) {
        fileToken = token
        savedText = savedSnapshot
    }
    private fun syncHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}
data class DraftSnapshot(val id: Long, val name: String, val text: String, val token: String?)
interface FileGateway {
    fun getPreference(key: String, defaultValue: String): String = defaultValue
    fun setPreference(key: String, value: String) {}
    fun readDrafts(): List<DraftSnapshot> = emptyList()
    fun writeDraft(id: Long, snapshot: DraftSnapshot) {}
    fun deleteDraft(id: Long) {}
    val supportsOpen: Boolean
    fun open(onResult: (OpenedFile?) -> Unit)
    fun save(document: EditorDocument, onResult: (String?) -> Unit)
}
data class OpenedFile(val name: String, val text: String, val token: String?)
fun isSupportedText(text: String): Boolean = '\u0000' !in text
