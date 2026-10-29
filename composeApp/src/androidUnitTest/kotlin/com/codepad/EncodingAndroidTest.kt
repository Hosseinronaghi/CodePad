package com.codepad

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncodingAndroidTest {
    @Test fun utf8BomRoundtrip() {
        val original = "\uFEFFسلام CodePad".removePrefix("\uFEFF")
        val bytes = encodeText(original, "UTF-8-BOM")
        val decoded = decodeText(bytes, "UTF-8")
        assertEquals("UTF-8-BOM", decoded.encoding)
        assertEquals(original, decoded.text)
        assertContentEquals(bytes, encodeText(decoded.text, decoded.encoding))
    }

    @Test fun utf16Roundtrip() {
        val bytes = encodeText("Hello دنیا", "UTF-16LE-BOM")
        val decoded = decodeText(bytes, "UTF-8")
        assertEquals("UTF-16LE-BOM", decoded.encoding)
        assertEquals("Hello دنیا", decoded.text)
    }

    @Test fun invalidUtf8Rejected() {
        assertFailsWith<java.nio.charset.CharacterCodingException> {
            decodeText(byteArrayOf(0xC3.toByte(), 0x28), "UTF-8")
        }
    }

    @Test fun cannotSilentlyLoseCharacters() {
        assertFailsWith<java.nio.charset.CharacterCodingException> {
            encodeText("سلام", "ISO-8859-1")
        }
    }
}
