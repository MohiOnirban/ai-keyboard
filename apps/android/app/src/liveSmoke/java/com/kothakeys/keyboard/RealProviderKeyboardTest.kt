package com.kothakeys.keyboard

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class RealProviderKeyboardTest : KeyboardJourneyTest() {
    @Test fun syntheticDraftRequiresExplicitAcceptance() {
        assumeTrue("explicit opt-in required", InstrumentationRegistry.getArguments().getString("kothaLiveSmoke") == "true")
        assumeTrue("use the isolated development listener", BuildConfig.DEBUG && BuildConfig.BACKEND_URL == "http://10.0.2.2:4012")

        val activity = setupKeyboard()
        focusPracticeField()
        selectEnglishLayout()
        val draft = "can we move our study session to friday"
        draft.forEach { tap(if (it == ' ') "Space" else it.toString(), keyboard = true) }
        editedText(draft)

        tap("Open AI assistant", keyboard = true)
        assertEquals(draft, practiceText(activity))
        tap("Generate", keyboard = true)
        waitFor("Replace", keyboard = true, timeoutMillis = 30_000)
        val preview = previewText()
        assertEquals("preview preserves the editor before acceptance", draft, practiceText(activity))

        tap("Replace", keyboard = true)
        editedText(preview)
        assertEquals("the accepted editor text matches the preview", preview, practiceText(activity))
    }

    private fun practiceText(activity: MainActivity): String {
        var value: String? = null
        instrumentation.runOnMainSync {
            value = activity.window.decorView.findPracticeField()?.text?.toString()
        }
        return checkNotNull(value) { "Practice field was unavailable" }
    }

    private fun View.findPracticeField(): EditText? {
        if (this is EditText) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).findPracticeField()?.let { return it }
        }
        return null
    }
}
