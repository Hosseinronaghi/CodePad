package com.codepad

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal data class DecodedText(val text: String, val encoding: String)

internal fun decodeText(bytes: ByteArray, preferred: String): DecodedText {
    val encoding = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> "UTF-8-BOM"
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> "UTF-16LE-BOM"
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> "UTF-16BE-BOM"
        else -> preferred
    }
    val offset = when (encoding) {
        "UTF-8-BOM" -> 3
        "UTF-16LE-BOM", "UTF-16BE-BOM" -> 2
        else -> 0
    }
    val charsetName = encoding.removeSuffix("-BOM")
    val decoder = Charset.forName(charsetName).newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val text = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
    require('\u0000' !in text) { "Binary file cannot be opened as text" }
    return DecodedText(text, encoding)
}

internal fun encodeText(text: String, encoding: String): ByteArray {
    val charsetName = encoding.removeSuffix("-BOM")
    val encoder = Charset.forName(charsetName).newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val buffer = encoder.encode(java.nio.CharBuffer.wrap(text))
    val payload = ByteArray(buffer.remaining())
    buffer.get(payload)
    val bom = when (encoding) {
        "UTF-8-BOM" -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        "UTF-16LE-BOM" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        "UTF-16BE-BOM" -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> byteArrayOf()
    }
    return bom + payload
}
