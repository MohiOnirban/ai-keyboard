package com.kothakeys.keyboard

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class RealProviderKeyboardTest : KeyboardJourneyTest() {
    @Test fun syntheticDraftRequiresExplicitAcceptance() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("explicit opt-in required", arguments.getString("kothaLiveSmoke") == "true")
        val testEndpoint = when (arguments.getString("kothaSmokeProvider")) {
            "mock" -> "http://10.0.2.2:4000"
            "live" -> "http://127.0.0.1:4012"
            else -> null
        }
        assumeTrue("use the selected isolated development listener", BuildConfig.DEBUG && BuildConfig.BACKEND_URL == testEndpoint)

        setupKeyboard()
        focusPracticeField()
        selectEnglishLayout()
        val draft = "can we move our study session to friday"
        draft.forEach { touchKey(if (it == ' ') "Space" else it.toString()) }
        editedText(draft)

        clickKeyboardControl("Open AI assistant")
        assertEquals(draft, practiceText())
        clickKeyboardControl("Generate")
        waitFor("Replace", keyboard = true, timeoutMillis = 75_000)
        val preview = previewText()
        assertEquals("preview preserves the editor before acceptance", draft, practiceText())

        clickKeyboardControl("Replace")
        editedText(preview)
        assertEquals("the accepted editor text matches the preview", preview, practiceText())
    }
}
