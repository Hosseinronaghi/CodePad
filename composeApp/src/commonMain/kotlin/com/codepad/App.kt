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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val background = Color(0xFF121920)
private val panel = Color(0xFF1B2630)
private val accent = Color(0xFF75CEA2)

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
    var replaceOpen by remember { mutableStateOf(false) }
    var closeId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var fontSize by remember { mutableFloatStateOf(14f) }
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(gateway) {
        if (!restored) {
            restored = true
            val recovered = gateway.readDrafts()
            if (recovered.isNotEmpty()) {
                docs.clear()
                recovered.forEach {
                    val doc = EditorDocument(it.id, it.name, "", it.token)
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
            if (dirty) gateway.writeDraft(doc.id, DraftSnapshot(doc.id, name, text, token))
            else gateway.deleteDraft(doc.id)
        }
    }
    val active = docs.firstOrNull { it.id == activeId } ?: docs.first()
    val scheme = darkColorScheme(primary = accent, background = background, surface = panel)
    MaterialTheme(colorScheme = scheme) {
        Column(Modifier.fillMaxSize().background(background)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("CodePad", color = accent, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = {
                    val id = nextId++
                    docs.add(EditorDocument(id, "untitled.txt"))
                    activeId = id
                }) { Text("New") }
                TextButton(onClick = {
                    gateway.open { file ->
                        if (file != null) {
                            val doc = EditorDocument(nextId++, file.name, file.text, file.token)
                            docs.add(doc)
                            activeId = doc.id
                            status = "Opened"
                        } else status = "Open cancelled or failed"
                    }
                }, enabled = gateway.supportsOpen) { Text("Open") }
                TextButton(onClick = {
                    val snapshot = active.field.text
                    gateway.save(active) { token ->
                        if (token != null) {
                            active.markSaved(token, snapshot)
                            if (!active.isDirty) gateway.deleteDraft(active.id)
                            status = "Saved"
                        } else status = "Save cancelled or failed"
                    }
                }) { Text("Save") }
            }
            LazyRow(Modifier.fillMaxWidth().background(panel)) {
                items(docs, key = { it.id }) { doc ->
                    Row(Modifier.padding(4.dp).background(if (doc.id == activeId) background else panel, RoundedCornerShape(6.dp)).clickable { activeId = doc.id }.padding(8.dp)) {
                        Text((if (doc.isDirty) "* " else "") + doc.name)
                        Spacer(Modifier.width(10.dp))
                        Text("×", Modifier.clickable { closeId = doc.id })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { active.undo() }, enabled = active.canUndo) { Text("Undo") }
                TextButton(onClick = { active.redo() }, enabled = active.canRedo) { Text("Redo") }
                TextButton(onClick = { searchOpen = !searchOpen }) { Text("Find") }
                TextButton(onClick = { searchOpen = true; replaceOpen = !replaceOpen }) { Text("Replace") }
                TextButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(10f) }) { Text("A-") }
                TextButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(28f) }) { Text("A+") }
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
                Column(Modifier.width(45.dp).fillMaxHeight().background(panel).verticalScroll(scroll, enabled = false).padding(top = 12.dp)) {
                    repeat((active.field.text.count { it == '\n' } + 1).coerceAtMost(10000)) {
                        Text((it + 1).toString(), fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp, color = accent)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    BasicTextField(
                        value = active.field,
                        onValueChange = { active.edit(it) },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minWidth = 280.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(accent)
                    )
                }
            }
            val position = cursorLocation(active.field.text, active.field.selection.start)
            Text("  " + status + "  |  Ln " + position.first + ", Col " + position.second + "  UTF-8", color = accent, fontSize = 12.sp)
        }
        val closing = docs.firstOrNull { it.id == closeId }
        if (closing != null) AlertDialog(
            onDismissRequest = { closeId = null },
            title = { Text("Close document?") },
            text = { Text(if (closing.isDirty) "Changes are not saved." else "Close this tab?") },
            confirmButton = {
                TextButton(onClick = {
                    gateway.deleteDraft(closing.id)
                    docs.remove(closing)
                    if (docs.isEmpty()) docs.add(EditorDocument(nextId++, "untitled.txt"))
                    activeId = docs.first().id
                    closeId = null
                }) { Text("Close") }
            },
            dismissButton = { TextButton(onClick = { closeId = null }) { Text("Cancel") } }
        )
    }
}
