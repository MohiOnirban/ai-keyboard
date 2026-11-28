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

    @Test fun corpusPredictsCompletionsAndCorrectionsInEnglish() {
        val model = File(shared, "predict.tsv").useLines { OfflinePrediction(it) }
        assertEquals("the", model.autocorrect(TypingLanguage.English, "teh", emptySet()))
        assertEquals("The", model.autocorrect(TypingLanguage.English, "Teh", emptySet()))
        assertEquals("THE", model.autocorrect(TypingLanguage.English, "TEH", emptySet()))
        assertNull(model.autocorrect(TypingLanguage.English, "the", emptySet()))
        assertNull(model.autocorrect(TypingLanguage.English, "teh", setOf("Teh")))
        assertNull(model.autocorrect(TypingLanguage.Banglish, "teh", emptySet()))
        assertTrue(model.complete(TypingLanguage.English, "th", null, emptySet()).size in 1..3)
        assertTrue("you" in model.next(TypingLanguage.English, "thank"))
    }

    @Test fun predictionsKeepTheThreeLanguagesAndRankAfterContext() {
        val model = OfflinePrediction(sequenceOf(
            "en\tThank you for waiting",
            "en\tThank you for helping",
            "en\tThank goodness",
            "banglish\tAmi bhalo achi",
            "banglish\tAmi bari jabo",
            "bn\tআমি ভালো আছি",
            "bn\tআমি ভালো থাকি",
            "bn\tভাই বাড়ি যাও",
        ))
        assertEquals(listOf("you", "goodness"), model.next(TypingLanguage.English, "Thank"))
        assertEquals(listOf("bari", "bhalo"), model.next(TypingLanguage.Banglish, "ami"))
        assertTrue(model.next(TypingLanguage.English, "ami").isEmpty())
        assertEquals(listOf("ভালো"), model.next(TypingLanguage.Phonetic, "আমি"))
        assertEquals(listOf("ভালো", "ভাই"), model.complete(TypingLanguage.Bangla, "ভ", "আমি", emptySet()))
        assertEquals("Fahima", model.complete(TypingLanguage.English, "fa", null, setOf("Fahima", "ফারিহা")).first())
        assertEquals("ফারিহা", model.complete(TypingLanguage.Bangla, "ফা", null, setOf("Fahima", "ফারিহা")).first())
    }

    @Test fun dictionaryValidatesSingleWordsAndDoesNotReadAcrossPunctuation() {
        assertEquals("Fahima", OfflinePrediction.dictionaryWord(" Fahima "))
        assertEquals("খুশি", OfflinePrediction.dictionaryWord("খুশি"))
        assertNull(OfflinePrediction.dictionaryWord("hello world"))
        assertNull(OfflinePrediction.dictionaryWord("abcবাংলা"))
        assertEquals("ভালো", OfflinePrediction.previousWord("আমি ভালো "))
        assertNull(OfflinePrediction.previousWord("আমি ভালো। "))
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
