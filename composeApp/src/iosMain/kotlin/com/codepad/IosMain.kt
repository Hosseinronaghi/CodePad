package com.codepad

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults

private class IosDraftGateway : FileGateway {
    override fun readDrafts(): List<DraftSnapshot> = emptyList()
    override fun writeDraft(id: Long, snapshot: DraftSnapshot) {
        NSUserDefaults.standardUserDefaults.setObject(snapshot.text, forKey = "recovery_$id")
    }
    override fun deleteDraft(id: Long) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey("recovery_$id")
    }
    override val supportsOpen = false
    override fun open(onResult: (OpenedFile?) -> Unit) = onResult(null)
    override fun save(document: EditorDocument, onResult: (String?) -> Unit) {
        val key = "draft_${document.name}"
        NSUserDefaults.standardUserDefaults.setObject(document.field.text, forKey = key)
        onResult(key)
    }
}
fun MainViewController() = ComposeUIViewController {
    CodePadApp(remember { IosDraftGateway() })
}
