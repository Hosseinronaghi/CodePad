package com.codepad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

private val keywords = setOf(
    "echo", "require", "include", "foreach", "extends", "implements", "public", "protected", "static", "final", "abstract", "function", "use", "trait", "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when", "for", "while", "import", "package", "private", "public", "true", "false", "null",
    "def", "print", "from", "as", "in", "not", "and", "or", "lambda", "self", "async", "await", "pass", "with", "try", "except",
    "const", "let", "function", "export", "default", "new", "this", "switch", "case", "break", "continue", "throw",
    "int", "float", "double", "string", "void", "bool", "static", "using", "namespace", "struct", "enum"
)
fun highlighted(text: String, extension: String): AnnotatedString {
    if (extension !in setOf("kt", "kts", "py", "js", "jsx", "ts", "tsx", "java", "php", "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "swift", "dart", "sql", "json", "yaml", "yml", "toml", "html", "htm", "xml", "css", "scss", "md", "sh", "bash", "ps1", "bat", "gradle")) return AnnotatedString(text)
    val pattern = Regex("//[^\n]*|#[^\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b[A-Za-z_][A-Za-z_0-9]*\\b|\\b\\d+(?:\\.\\d+)?\\b")
    return buildAnnotatedString {
        var cursor = 0
        for (match in pattern.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            val item = match.value
            val color = when {
                item.startsWith("//") || item.startsWith("#") -> Color(0xFF7C927B)
                item.startsWith("\"") || item.startsWith("'") -> Color(0xFFE3BD83)
                item.firstOrNull()?.isDigit() == true -> Color(0xFFB4A2E8)
                item in keywords -> Color(0xFF80B8F0)
                else -> null
            }
            if (color == null) append(item) else withStyle(SpanStyle(color = color)) { append(item) }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}
