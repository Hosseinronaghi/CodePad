package com.codepad

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodePadApp(rememberAndroidFileGateway()) }
    }
}
@Composable
fun rememberAndroidFileGateway(): FileGateway {
    val context = androidx.compose.ui.platform.LocalContext.current
    var openCallback by remember { mutableStateOf<((OpenedFile?) -> Unit)?>(null) }
    var saveCallback by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    var pendingContent by remember { mutableStateOf("") }
    var pendingEncoding by remember { mutableStateOf("UTF-8") }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val result = uri?.let {
            runCatching {
                val fileBytes = context.contentResolver.openInputStream(it)!!.use { stream -> stream.readBytes() }
                val selectedEncoding = context.getSharedPreferences("codepad_settings", android.content.Context.MODE_PRIVATE).getString("default_encoding", "UTF-8") ?: "UTF-8"
                val decoded = decodeText(fileBytes, selectedEncoding)
                val text = decoded.text
                val name = context.contentResolver.query(it, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "document.txt"
                if (!isSupportedText(text)) error("Binary file")
                runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                OpenedFile(name, text, it.toString(), decoded.encoding)
            }.getOrNull()
        }
        openCallback?.invoke(result); openCallback = null
    }
    val creator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        val saved = uri?.let {
            runCatching {
                val encoded = encodeText(pendingContent, pendingEncoding)
                context.contentResolver.openOutputStream(it, "wt")!!.use { stream -> stream.write(encoded) }
                it.toString()
            }.getOrNull()
        }
        saveCallback?.invoke(saved); saveCallback = null
    }
    return remember(opener, creator) {
        object : FileGateway {
            private val prefs = context.getSharedPreferences("codepad_settings", android.content.Context.MODE_PRIVATE)
            override fun getPreference(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
            override fun setPreference(key: String, value: String) { prefs.edit().putString(key, value).apply() }
            private val draftsDir = java.io.File(context.filesDir, "recovery")
            override fun readDrafts(): List<DraftSnapshot> = draftsDir.listFiles()?.mapNotNull { file ->
                runCatching {
                    val lines = file.readText(Charsets.UTF_8).split("\n", limit = 3)
                    if (lines.size != 3) null else DraftSnapshot(file.nameWithoutExtension.toLong(), lines[0], lines[2], lines[1].ifEmpty { null })
                }.getOrNull()
            } ?: emptyList()
            override fun writeDraft(id: Long, snapshot: DraftSnapshot) {
                runCatching {
                    draftsDir.mkdirs()
                    val target = java.io.File(draftsDir, "$id.draft")
                    val temp = java.io.File(draftsDir, "$id.tmp")
                    temp.writeText(snapshot.name.replace("\n", " ") + "\n" + (snapshot.token ?: "") + "\n" + snapshot.text)
                    if (!temp.renameTo(target)) { target.delete(); temp.renameTo(target) }
                }
            }
            override fun deleteDraft(id: Long) { java.io.File(draftsDir, "$id.draft").delete() }
            override val supportsOpen = true
            override fun open(onResult: (OpenedFile?) -> Unit) {
                openCallback = onResult
                opener.launch(arrayOf("*/*"))
            }
            override fun save(document: EditorDocument, onResult: (String?) -> Unit) {
                val token = document.fileToken
                if (token != null) {
                    val result = runCatching {
                        val encoded = encodeText(document.field.text, document.encoding)
                        context.contentResolver.openOutputStream(Uri.parse(token), "wt")!!.use { it.write(encoded) }
                        token
                    }.getOrNull()
                    onResult(result)
                } else {
                    pendingContent = document.field.text
                    pendingEncoding = document.encoding
                    saveCallback = onResult
                    creator.launch(document.name)
                }
            }
        }
    }
}
