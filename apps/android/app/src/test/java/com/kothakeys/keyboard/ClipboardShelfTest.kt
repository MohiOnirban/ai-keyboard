package com.kothakeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardShelfTest {
    @Test fun retainsOnlyNonexpiredItemsUnlessPinned() {
        val day = 86_400_000L
        val shelf = ClipboardShelf("", day, 1)
        shelf.save("shopping")
        shelf.save("directions")
        shelf.pin("directions")
        val restored = ClipboardShelf(shelf.encode(), 3 * day, 1)
        assertEquals(listOf("directions"), restored.items.map { it.text })
        restored.pin("directions")
        assertFalse(restored.items.single().pinned)
        restored.remove("directions")
        assertTrue(restored.items.isEmpty())
    }

    @Test fun protectsPinsAndCapsContentAndStorage() {
        val shelf = ClipboardShelf("", 42, 7)
        repeat(24) { assertTrue(shelf.save("clip $it")); shelf.pin("clip $it") }
        assertFalse(shelf.save("new item"))
        assertFalse(shelf.save("x".repeat(1601)))
        assertTrue(shelf.save("clip 3"))
        assertEquals(24, shelf.items.size)
        shelf.clear()
        assertEquals("[]", shelf.encode())
    }
}
