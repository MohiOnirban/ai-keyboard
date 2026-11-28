package com.kothakeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePredictionTest {
    private val english = TypingLanguage.English
    private val model = OfflinePrediction(sequenceOf(
        "en\tI don't know",
        "en\tI don't mind",
        "en\tI didn't leave",
        "en\tI'm feeling better",
        "en\tI'm on my way",
        "en\tsee you soon",
        "en\tsee you tomorrow",
        "en\tsee you soon",
        "en\tthank you very much",
        "en\tare you very sure",
        "en\tare you very tired",
        "en\tI am going home",
        "en\tI am going to work",
        "en\tI am going to school",
        "en\tI was going home",
        "en\twe are going home",
        "en\thello there",
        "en\thello again",
        "en\thello everyone",
        "en\tthe event begins",
        "en\tthe exam ends",
        "en\twe go together",
        "en\twe go together",
        "banglish\tami tomake bhalobashi",
        "banglish\ttumi kortesi",
        "bn\tআমি তোমাকে ভালোবাসি",
    ))

    @Test fun contextualCorrectionPrefersContractionOverUnrelatedCompletion() {
        val context = OfflinePrediction.contextBefore("hey how ya doing? ")
        assertEquals(listOf("I'm", "im"), model.suggestions(english, "im", context).take(2))
        assertEquals("I'm", model.autocorrect(english, "im", context))
        assertEquals("don't", model.autocorrect(english, "dont", emptySet()))
        assertEquals("didn't", model.spelling(english, "didnt", emptySet()).first())
    }

    @Test fun attestedSubtitleFormsNeedContextBeforeAutomaticCorrection() {
        val withFrequency = OfflinePrediction(sequenceOf(
            "en\tI'm well", "en\tI'm leaving", "en\twe can't go", "en\twe can't stay",
        ), sequenceOf("en\tim\t6553", "en\tcant\t3936"))
        assertEquals("I'm", withFrequency.autocorrect(english, "im", PredictionContext(sentenceStart = true)))
        assertEquals("can't", withFrequency.autocorrect(english, "cant", PredictionContext("we")))
        assertNull(withFrequency.autocorrect(english, "cant", PredictionContext("ancient", "the")))
        assertTrue("can't" in withFrequency.suggestions(english, "cant"))
    }

    @Test fun supportsEditKindsAndPreservesVocabulary() {
        val withFrequency = OfflinePrediction(sequenceOf("en\twe receive mail"), sequenceOf(
            "en\treceive\t15000",
            "en\tbecause\t25000",
            "en\thello\t9000",
            "en\thelp\t5500",
            "en\twere\t15000",
            "en\twe're\t4500",
            "en\tworld\t7000",
        ))
        assertEquals("receive", withFrequency.autocorrect(english, "recieve", emptySet()))
        assertEquals("hello", withFrequency.suggestions(english, "helo").first())
        assertTrue("world" in withFrequency.suggestions(english, "wirld"))
        assertTrue("because" in withFrequency.suggestions(english, "becau"))
        assertNull(withFrequency.autocorrect(english, "were", emptySet()))
        assertNull(withFrequency.autocorrect(english, "wrod", setOf("Wrod")))
        assertNull(withFrequency.autocorrect(english, "CustomerName", emptySet()))
    }

    @Test fun triesAContextualSplitAndTrigramBeforeBigram() {
        assertEquals("see you", model.spelling(english, "seeyou", emptySet()).first())
        assertEquals("to", model.next(english, PredictionContext("going", "am")).first())
        assertEquals("soon", model.next(english, PredictionContext("you", "see")).first())
        assertEquals("bhalobashi", model.next(TypingLanguage.Banglish, PredictionContext("tomake", "ami")).first())
        assertEquals("ভালোবাসি", model.next(TypingLanguage.Bangla, PredictionContext("তোমাকে", "আমি")).first())
        assertEquals("home", model.next(english, "going").first())
    }

    @Test fun strongCompletionsCanLeadWhileTheOriginalStaysAvailable() {
        assertEquals(listOf("going", "goi"), model.suggestions(english, "goi", PredictionContext("am", "I")).take(2))
        assertEquals("go", model.suggestions(english, "go", PredictionContext("am", "I")).first())
        val personal = PredictionProfile(dictionary = setOf("NovaName"))
        assertEquals(listOf("NovaName", "nov"), model.suggestions(english, "nov", profile = personal).take(2))
    }

    @Test fun noisyEditNeighborsLeaveRoomForApostrophesAndCompletions() {
        val withFrequency = OfflinePrediction(sequenceOf("en\tyou're right", "en\tyou're welcome"), sequenceOf(
            "en\tyour\t4610945",
            "en\tyours\t140553",
            "en\tum\t201000",
            "en\tbum\t50320",
            "en\tumbrella\t5570",
        ))
        assertTrue("you're" in withFrequency.suggestions(english, "youre"))
        assertTrue("umbrella" in withFrequency.suggestions(english, "umb"))
    }

    @Test fun contextHonorsSentenceBoundariesAndSuppressesAddresses() {
        assertEquals(PredictionContext("going", "am"), OfflinePrediction.contextBefore("I am going "))
        assertEquals(PredictionContext(sentenceStart = true), OfflinePrediction.contextBefore("Hello. “"))
        assertEquals(PredictionContext("tumi"), OfflinePrediction.contextBefore("Hello! tumi "))
        assertTrue(OfflinePrediction.protectedTokenPrefix("visit https://example."))
        assertTrue(OfflinePrediction.protectedTokenPrefix("mail me+test@"))
    }

    @Test fun personalAcceptanceAndRejectionRerankWithoutSwitchingLanguages() {
        val history = TypingHistory()
        history.record(TypingLanguage.English, "NovaName", accepted = true)
        history.reject(TypingLanguage.English, "teh", "the")
        val englishProfile = history.profile(english, emptySet())
        assertTrue("NovaName" in model.suggestions(english, "nov", profile = englishProfile))
        assertNull(model.autocorrect(english, "teh", PredictionContext(), englishProfile))
        assertEquals("the", model.autocorrect(english, "teh", emptySet()))
        val banglish = TypingLanguage.Banglish
        assertEquals(listOf("kortesi"), model.suggestions(banglish, "kortesi", profile = history.profile(banglish, emptySet())))
        assertTrue("NovaName" !in model.suggestions(banglish, "nov", profile = history.profile(banglish, emptySet())))
        assertEquals(englishProfile, TypingHistory(history.serialized()).profile(english, emptySet()))
    }
}
