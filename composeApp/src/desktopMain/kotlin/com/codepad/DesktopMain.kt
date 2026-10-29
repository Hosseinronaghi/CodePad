package com.codepad

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser

private class DesktopGateway : FileGateway {
    private val prefs = java.util.prefs.Preferences.userNodeForPackage(DesktopGateway::class.java)
    override fun getPreference(key: String, defaultValue: String): String = prefs.get(key, defaultValue)
    override fun setPreference(key: String, value: String) { prefs.put(key, value) }
    private val draftsDir = File(System.getProperty("user.home"), ".codepad/recovery")
    override fun readDrafts(): List<DraftSnapshot> = draftsDir.listFiles()?.mapNotNull { file ->
        runCatching {
            val contents = file.readText(Charsets.UTF_8)
            val lines = contents.split("\n", limit = 4)
            val encodings = setOf("UTF-8", "UTF-8-BOM", "UTF-16LE", "UTF-16LE-BOM", "UTF-16BE", "UTF-16BE-BOM", "Windows-1252", "ISO-8859-1")
            if (lines.size >= 4 && lines[2] in encodings)
                DraftSnapshot(file.nameWithoutExtension.toLong(), lines[0], lines[3], lines[1].ifEmpty { null }, lines[2])
            else {
                val old = contents.split("\n", limit = 3)
                if (old.size != 3) null else DraftSnapshot(file.nameWithoutExtension.toLong(), old[0], old[2], old[1].ifEmpty { null })
            }
        }.getOrNull()
    } ?: emptyList()
    override fun writeDraft(id: Long, snapshot: DraftSnapshot) {
        runCatching {
            draftsDir.mkdirs()
            atomicWrite(File(draftsDir, "$id.draft"), snapshot.name.replace("\n", " ") + "\n" + (snapshot.token ?: "") + "\n" + snapshot.encoding + "\n" + snapshot.text)
        }
    }
    override fun deleteDraft(id: Long) { File(draftsDir, "$id.draft").delete() }
    override val supportsOpen = true
    override fun open(onResult: (OpenedFile?) -> Unit) {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            onResult(runCatching {
                val decoded = decodeText(file.readBytes(), getPreference("default_encoding", "UTF-8"))
                OpenedFile(file.name, decoded.text, file.absolutePath, decoded.encoding)
            }.getOrNull())
        } else onResult(null)
    }
    override fun save(document: EditorDocument, onResult: (String?) -> Unit) {
        val file = if (document.fileToken != null) File(document.fileToken) else {
            val chooser = JFileChooser().apply { selectedFile = File(document.name) }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) { onResult(null); return }
            chooser.selectedFile
        }
        onResult(runCatching { atomicWriteBytes(file, encodeText(document.field.text, document.encoding)); file.absolutePath }.getOrNull())
    }
}
private fun atomicWrite(file: File, content: String) = atomicWriteBytes(file, content.toByteArray(Charsets.UTF_8))

private fun atomicWriteBytes(file: File, bytes: ByteArray) {
    val parent = file.absoluteFile.parentFile
    parent.mkdirs()
    val temp = File.createTempFile(".codepad-", ".tmp", parent)
    try {
        temp.writeBytes(bytes)
        try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    } finally { temp.delete() }
}
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "CodePad") {
        CodePadApp(remember { DesktopGateway() })
    }
}
