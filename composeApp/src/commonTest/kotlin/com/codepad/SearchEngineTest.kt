package com.codepad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SearchEngineTest {
    @Test fun findsLiteral() {
        val result = findMatches("cat dog cat", "cat", false)
        assertEquals(2, result.hits.size)
        assertEquals(0, result.hits.first().start)
    }
    @Test fun regexReplacement() {
        val result = replaceMatches("a1 b2", "([a-z])([0-9])", "$2$1", true)
        assertEquals("1a 2b", result.text)
        assertEquals(2, result.count)
    }
    @Test fun invalidRegex() {
        val result = findMatches("abc", "[", true)
        assertNotNull(result.error)
    }
}
