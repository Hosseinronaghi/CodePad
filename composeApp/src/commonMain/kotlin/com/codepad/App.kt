package com.codepad

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val background = Color(0xFF0D1117)
private val panel = Color(0xFF161B22)
private val accent = Color(0xFF58D6A2)
private val foreground = Color(0xFFE6EDF3)
private val muted = Color(0xFF92A1B2)
private val stroke = Color(0xFF303A46)

@Composable
fun CodePadApp(gateway: FileGateway) {
    val docs = remember { mutableStateListOf(EditorDocument(1L, "untitled.txt")) }
    var activeId by remember { mutableLongStateOf(1L) }
    var nextId by remember { mutableLongStateOf(2L) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var ignoreCase by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var folderPath by remember { mutableStateOf<String?>(null) }
    var folderRefresh by remember { mutableIntStateOf(0) }
    var folderOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FolderEntry?>(null) }
    var foldedView by remember { mutableStateOf(false) }
    var runConfirm by remember { mutableStateOf(false) }
    var runOutput by remember { mutableStateOf<String?>(null) }
    var replaceOpen by remember { mutableStateOf(false) }
    var closeId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var fontSize by remember { mutableFloatStateOf(gateway.getPreference("font_size", "14").toFloatOrNull()?.coerceIn(10f, 28f) ?: 14f) }
    var restored by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var wordWrap by remember { mutableStateOf(gateway.getPreference("word_wrap", "true") == "true") }
    var showNumbers by remember { mutableStateOf(gateway.getPreference("line_numbers", "true") == "true") }
    var autoBrackets by remember { mutableStateOf(gateway.getPreference("auto_brackets", "true") == "true") }
    var goToOpen by remember { mutableStateOf(false) }
    var goToValue by remember { mutableStateOf("") }
    var defaultEncoding by remember { mutableStateOf(gateway.getPreference("default_encoding", "UTF-8")) }
    var themeMode by remember { mutableStateOf(gateway.getPreference("theme", "system")) }
    val darkMode = when(themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val bg = if(darkMode) background else Color(0xFFF7F9FC)
    val bar = if(darkMode) panel else Color.White
    val ink = if(darkMode) foreground else Color(0xFF17212E)
    val secondary = if(darkMode) muted else Color(0xFF64748B)
    val primaryAccent = if(darkMode) accent else Color(0xFF008967)
    val border = if(darkMode) stroke else Color(0xFFE0E6EF)

    LaunchedEffect(gateway) {
        if (!restored) {
            restored = true
            val recovered = gateway.readDrafts()
            if (recovered.isNotEmpty()) {
                docs.clear()
                recovered.forEach {
                    val doc = EditorDocument(it.id, it.name, "", it.token, it.encoding)
                    doc.edit(TextFieldValue(it.text))
                    docs.add(doc)
                }
                activeId = docs.first().id
                nextId = (recovered.maxOfOrNull { it.id } ?: 1L) + 1
            }
        }
    }
    docs.forEach { doc ->
        val text = doc.field.text
        val name = doc.name
        val token = doc.fileToken
        val dirty = doc.isDirty
        LaunchedEffect(doc.id, text, name, token, dirty) {
            delay(850)
            if (dirty) gateway.writeDraft(doc.id, DraftSnapshot(doc.id, name, text, token, doc.encoding))
            else gateway.deleteDraft(doc.id)
        }
    }
    val active = docs.firstOrNull { it.id == activeId } ?: docs.first()
    val scheme = if (darkMode) darkColorScheme(primary = primaryAccent, onPrimary = bg, background = bg, onBackground = ink, surface = bar, onSurface = ink) else lightColorScheme(primary = primaryAccent, onPrimary = Color.White, background = bg, onBackground = ink, surface = bar, onSurface = ink)
    MaterialTheme(colorScheme = scheme) {
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
            // Compact app bar that stays below the Android system status bar.
            Row(
                Modifier.fillMaxWidth().height(58.dp).background(bar).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("CodePad", color = ink, fontSize = 20.sp, style = MaterialTheme.typography.titleLarge)
                    Text("CODE EDITOR", color = primaryAccent, fontSize = 9.sp, letterSpacing = 1.4.sp)
                }
                TextButton(onClick = {
                    val id = nextId++
                    docs.add(EditorDocument(id, "untitled.txt", initialEncoding = defaultEncoding))
                    activeId = id
                }) { Text("+ New", color = primaryAccent) }
                TextButton(onClick = {
                    gateway.open { file ->
                        if (file != null) {
                            val doc = EditorDocument(nextId++, file.name, file.text, file.token, file.encoding)
                            docs.add(doc); activeId = doc.id
                            status = "Opened " + file.name
                        } else status = "Open cancelled or failed"
                    }
                }, enabled = gateway.supportsOpen) { Text("Open", color = if (gateway.supportsOpen) foreground else muted) }
                TextButton(onClick = {
                    val snapshot = active.field.text
                    gateway.save(active) { token ->
                        if (token != null) {
                            active.markSaved(token, snapshot)
                            if (!active.isDirty) gateway.deleteDraft(active.id)
                            status = "Saved"
                        } else status = "Save cancelled or failed"
                    }
                }) { Text("Save", color = primaryAccent) }
            }
            HorizontalDivider(color = border, thickness = 1.dp)
            LazyRow(
                Modifier.fillMaxWidth().height(48.dp).background(bar),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(docs, key = { it.id }) { doc ->
                    val selected = doc.id == activeId
                    Row(
                        Modifier.padding(end = 6.dp)
                            .background(if (selected) bg else if (darkMode) Color(0xFF222A34) else Color(0xFFECF2F7), RoundedCornerShape(8.dp))
                            .clickable { activeId = doc.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("▣", color = primaryAccent, fontSize = 13.sp)
                        Spacer(Modifier.width(7.dp))
                        Text((if (doc.isDirty) "● " else "") + doc.name,
                            color = if (selected) foreground else muted,
                            maxLines = 1, fontSize = 12.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("×", Modifier.clickable { closeId = doc.id }, color = secondary, fontSize = 18.sp)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(bg)
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { active.undo() }, enabled = active.canUndo) { Text("↶ Undo", fontSize = 12.sp) }
                TextButton(onClick = { active.redo() }, enabled = active.canRedo) { Text("↷ Redo", fontSize = 12.sp) }
                TextButton(onClick = { searchOpen = !searchOpen }) { Text("⌕ Find", fontSize = 12.sp) }
                TextButton(onClick = { searchOpen = true; replaceOpen = !replaceOpen }) { Text("Replace", fontSize = 12.sp) }
                TextButton(onClick = { runConfirm = true }, enabled = gateway.supportsExecution) { Text("▶ Run", fontSize = 12.sp) }
                TextButton(onClick = { foldedView = !foldedView }) { Text(if (foldedView) "Edit" else "Fold", fontSize = 12.sp) }
                TextButton(onClick = { goToOpen = true }) { Text("Go to", fontSize = 12.sp) }
                TextButton(onClick = {
                    val snapshot = active.field.text
                    gateway.saveAs(active) { token ->
                        if (token != null) {
                            active.markSaved(token, snapshot)
                            if (!active.isDirty) gateway.deleteDraft(active.id)
                            status = "Saved as new file"
                        } else status = "Save As cancelled or failed"
                    }
                }) { Text("Save As", fontSize = 12.sp) }
                TextButton(onClick = { gateway.chooseFolder { path -> if (path != null) { folderPath = path; folderOpen = true; folderRefresh++ } } }) { Text("Folder", fontSize = 12.sp) }
                TextButton(onClick = { settingsOpen = true }) { Text("Settings", fontSize = 12.sp) }
                TextButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(10f); gateway.setPreference("font_size", fontSize.toInt().toString()) }) { Text("A−", fontSize = 12.sp) }
                TextButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(28f); gateway.setPreference("font_size", fontSize.toInt().toString()) }) { Text("A+", fontSize = 12.sp) }
            }
            HorizontalDivider(color = border, thickness = 1.dp)
            if (folderOpen && folderPath != null) {
                val path = folderPath!!
                val entries = remember(path, folderRefresh) { gateway.listFolder(path) }
                Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).background(bar).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Folder: " + path.substringAfterLast('/').substringAfterLast('\\'), color = ink, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { newFolderOpen = true }) { Text("+ Folder") }
                        TextButton(onClick = { folderRefresh++ }) { Text("Refresh") }
                        TextButton(onClick = { folderOpen = false }) { Text("Close") }
                    }
                    entries.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (entry.directory) { folderPath = entry.path; folderRefresh++ }
                            else {
                                val file = gateway.readEntry(entry.path)
                                if (file != null) {
                                    val doc = EditorDocument(nextId++, file.name, file.text, file.token, file.encoding)
                                    docs.add(doc); activeId = doc.id
                                } else status = "Unable to open file"
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Text((if (entry.directory) "📁 " else "▣ ") + entry.name, modifier = Modifier.fillMaxWidth(), fontSize = 12.sp)
                        }
                        if (!entry.directory) TextButton(onClick = { pendingDelete = entry }) { Text("Delete", fontSize = 11.sp) }
                        }
                    }
                }
            }
            if (searchOpen) {
                Column(Modifier.padding(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Find") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(regex, { regex = it })
                        Text("Regex")
                        Checkbox(ignoreCase, { ignoreCase = it })
                        Text("Ignore case")
                    }
                    val hits = remember(active.field.text, query, regex, ignoreCase) {
                        findMatches(active.field.text, query, regex, ignoreCase)
                    }
                    if (replaceOpen) {
                        OutlinedTextField(replacement, { replacement = it }, label = { Text("Replace with") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        TextButton(onClick = {
                            val result = replaceMatches(active.field.text, query, replacement, regex, ignoreCase)
                            if (result.error == null) {
                                if (result.text != active.field.text) active.edit(TextFieldValue(result.text))
                                status = "Replaced " + result.count
                            } else status = result.error
                        }) { Text("Replace all") }
                    }
                    TextButton(onClick = {
                        val hit = nextHit(hits.hits, active.field.selection.end)
                        if (hit != null) active.edit(active.field.copy(selection = TextRange(hit.start, hit.end)))
                        else status = hits.error ?: "No matches"
                    }) { Text("Next match") }
                    Text(hits.error ?: (hits.hits.size.toString() + " matches"))
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                val scroll = rememberScrollState()
                if (showNumbers) Column(Modifier.width(48.dp).fillMaxHeight().background(bar).verticalScroll(scroll, enabled = false).padding(top = 12.dp), horizontalAlignment = Alignment.End) {
                    repeat((active.field.text.count { it == '\n' } + 1).coerceAtMost(10000)) {
                        Text((it + 1).toString(), modifier = Modifier.padding(end = 10.dp), fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp, color = secondary, fontFamily = FontFamily.Monospace)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).then(if (wordWrap) Modifier else Modifier.horizontalScroll(rememberScrollState())).padding(12.dp)) {
                    if (active.field.text.isEmpty()) {
                        Text("Start typing your notes or code…", color = secondary.copy(alpha = 0.65f), fontFamily = FontFamily.Monospace, fontSize = fontSize.sp)
                    }
                    if (foldedView) {
                        val blocks = remember(active.field.text) { detectFoldBlocks(active.field.text) }
                        var folded by remember(active.id) { mutableStateOf(setOf<Int>()) }
                        Column {
                            Text("Code folding · preview only; tap Edit to modify text", color = secondary, fontSize = 11.sp)
                            blocks.take(30).forEach { block ->
                                TextButton(onClick = { folded = if (block.startLine in folded) folded - block.startLine else folded + block.startLine }) {
                                    Text((if (block.startLine in folded) "▸" else "▾") + " Line " + (block.startLine + 1), fontSize = 12.sp)
                                }
                            }
                            Text(foldedPreview(active.field.text, folded), color = ink, fontFamily = FontFamily.Monospace, fontSize = fontSize.sp)
                        }
                    } else BasicTextField(
                        value = active.field,
                        onValueChange = { active.edit(completeBracket(active.field, it, autoBrackets)) },
                        modifier = if (wordWrap) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().defaultMinSize(minWidth = 280.dp),
                        textStyle = TextStyle(color = ink, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(primaryAccent),
                        visualTransformation = VisualTransformation { value ->
                            TransformedText(highlighted(value.text, active.extension), OffsetMapping.Identity)
                        }
                    )
                }
            }
            val position = cursorLocation(active.field.text, active.field.selection.start)
            Row(Modifier.fillMaxWidth().height(32.dp).background(bar).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(status, color = secondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("Ln " + position.first + " : " + position.second + "   ·   " + active.encoding, color = primaryAccent, fontSize = 11.sp)
            }
        }
        if (newFolderOpen) AlertDialog(
            onDismissRequest = { newFolderOpen = false },
            title = { Text("Create folder") },
            text = { OutlinedTextField(newFolderName, { newFolderName = it }, label = { Text("Folder name") }) },
            confirmButton = { TextButton(onClick = {
                val path = folderPath
                if (path != null && gateway.createFolder(path, newFolderName)) {
                    folderRefresh++
                    status = "Folder created"
                } else status = "Unable to create folder"
                newFolderName = ""
                newFolderOpen = false
            }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newFolderOpen = false }) { Text("Cancel") } }
        )
        val deleting = pendingDelete
        if (deleting != null) AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete file?") },
            text = { Text("Delete " + deleting.name + "? This cannot be undone.") },
            confirmButton = { TextButton(onClick = {
                status = if (gateway.deleteEntry(deleting.path)) "File deleted" else "Delete failed"
                folderRefresh++
                pendingDelete = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
        if (runConfirm) AlertDialog(
            onDismissRequest = { runConfirm = false },
            title = { Text("Run PHP / Java") },
            text = { Text("Only execute code you trust. This runs locally with your user permissions and is NOT sandboxed. Requires installed PHP or Java JDK.") },
            confirmButton = { TextButton(onClick = {
                runConfirm = false
                gateway.execute(active) { output -> runOutput = output }
            }) { Text("Execute") } },
            dismissButton = { TextButton(onClick = { runConfirm = false }) { Text("Cancel") } }
        )
        if (runOutput != null) AlertDialog(
            onDismissRequest = { runOutput = null },
            title = { Text("Program output") },
            text = { Text(runOutput ?: "", fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { runOutput = null }) { Text("Close") } }
        )
        if (goToOpen) AlertDialog(
            onDismissRequest = { goToOpen = false },
            title = { Text("Go to line") },
            text = { OutlinedTextField(value = goToValue, onValueChange = { goToValue = it.filter(Char::isDigit) },
                label = { Text("Line number") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val number = goToValue.toIntOrNull()
                if (number != null && number > 0) {
                    val text = active.field.text
                    var offset = 0
                    repeat((number - 1).coerceAtMost(text.count { it == '\n' })) {
                        val next = text.indexOf('\n', offset)
                        if (next >= 0) offset = next + 1
                    }
                    active.edit(active.field.copy(selection = TextRange(offset)))
                    status = "Line " + number
                }
                goToOpen = false
            }) { Text("Go") } },
            dismissButton = { TextButton(onClick = { goToOpen = false }) { Text("Cancel") } }
        )
        if (settingsOpen) AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Editor settings") },
            text = {
                Column {
                    Text("Appearance")
                    listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = themeMode == value, onClick = {
                                themeMode = value
                                gateway.setPreference("theme", value)
                            })
                            Text(label)
                        }
                    }
                    Text("Default file encoding")
                    listOf("UTF-8", "Windows-1252", "ISO-8859-1", "UTF-16LE", "UTF-16BE").forEach { choice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = defaultEncoding == choice, onClick = {
                                defaultEncoding = choice
                                gateway.setPreference("default_encoding", choice)
                            })
                            Text(choice)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = wordWrap, onCheckedChange = {
                            wordWrap = it; gateway.setPreference("word_wrap", it.toString())
                        })
                        Spacer(Modifier.width(8.dp))
                        Text("Word wrap")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = showNumbers, onCheckedChange = {
                            showNumbers = it; gateway.setPreference("line_numbers", it.toString())
                        })
                        Spacer(Modifier.width(8.dp))
                        Text("Line numbers")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = autoBrackets, onCheckedChange = {
                            autoBrackets = it; gateway.setPreference("auto_brackets", it.toString())
                        })
                        Spacer(Modifier.width(8.dp))
                        Text("Auto-close brackets")
                    }
                    Text("Font size: " + fontSize.toInt())
                    Slider(value = fontSize, onValueChange = {
                        fontSize = it
                        gateway.setPreference("font_size", it.toInt().toString())
                    }, valueRange = 10f..28f, steps = 17)
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done") } }
        )
        val closing = docs.firstOrNull { it.id == closeId }
        if (closing != null) AlertDialog(
            onDismissRequest = { closeId = null },
            title = { Text("Close document?") },
            text = { Text(if (closing.isDirty) "Changes are not saved." else "Close this tab?") },
            confirmButton = {
                TextButton(onClick = {
                    gateway.deleteDraft(closing.id)
                    docs.remove(closing)
                    if (docs.isEmpty()) docs.add(EditorDocument(nextId++, "untitled.txt", initialEncoding = defaultEncoding))
                    activeId = docs.first().id
                    closeId = null
                }) { Text("Close") }
            },
            dismissButton = { TextButton(onClick = { closeId = null }) { Text("Cancel") } }
        )
    }
}
