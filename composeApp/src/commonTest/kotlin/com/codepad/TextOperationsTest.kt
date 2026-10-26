package com.codepad

import kotlin.test.Test
import kotlin.test.assertEquals

class TextOperationsTest {
    @Test fun nextWraps() { assertEquals(0, nextMatch("hello world", "hello", 6)) }
    @Test fun missing() { assertEquals(-1, nextMatch("hello", "zzz", 0)) }
    @Test fun counts() { assertEquals(3, countMatches("a a a", "a")) }
    @Test fun location() { assertEquals(2 to 2, cursorLocation("hello\nabc", 7)) }
}
