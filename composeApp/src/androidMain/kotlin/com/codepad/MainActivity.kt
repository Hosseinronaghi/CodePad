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
    var folderCallback by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    var saveCallback by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    var pendingContent by remember { mutableStateOf("") }
    var pendingEncoding by remember { mutableStateOf("UTF-8") }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        folderCallback?.invoke(uri?.toString())
        folderCallback = null
    }
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
    return remember(opener, creator, folderPicker) {
        object : FileGateway {
            private val prefs = context.getSharedPreferences("codepad_settings", android.content.Context.MODE_PRIVATE)
            override fun getPreference(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
            override fun setPreference(key: String, value: String) { prefs.edit().putString(key, value).apply() }
            private val draftsDir = java.io.File(context.filesDir, "recovery")
            override fun readDrafts(): List<DraftSnapshot> = draftsDir.listFiles()?.mapNotNull { file ->
                runCatching {
                    val contents = file.readText(Charsets.UTF_8)
                    val lines = contents.split("\n", limit = 4)
                    val knownEncodings = setOf("UTF-8", "UTF-8-BOM", "UTF-16LE", "UTF-16LE-BOM", "UTF-16BE", "UTF-16BE-BOM", "Windows-1252", "ISO-8859-1")
                    if (lines.size >= 4 && lines[2] in knownEncodings)
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
                    val target = java.io.File(draftsDir, "$id.draft")
                    val temp = java.io.File(draftsDir, "$id.tmp")
                    val payload = snapshot.name.replace("\n", " ") + "\n" +
                        (snapshot.token ?: "") + "\n" + snapshot.encoding + "\n" + snapshot.text
                    java.io.FileOutputStream(temp).use { output ->
                        output.write(payload.toByteArray(Charsets.UTF_8))
                        output.fd.sync()
                    }
                    try {
                        java.nio.file.Files.move(temp.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                        java.nio.file.Files.move(temp.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            override fun deleteDraft(id: Long) { java.io.File(draftsDir, "$id.draft").delete() }
            override fun chooseFolder(onResult: (String?) -> Unit) {
                folderCallback = onResult
                folderPicker.launch(null)
            }
            override fun listFolder(path: String): List<FolderEntry> = runCatching {
                val treeUri = Uri.parse(path)
                val documentId = if (android.provider.DocumentsContract.isDocumentUri(context, treeUri))
                    android.provider.DocumentsContract.getDocumentId(treeUri)
                else android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val childUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                val columns = arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                val entries = mutableListOf<FolderEntry>()
                context.contentResolver.query(childUri, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(0)
                        val name = cursor.getString(1)
                        val mime = cursor.getString(2)
                        val childDocument = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        entries.add(FolderEntry(name, childDocument.toString(), mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR))
                    }
                }
                entries.sortedWith(compareBy<FolderEntry> { !it.directory }.thenBy { it.name.lowercase() })
            }.getOrDefault(emptyList())
            override fun readEntry(path: String): OpenedFile? = runCatching {
                val uri = Uri.parse(path)
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val encoding = getPreference("default_encoding", "UTF-8")
                val decoded = decodeText(bytes, encoding)
                val name = context.contentResolver.query(uri,
                    arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else "document.txt"
                } ?: "document.txt"
                OpenedFile(name, decoded.text, path, decoded.encoding)
            }.getOrNull()
            override fun createFolder(path: String, name: String): Boolean = runCatching {
                require(name.isNotBlank() && '/' !in name && '\\' !in name)
                android.provider.DocumentsContract.createDocument(context.contentResolver,
                    Uri.parse(path), android.provider.DocumentsContract.Document.MIME_TYPE_DIR, name) != null
            }.getOrDefault(false)
            override fun deleteEntry(path: String): Boolean = runCatching {
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(path))
            }.getOrDefault(false)
            override val supportsOpen = true
            override fun open(onResult: (OpenedFile?) -> Unit) {
                openCallback = onResult
                opener.launch(arrayOf("*/*"))
            }
            override fun saveAs(document: EditorDocument, onResult: (String?) -> Unit) {
                pendingContent = document.field.text
                pendingEncoding = document.encoding
                saveCallback = onResult
                creator.launch(document.name)
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
