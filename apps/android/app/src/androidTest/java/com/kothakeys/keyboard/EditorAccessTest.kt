package com.kothakeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorAccessTest {
    @Test fun passwordAndPrivateEditorsSuppressAiAndSuggestions() {
        for (type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_NULL
        )) {
            val traits = EditorTraits.from(EditorInfo().apply { inputType = type })
            assertTrue(traits.sensitive)
            assertFalse(traits.aiAllowed)
            assertFalse(traits.wordAssistance)
        }
        val private = EditorTraits.from(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        })
        assertTrue(private.sensitive)
        assertFalse(private.aiAllowed)
    }

    @Test fun standardEditorTraitsAllowAiAndHonorActionFlags() {
        val normal = EditorTraits.from(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
        })
        assertTrue(normal.aiAllowed)
        assertEquals(EditorInfo.IME_ACTION_SEND, normal.enterAction)
        val email = EditorTraits.from(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        })
        assertTrue(email.address)
        assertFalse(email.aiAllowed)
        assertEquals(EditorInfo.IME_ACTION_NONE, email.enterAction)
    }

    @Test fun graphemeDeletionKeepsBengaliAndEmojiSequencesTogether() {
        val editor = EditorAccess()
        for (cluster in listOf("কি", "ক্ষ", "ক্তি", "ক্ষ্ম", "😀", "👨‍👩‍👧", "🇧🇩", "❤️")) {
            assertEquals(cluster, cluster.length, editor.lastGraphemeLength("A" + cluster))
        }
    }
}
