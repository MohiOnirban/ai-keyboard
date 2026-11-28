package com.kothakeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class EditorWordsTest {
    private val paragraph = "At home.\nWe recieve letters."
    private val first = paragraph.indexOf("recieve")
    private val last = first + 7

    @Test fun aTapInsideAWordAtItsEndOrSelectionTargetsTheSameRange() {
        listOf(first to first, first + 3 to first + 3, last to last, first to last, last to first).forEach { (a, b) ->
            val snapshot = TextSnapshot(paragraph, 0, a, b)
            val result = requireNotNull(snapshot.wordAtCursor())
            assertEquals(first, result.range.start)
            assertEquals("recieve", result.range.value)
            assertEquals("letters", result.following)
            assertTrue(result.range.matches(snapshot))
            assertFalse(result.range.matches(snapshot.copy(text = paragraph.replace("letters", "parcels"))))
            assertFalse(result.range.matches(snapshot.copy(selectionStart = 0)))
        }
    }

    @Test fun unsafeContextsNeverProduceEditableRanges() {
        listOf(TextSnapshot("https://example.com", 0, 12, 12), TextSnapshot("name@example.com", 0, 3, 3),
            TextSnapshot("fragment other", 4, 2, 2), TextSnapshot("one and two", 0, 0, 7),
            TextSnapshot("x".repeat(68), 0, 4, 4), TextSnapshot("foo_bar", 0, 2, 2)).forEach {
            assertNull(it.wordAtCursor())
        }
    }

    @Test fun aShortRightHandSampleDoesNotProveEndOfWord() {
        val partial = TextSnapshot("we improv", 0, 5, 5)
        assertNull(partial.wordAtCursor())
        assertEquals("improv", partial.copy(documentEndVerified = true).wordAtCursor()?.range?.value)
    }

    @Test fun banglaVowelSignsAndConjunctsBelongToTheSameWord() {
        val words = "আমি স্কুলে যাই"
        val token = requireNotNull(TextSnapshot(words, 0, words.indexOf('্') + 1, words.indexOf('্') + 1).wordAtCursor())
        assertEquals("স্কুলে", token.range.value)
    }

    @Test fun postEditWitnessChecksBothPositionAndNeighborsEvenAfterADeletion() {
        val before = TextSnapshot(paragraph, 0, first, last)
        val empty = requireNotNull(before.range(first, last)).after("")
        val cut = TextSnapshot(paragraph.removeRange(first, last), 0, first, first)
        assertTrue(empty.matches(cut))
        assertFalse(empty.matches(cut.copy(text = cut.text.replace("letters", "parcels"))))
        assertFalse(empty.matches(cut.copy(selectionStart = first - 1, selectionEnd = first - 1)))
    }

    @Test fun rejectedSegmentationSurvivesPersistenceAndKeepsItsOriginal() {
        val language = TypingLanguage.English
        val history = TypingHistory()
        assertTrue(history.reject(language, "iwantthis", "I want this"))
        assertTrue(TypingHistory(history.serialized()).profile(language, emptySet()).rejects("iwantthis", "i want this"))
        assertFalse(history.reject(language, "iwantthis", "I want http://test"))
    }

    @Test fun pairRankingRequiresRepeatedUseAndSurvivesPersistence() {
        val history = TypingHistory()
        history.recordPair(TypingLanguage.Banglish, "ekhane", "boisha")
        assertTrue(history.next(TypingLanguage.Banglish, "ekhane").isEmpty())
        history.recordPair(TypingLanguage.Banglish, "ekhane", "boisha")
        val saved = TypingHistory(history.serialized())
        assertEquals(mapOf("boisha" to 2), saved.next(TypingLanguage.Banglish, "ekhane"))
        assertTrue(saved.next(TypingLanguage.English, "ekhane").isEmpty())
    }
}
