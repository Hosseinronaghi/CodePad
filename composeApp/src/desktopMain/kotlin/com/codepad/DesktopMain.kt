package com.codepad

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser

private class DesktopGateway : FileGateway {
    private val draftsDir = File(System.getProperty("user.home"), ".codepad/recovery")
    override fun readDrafts(): List<DraftSnapshot> = draftsDir.listFiles()?.mapNotNull { file ->
        runCatching {
            val lines = file.readText(Charsets.UTF_8).split("\n", limit = 3)
            if (lines.size != 3) null else DraftSnapshot(file.nameWithoutExtension.toLong(), lines[0], lines[2], lines[1].ifEmpty { null })
        }.getOrNull()
    } ?: emptyList()
    override fun writeDraft(id: Long, snapshot: DraftSnapshot) {
        runCatching {
            draftsDir.mkdirs()
            atomicWrite(File(draftsDir, "$id.draft"), snapshot.name.replace("\n", " ") + "\n" + (snapshot.token ?: "") + "\n" + snapshot.text)
        }
    }
    override fun deleteDraft(id: Long) { File(draftsDir, "$id.draft").delete() }
    override val supportsOpen = true
    override fun open(onResult: (OpenedFile?) -> Unit) {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            onResult(runCatching { OpenedFile(file.name, file.readText(Charsets.UTF_8), file.absolutePath) }.getOrNull())
        } else onResult(null)
    }
    override fun save(document: EditorDocument, onResult: (String?) -> Unit) {
        val file = if (document.fileToken != null) File(document.fileToken) else {
            val chooser = JFileChooser().apply { selectedFile = File(document.name) }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) { onResult(null); return }
            chooser.selectedFile
        }
        onResult(runCatching { atomicWrite(file, document.field.text); file.absolutePath }.getOrNull())
    }
}
private fun atomicWrite(file: File, content: String) {
    val parent = file.absoluteFile.parentFile
    parent.mkdirs()
    val temp = File.createTempFile(".codepad-", ".tmp", parent)
    try {
        temp.writeText(content, Charsets.UTF_8)
        try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    } finally { temp.delete() }
}
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "CodePad") {
        CodePadApp(remember { DesktopGateway() })
    }
}
