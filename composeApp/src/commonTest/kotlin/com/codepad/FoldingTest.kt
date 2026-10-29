package com.codepad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldingTest {
    @Test fun nestedBraces() {
        val source = "class A {\n  fun x() {\n    print(1)\n  }\n}"
        val blocks = detectFoldBlocks(source)
        assertEquals(2, blocks.size)
        assertEquals(FoldBlock(0, 4), blocks[0])
        assertEquals(FoldBlock(1, 3), blocks[1])
        assertTrue(foldedPreview(source, setOf(1)).contains("lines folded"))
    }
    @Test fun stringsAreNotBraces() {
        assertEquals(emptyList(), detectFoldBlocks("val text = \"{\""))
    }
}
