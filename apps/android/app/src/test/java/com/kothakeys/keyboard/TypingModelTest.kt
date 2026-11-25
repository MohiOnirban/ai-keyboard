package com.kothakeys.keyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TypingModelTest {
    private val shared = generateSequence(File(".").canonicalFile) { it.parentFile }
        .map { File(it, "shared") }.first { File(it, "phonetic.json").isFile }
    private val avro = Avro(JSONObject(File(shared, "avro/grammar.json").readText()))
    private val phonetics = Phonetics(JSONObject(File(shared, "phonetic.json").readText()), avro)

    @Test fun practicalBanglishPhraseUsesCommonSpellings() {
        val phrase = "ami bhalo achi".split(' ').joinToString(" ") { phonetics.candidates(it).first() }
        assertEquals("আমি ভালো আছি", phrase)
        assertEquals(listOf("ভালো", "ভাল", "bhalo"), phonetics.candidates("bhalo"))
    }

    @Test fun dictionaryCompletesPrefixAndKeepsCapitalizedPracticalPhrase() {
        val phrase = "Ami tomake bhalobashi".split(' ').joinToString(" ") { phonetics.candidates(it).first() }
        assertEquals("আমি তোমাকে ভালোবাসি", phrase)
        val candidates = phonetics.candidates("tomak")
        assertTrue("তোমাকে" in candidates)
        assertTrue("tomak" in candidates)
        assertTrue(candidates.size <= 3)
    }

    @Test fun avroUsesContextualVowelsAndPreservesCase() {
        assertEquals("টা", avro.render("Ta"))
        assertEquals("তা", avro.render("ta"))
        assertEquals("ক্ষ", phonetics.candidates("kSh").first())
        assertEquals("সৃষ্টি", phonetics.candidates("srriShTi").first())
        assertEquals("বিক্ষুব্ধ", phonetics.candidates("bikkhubdho").first())
        assertEquals("স্নেহ", phonetics.candidates("sneho").first())
    }

    @Test fun commonTypingErrorsKeepsCaseAndLimitsCandidates() {
        assertEquals("the", englishCorrection("teh"))
        assertEquals("The", englishCorrection("Teh"))
        assertEquals("THE", englishCorrection("TEH"))
        assertNull(englishCorrection("the"))
        assertTrue(englishCandidates("th").size <= 3)
        assertTrue("thanks" in englishCandidates("th"))
    }

    @Test fun selectionCapturesOnlyExplicitSelectedText() {
        val snapshot = TextSnapshot("prefix লক্ষ্য suffix", 12, 13, 7)
        val target = checkNotNull(snapshot.target())
        assertEquals("লক্ষ্য", target.text)
        assertEquals(19, target.start)
        assertEquals(25, target.end)
        assertTrue(target.stillMatches(snapshot.copy()))
        assertFalse(target.stillMatches(snapshot.copy(text = "prefix লক্ষ্ম suffix")))
        assertFalse(target.stillMatches(snapshot.copy(selectionStart = 7)))
        assertFalse(target.stillMatches(snapshot.copy(offset = 13)))
    }

    @Test fun inferredDraftRequiresBoundedFullPrefixAndEndCaret() {
        assertEquals("draft", TextSnapshot("draft", 0, 5, 5).target()?.text)
        assertNull(TextSnapshot("draft", 4, 5, 5).target())
        assertNull(TextSnapshot("draft", 0, 4, 4).target())
        assertNull(TextSnapshot("draft", 0, 5, 5).target(draftAllowed = false))
        assertNull(TextSnapshot(" ".repeat(4), 0, 4, 4).target())
        val long = "a".repeat(1601)
        assertNull(TextSnapshot(long, 0, 1601, 1601).target())
        assertNull(TextSnapshot(long, 0, 0, 1601).target())
    }
}
