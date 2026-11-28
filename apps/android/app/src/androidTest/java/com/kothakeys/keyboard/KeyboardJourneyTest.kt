package com.kothakeys.keyboard

import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

open class KeyboardJourneyTest {
    private val startingLanguage: TypingLanguage?
        get() = InstrumentationRegistry.getArguments().getString("startingLayout")?.let(TypingLanguage::valueOf)

    private val shiftKey = "Shift. Double tap for caps lock."
    protected val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private val automation get() = instrumentation.uiAutomation

    @Test fun localKeyboardAndDictionaryWorkOffline() {
        setupKeyboard()
        command("svc wifi disable")
        command("svc data disable")
        try {
            focusPracticeField()
            selectLayout(TypingLanguage.English)

            type("teh", "Space")
            editedText("the ")
            clear()
            selectLayout(TypingLanguage.Bangla)
            clickKeyboardControl("ক")
            clickKeyboardControl("◌্")
            clickKeyboardControl("More Bangla letters")
            clickKeyboardControl("ষ")
            editedText("ক্ষ")
            clickKeyboardControl("Backspace")
            editedText("")

            selectLayout(TypingLanguage.Banglish)
            type("ami")
            editedText("ami")
            clear()
            openFeature("Emoji")
            clickKeyboardControl("🇧🇩")
            clickKeyboardControl("👨‍👩‍👧")
            editedText("🇧🇩👨‍👩‍👧")
            clickKeyboardControl("Backspace")
            editedText("🇧🇩")
            clickKeyboardControl("Backspace")
            editedText("")
        } finally {
            command("svc wifi enable")
            command("svc data enable")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }

    @Test fun phase11SuggestionsToolbarUndoAndDictionaryWorkOffline() {
        setupKeyboard()
        val settings = LocalSettings(instrumentation.targetContext)
        command("svc wifi disable")
        command("svc data disable")
        try {
            focusPracticeField()
            eventually("visible practice editor") {
                visibleRoots(false).firstNotNullOfOrNull { root ->
                    root.first { it.className == "android.widget.EditText" }
                }
            }
            selectLayout(TypingLanguage.English)
            val frame = keyboardFrame()
            type("teh")
            val toolbar = bounds(waitFor("Fix Grammar", keyboard = true))
            val candidate = bounds(waitFor("Suggestion the", keyboard = true))
            val q = bounds(waitFor("q", keyboard = true))
            assertTrue(toolbar.bottom <= candidate.top && candidate.bottom <= q.top)
            waitFor("Voice input", keyboard = true)
            assertEquals(frame, keyboardFrame())
            capture("25-suggestions-toolbar")

            clickKeyboardControl("Space")
            editedText("the ")
            clickKeyboardControl("Backspace")
            editedText("teh")
            setPracticeText("")
            type("teh", "Space")
            clickKeyboardControl("Undo")
            editedText("teh ")

            instrumentation.runOnMainSync { settings.correction = false }
            setPracticeText("")
            type("teh", "Space")
            editedText("teh ")
            setPracticeText("thank ")
            clickKeyboardControl("Suggestion you")
            editedText("thank you ")

            instrumentation.runOnMainSync {
                assertTrue(settings.saveDictionaryWord("Fahima"))
                assertTrue(settings.saveDictionaryWord("Fahimah", "Fahima"))
            }
            setPracticeText("")
            type("fah")
            clickKeyboardControl("Suggestion Fahimah")
            editedText("Fahimah")
            instrumentation.runOnMainSync { settings.removeDictionaryWord("Fahimah") }
            setPracticeText("")
            type("fah")
            assertNull(keyboardNode("Suggestion Fahimah"))
            clickKeyboardControl("Voice input")
            waitFor("Return to keyboard", keyboard = true)
            capture("26-voice")
            clickKeyboardControl("Return to keyboard")
            setPracticeText("")
            instrumentation.runOnMainSync {
                val field = checkNotNull(activity.window.decorView.practiceField())
                field.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).restartInput(field)
            }
            waitFor("Private field", keyboard = true)
            assertNull(keyboardNode("Fix Grammar"))
            assertNull(keyboardNode("Voice input"))
            type("teh", "Space")
            editedText("teh ")
            assertNull(keyboardNode("Suggestion the"))
            capture("27-private-field")
        } finally {
            instrumentation.runOnMainSync {
                settings.correction = true
                settings.removeDictionaryWord("Fahima")
                settings.removeDictionaryWord("Fahimah")
            }
            command("svc wifi enable")
            command("svc data enable")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }

    @Test fun phase2PersonalLearningControlsAndSecureFields() {
        setupKeyboard()
        val settings = LocalSettings(instrumentation.targetContext)
        focusPracticeField()
        try {
            instrumentation.runOnMainSync {
                settings.clearLearnedTyping()
                settings.learning = true
                settings.automaticCapitalization = true
            }
            setPracticeText("hey how ya doing? ")
            type("im")
            editedText("hey how ya doing? Im")
            waitFor("Suggestion I'm", keyboard = true)
            capture("33-contraction-suggestion")
            clickKeyboardControl("Space")
            editedText("hey how ya doing? I'm ")
            clickKeyboardControl("Undo")
            editedText("hey how ya doing? Im ")
            setPracticeText("hey how ya doing? ")
            type("im", "Space")
            editedText("hey how ya doing? Im ")

            instrumentation.runOnMainSync {
                settings.automaticCapitalization = false
                settings.correction = false
            }
            setPracticeText("")
            type("quandivox", "Space")
            type("quandivox", "Space")
            setPracticeText("")
            type("quandi")
            waitFor("Suggestion quandivox", keyboard = true)
            clickKeyboardControl("Suggestion quandivox")
            editedText("quandivox")
            val persisted = LocalSettings(instrumentation.targetContext)
            assertEquals(5, persisted.predictionProfile(TypingLanguage.English).learned["quandivox"])

            instrumentation.runOnMainSync { settings.learning = false }
            setPracticeText("")
            type("quandi")
            assertNull(keyboardNode("Suggestion quandivox"))
            clickKeyboardControl("Space")
            editedText("quandi ")
            instrumentation.runOnMainSync {
                settings.correction = true
                settings.nextWord = false
                settings.clearLearnedTyping()
            }
            setPracticeText("")
            type("thank", "Space")
            editedText("thank ")
            assertNull(keyboardNode("Suggestion you"))
            instrumentation.runOnMainSync { settings.nextWord = true }
            setPracticeText("")
            type("thank", "Space")
            editedText("thank ")
            waitFor("Suggestion you", keyboard = true)

            instrumentation.runOnMainSync { settings.learning = true }
            setPracticeText("https://example.")
            type("teh", "Space")
            editedText("https://example.teh ")
            assertFalse(persisted.hasLearnedTyping)
            setPracticeText("")
            instrumentation.runOnMainSync {
                val field = checkNotNull(activity.window.decorView.practiceField())
                field.imeOptions = field.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).restartInput(field)
            }
            waitFor("Private field", keyboard = true)
            type("privateword", "Space")
            editedText("privateword ")
            assertFalse(persisted.hasLearnedTyping)
        } finally {
            instrumentation.runOnMainSync {
                settings.clearLearnedTyping()
                settings.learning = false
                settings.correction = true
                settings.nextWord = true
                settings.automaticCapitalization = false
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }

    @Test fun requestedPhraseAndPrefixCompletionWorkOffline() {
        setupKeyboard()
        command("svc wifi disable")
        command("svc data disable")
        try {
            focusPracticeField()
            selectLayout(TypingLanguage.Phonetic)
            touchKey(shiftKey)
            waitFor("A", keyboard = true)
            editedText("")
            touchKey("A")
            waitFor("a", keyboard = true)
            type("mi", "Space", physical = true)
            type("tomak", physical = true)
            touchKey("Suggestion তোমাকে")
            touchKey("Space")
            type("bhalobashi", "Space", physical = true)
            editedText("আমি তোমাকে ভালোবাসি ")
        } finally {
            command("svc wifi enable")
            command("svc data enable")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }

    @Test fun previewReplacesMatchingDraftAndPreservesChangedSelection() {
        assumeTrue("requires the default mock backend", BuildConfig.BACKEND_URL == "http://10.0.2.2:4000")
        assumeTrue("set kothaMock=true for a local mock server", InstrumentationRegistry.getArguments().getString("kothaMock") == "true")
        setupKeyboard()
        command("svc wifi enable")
        command("svc data enable")
        focusPracticeField()
        selectLayout(TypingLanguage.English)
        type("hello")
        openFeature("Ask AI")
        clickKeyboardControl("Generate")
        waitFor("Replace Text", keyboard = true)
        val suggestion = previewText()
        clickKeyboardControl("Replace Text")
        editedText(suggestion)

        selectAllFromTools()
        openFeature("Ask AI")
        clickKeyboardControl("Generate")
        waitFor("Replace Text", keyboard = true)
        setPracticeText("Changed draft")
        clickKeyboardControl("Replace Text")
        waitFor("Insert safely", keyboard = true)
        clickKeyboardControl("Return to keyboard")
        editedText("Changed draft")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    protected fun setupKeyboard() {
        assumeTrue("requires an isolated Android 35 or newer emulator", Build.HARDWARE.contains("ranchu") && Build.VERSION.SDK_INT >= 35)
        val flags = automation.serviceInfo
        flags.flags = flags.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = flags
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            LocalSettings(context).apply {
                typingLanguage = startingLanguage ?: TypingLanguage.English
                correction = true
                suggestions = true
                nextWord = true
                automaticCapitalization = false
                learning = false
                theme = "Light"
                keyboardHeight = KeyboardHeight.Standard
            }
        }
        if (InstrumentationRegistry.getArguments().getString("kothaMock") == "true" &&
            BuildConfig.BACKEND_URL == "http://10.0.2.2:4000") {
            val ready = CountDownLatch(1)
            val client = AiClient.get(context)
            client.setBackendOverride(null)
            client.usage { ready.countDown() }
            assertTrue("mock session ready", ready.await(12, TimeUnit.SECONDS))
        }
        val component = ComponentName(context, KothaIme::class.java).flattenToShortString()
        command("settings put secure show_ime_with_hard_keyboard 1")
        command("ime enable $component")
        command("ime set $component")
        val launch = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity = instrumentation.startActivitySync(launch) as MainActivity
        if (InstrumentationRegistry.getArguments().getString("viewportTest") == "true") {
            instrumentation.runOnMainSync {
                val host = android.widget.FrameLayout(activity)
                val field = EditText(activity).apply {
                    hint = "Synthetic draft"
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
                host.addView(field, android.widget.FrameLayout.LayoutParams(-1, -2))
                activity.setContentView(host)
            }
        }
        val selected = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
        )
        assertEquals(ComponentName.unflattenFromString(component), ComponentName.unflattenFromString(selected))
    }

    protected fun selectEnglishLayout() = selectLayout(TypingLanguage.English)

    protected fun previewText(): String = eventually("generated suggestion") {
        visibleRoots(true).firstNotNullOfOrNull { root ->
            root.first { it.viewIdResourceName?.endsWith(":id/ai_suggestion") == true }?.text?.toString()
        }?.takeIf(String::isNotBlank)
    }

    protected fun openFeature(name: String) {
        if (keyboardNode(name) == null) clickKeyboardControl("Tools menu")
        clickKeyboardControl(name)
    }

    private fun setPracticeText(value: String) {
        instrumentation.runOnMainSync {
            val field = checkNotNull(activity.window.decorView.practiceField())
            field.text.replace(0, field.text.length, value)
            field.setSelection(value.length)
        }
        editedText(value)
    }

    private fun keyboardFrame(): Rect {
        val display = if (Build.VERSION.SDK_INT >= 30) activity.windowManager.maximumWindowMetrics.bounds else null
        fun sample() = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { Rect().also(it::getBoundsInScreen) }
            ?.takeIf { display == null || it.bottom <= display.bottom }
        return eventually("settled keyboard frame") {
            val first = sample() ?: return@eventually null
            SystemClock.sleep(125)
            if (sample() != first) return@eventually null
            SystemClock.sleep(125)
            sample()?.takeIf { it == first }
        }
    }

    private fun capture(name: String) {
        val arguments = InstrumentationRegistry.getArguments()
        val folder = when {
            arguments.getString("phase2Evidence") == "true" -> "phase2"
            arguments.getString("geometryEvidence") == "true" -> "geometry"
            arguments.getString("phase11Evidence") == "true" -> "phase11"
            arguments.getString("phase1Evidence") == "true" -> "phase1"
            else -> return
        }
        val dir = checkNotNull(instrumentation.targetContext.getExternalFilesDir(folder))
        val bitmap = checkNotNull(automation.takeScreenshot())
        try {
            File(dir, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun urlKeyboardKeepsReferenceGeometryAndEditorAction() {
        setupKeyboard()
        focusPracticeField()
        selectLayout(TypingLanguage.English)
        val editorAction = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val field = checkNotNull(activity.window.decorView.practiceField())
            field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            field.setSingleLine(true)
            field.imeOptions = EditorInfo.IME_ACTION_DONE
            field.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    editorAction.countDown()
                    true
                } else false
            }
            activity.getSystemService(InputMethodManager::class.java).restartInput(field)
        }
        waitFor("Email / URL", keyboard = true)
        val (q, a) = eventually("adjacent URL rows in the same IME frame") {
            val top = keyboardNode("q")?.let(::bounds) ?: return@eventually null
            val middle = keyboardNode("a")?.let(::bounds) ?: return@eventually null
            (top to middle).takeIf { top.bottom == middle.top }
        }
        val space = settledKeyBounds("Space")
        val emoji = settledKeyBounds("Emoji shortcut")
        val slash = settledKeyBounds("/")
        val done = settledKeyBounds("Enter: Done")
        capture("url-standard-reference")
        assertNull(keyboardNode("Fix Grammar"))
        assertTrue("row pitch is close to 85 units per 600 units of width: q=$q a=$a",
            abs((a.top - q.top) / q.width().toFloat() - 85f / 60) < 0.08f)
        assertTrue("spacebar spans four regular columns", abs(space.width() - q.width() * 4) <= 5)
        assertTrue("context action spans one and a half columns", abs(done.width() * 2 - q.width() * 3) <= 5)
        assertTrue("emoji is between slash and space", slash.right == emoji.left && emoji.right == space.left)
        if (InstrumentationRegistry.getArguments().getString("geometryPlatformReference") == "true") {
            val packageName = "com.google.android.inputmethod.latin"
            val component = "$packageName/com.android.inputmethod.latin.LatinIME"
            val ours = ComponentName(instrumentation.targetContext, KothaIme::class.java).flattenToShortString()
            try {
                command("ime set $component")
                eventually("reference keyboard shown") {
                    visibleRoots(true).firstOrNull { it.packageName?.toString() == packageName }
                }
                SystemClock.sleep(750)
                capture("url-gboard-same-emulator")
            } finally {
                command("ime set $ours")
                waitFor("Emoji shortcut", keyboard = true)
            }
        }
        clickKeyboardControl("Emoji shortcut")
        waitFor("🇧🇩", keyboard = true)
        clickKeyboardControl("Return to keyboard")
        clickKeyboardControl("Enter: Done")
        assertTrue("Done dispatches the host action", editorAction.await(2, TimeUnit.SECONDS))
    }

    @Test fun phaseOneGridAndModeHeight() {
        setupKeyboard()
        focusPracticeField()
        selectLayout(TypingLanguage.English)
        assertTrue("long press Space opens the language chooser",
            waitFor("Space", keyboard = true).performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
        waitFor("Raw Banglish", keyboard = true)
        clickKeyboardControl("English")
        waitFor("q", keyboard = true)
        val frame = keyboardFrame()
        val q = bounds(waitFor("q", keyboard = true))
        val a = bounds(waitFor("a", keyboard = true))
        val z = bounds(waitFor("z", keyboard = true))
        val shift = bounds(waitFor(shiftKey, keyboard = true))
        val delete = bounds(waitFor("Backspace", keyboard = true))
        assertTrue("regular letter widths match across alphabet rows", abs(q.width() - a.width()) <= 1 && abs(q.width() - z.width()) <= 1)
        assertTrue("second row is offset by half a key", abs((a.left - q.left) * 2 - q.width()) <= 3)
        assertTrue("third row is offset by its wider shift key", abs(z.left - shift.right) <= 1 && shift.width() > z.width())
        assertTrue("left and right side keys are equally wide", abs(shift.width() - delete.width()) <= 2)
        capture("01-english-alphabet")

        setPracticeText("Keep this draft")
        for (feature in listOf("Fix Grammar", "Improve & Tone", "Ask AI", "Translation", "Emoji", "Clipboard")) {
            openFeature(feature)
            waitFor("Return to keyboard", keyboard = true)
            assertNull("no host letter keys in $feature", keyboardNode("q"))
            assertEquals("fixed keyboard bounds in $feature", frame, keyboardFrame())
            editedText("Keep this draft")
            val output = mapOf("Fix Grammar" to "07-fix-grammar", "Improve & Tone" to "08-improve-tone",
                "Ask AI" to "09-ask-ai", "Translation" to "10-translation",
                "Emoji" to "11-emoji", "Clipboard" to "12-clipboard")
            capture(output.getValue(feature))
            clickKeyboardControl("Return to keyboard")
            waitFor("q", keyboard = true)
        }
        setPracticeText("")
        clickKeyboardControl("Toggle numbers and symbols")
        val symbolPitch = settledKeyBounds("@").top - settledKeyBounds("1").top
        assertTrue("symbols and alphabet share a row pitch", abs(a.top - q.top - symbolPitch) <= 1)
        capture("04-symbols")
        clickKeyboardControl("More symbols")
        waitFor("Primary symbols", keyboard = true)
        capture("05-secondary-symbols")
        clickKeyboardControl("Numeric keypad")
        val zero = settledKeyBounds("0")
        val two = settledKeyBounds("2")
        assertTrue("zero aligns under the middle numeric column", abs(zero.centerX() - two.centerX()) <= 1)
        assertTrue("number and symbol rows share a pitch", abs(symbolPitch - settledKeyBounds("5").top + two.top) <= 1)
        assertEquals(frame, keyboardFrame())
        capture("06-numeric")
        clickKeyboardControl("Alphabet layout")
        selectLayout(TypingLanguage.Phonetic)
        capture("02-bangla-phonetic")
        selectLayout(TypingLanguage.Bangla)
        capture("03-direct-bangla")
        selectLayout(TypingLanguage.Banglish)
        capture("13-raw-banglish")
    }

    @Test fun phaseTwoToolsAndNumericEntry() {
        setupKeyboard()
        focusPracticeField()
        selectLayout(TypingLanguage.English)
        val frame = keyboardFrame()
        val toolbarFeatures = listOf("Tools menu", "Fix Grammar", "Improve & Tone", "Ask AI",
            "Translation", "Emoji", "Clipboard", "Voice input")
        val visibleFeatures = toolbarFeatures.mapNotNull { keyboardNode(it)?.let(::bounds) }
        assertTrue("toolbar follows the tools, AI, utility and voice order",
            visibleFeatures.zipWithNext().all { (left, right) -> left.right <= right.left })
        clickKeyboardControl("Toggle numbers and symbols")
        clickKeyboardControl("More symbols")
        clickKeyboardControl("Tools menu")
        assertEquals(frame, keyboardFrame())
        val tools = listOf("Theme", "Clipboard", "Emoji", "Typing language", "Text editing",
            "Keyboard layout", "Voice input", "Settings").map(::settledKeyBounds)
        tools.chunked(2).forEach { (left, right) ->
            assertEquals(left.top, right.top)
            assertTrue(left.right <= right.left && abs(left.width() - right.width()) <= 1)
        }
        capture("31-tools")
        clickKeyboardControl("Close tools")
        clickKeyboardControl("Numeric keypad")
        for (key in listOf("1", "Plus", "2", "Minus", "3", "Multiply", "4", "Divide", "5",
            "Percent", "Space", "0", "Equals", "6", "Comma", "Decimal point", "Backspace")) {
            clickKeyboardControl(key)
        }
        editedText("1+2-3*4/5% 0=6,")
        clickKeyboardControl("Symbol layout")
        waitFor("More symbols", keyboard = true)
        clickKeyboardControl("Tools menu")
        clickKeyboardControl("Keyboard layout")
        clickKeyboardControl("Alphabet")
        waitFor("q", keyboard = true)
        setPracticeText("ab")
        clickKeyboardControl("Tools menu")
        clickKeyboardControl("Text editing")
        clickKeyboardControl("Move cursor left")
        clickKeyboardControl("Back to tools")
        clickKeyboardControl("Close tools")
        clickKeyboardControl("x")
        editedText("axb")
        setPracticeText("")
        val q = waitFor("q", keyboard = true)
        assertTrue(q.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
        editedText("1")

        instrumentation.runOnMainSync {
            activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Synthetic clipboard", " synthetic clipboard"))
        }
        clickKeyboardControl("Tools menu")
        clickKeyboardControl("Clipboard")
        clickKeyboardControl("Paste clipboard")
        editedText("1 synthetic clipboard")
        instrumentation.runOnMainSync { activity.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
        clickKeyboardControl("Back to tools")
        clickKeyboardControl("Theme")
        clickKeyboardControl("Dark")
        assertEquals("Dark", LocalSettings(instrumentation.targetContext).theme)
        clickKeyboardControl("Light")
        clickKeyboardControl("Back to tools")
        clickKeyboardControl("Settings")
        waitFor("Free daily AI usage")
    }

    @Test fun phaseTwoHandsOffToAndroidVoiceProvider() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("phase2Voice") == "true")
        setupKeyboard()
        focusPracticeField()
        val manager = instrumentation.targetContext.getSystemService(InputMethodManager::class.java)
        val voicePackages = manager.enabledInputMethodList.filter { method ->
            manager.getEnabledInputMethodSubtypeList(method, true).any { it.mode.equals("voice", true) }
        }.map { it.packageName }.toSet()
        assertTrue("test emulator has an enabled Android voice provider", voicePackages.isNotEmpty())
        try {
            openFeature("Voice input")
            clickKeyboardControl("Start voice input")
            val provider = eventually("Android voice provider window") {
                visibleRoots(true).firstOrNull { it.packageName?.toString() in voicePackages }
            }
            assertTrue(provider.packageName?.toString() in voicePackages)
            capture("32-android-voice-provider")
        } finally {
            val component = ComponentName(instrumentation.targetContext, KothaIme::class.java).flattenToShortString()
            command("ime set $component")
        }
    }

    @Test fun phaseOneAiPanelsUseMockWithoutOverwritingDraft() {
        val arguments = InstrumentationRegistry.getArguments()
        val delayedMock = arguments.getString("phase1LoadingEvidence") == "true" && BuildConfig.BACKEND_URL == "http://127.0.0.1:4133"
        assumeTrue(BuildConfig.BACKEND_URL == "http://10.0.2.2:4000" || delayedMock)
        assumeTrue(arguments.getString("kothaMock") == "true")
        setupKeyboard()
        val settings = LocalSettings(instrumentation.targetContext)
        instrumentation.runOnMainSync { settings.revokeQuickAi() }
        focusPracticeField()
        val frame = keyboardFrame()

        setPracticeText("im on teh bus")
        openFeature("Fix Grammar")
        waitFor("Allow and continue", keyboard = true)
        capture("14-fix-grammar-consent")
        clickKeyboardControl("Not now")
        assertNull(keyboardNode("Replace Text"))
        assertEquals("im on teh bus", practiceText())
        clickKeyboardControl("Return to keyboard")
        openFeature("Fix Grammar")
        clickWithoutDelay("Allow and continue")
        captureLoading("Checking grammar…", "14-fix-grammar-loading")
        assertEquals(frame, keyboardFrame())
        waitFor("Replace Text", keyboard = true)
        assertEquals("I'm on the bus.", previewText())
        assertEquals("im on teh bus", practiceText())
        capture("15-fix-grammar-preview")
        clickKeyboardControl("Replace Text")
        editedText("I'm on the bus.")

        setPracticeText("im on teh bus")
        openFeature("Fix Grammar")
        waitFor("Replace Text", keyboard = true)
        assertNull(keyboardNode("Allow and continue"))
        clickKeyboardControl("Return to keyboard")
        setPracticeText("can you check this today?")
        openFeature("Improve & Tone")
        clickWithoutDelay("Professional")
        captureLoading("Generating…", "16-improve-tone-loading")
        waitFor("Replace Text", keyboard = true)
        assertTrue(previewText().isNotBlank())
        assertEquals("can you check this today?", practiceText())
        capture("17-improve-tone-preview")
        clickKeyboardControl("Return to keyboard")

        setPracticeText("How are you?")
        openFeature("Translation")
        waitFor("Bangla", keyboard = true)
        clickWithoutDelay("Translate")
        captureLoading("Translating…", "18-translation-loading")
        waitFor("Replace Text", keyboard = true)
        assertEquals("তুমি কেমন আছো?", previewText())
        assertEquals("How are you?", practiceText())
        capture("19-translation-preview")
        clickKeyboardControl("Return to keyboard")

        openFeature("Ask AI")
        clickKeyboardControl("Express")
        clickKeyboardControl("Type input")
        type("hello")
        clickKeyboardControl("Done editing")
        clickWithoutDelay("Generate")
        captureLoading("Generating…", "20-ask-ai-loading")
        waitFor("Insert", keyboard = true)
        assertTrue(previewText().isNotBlank())
        assertEquals("How are you?", practiceText())
        capture("21-ask-ai-preview")
    }

    @Test fun usbGrammarPreservesDraft() {
        assumeTrue("opt in with kothaUsbGrammar=true", InstrumentationRegistry.getArguments().getString("kothaUsbGrammar") == "true")
        setupKeyboard()
        val context = instrumentation.targetContext
        val settings = LocalSettings(context)
        val client = AiClient.get(context)
        val originalOverride = client.backendOverride()
        val originalEndpoint = client.configuredBackend()
        val originalConsent = settings.quickAiConsentFor(originalEndpoint)
        val draft = "she have two blue pens"
        try {
            instrumentation.runOnMainSync {
                client.useUsbBackend()
                settings.revokeQuickAi()
            }
            val ready = CountDownLatch(1)
            var failure: Throwable? = null
            client.usage { result ->
                failure = result.exceptionOrNull()
                ready.countDown()
            }
            assertTrue("USB usage preflight completed", ready.await(20, TimeUnit.SECONDS))
            assertNull("USB usage preflight failed", failure)
            focusPracticeField()
            selectEnglishLayout()
            val words = draft.split(' ')
            words.forEachIndexed { index, word ->
                type(word, if (index == words.lastIndex) null else "Space")
            }
            editedText(draft)
            openFeature("Fix Grammar")
            waitFor("Allow and continue", keyboard = true)
            capture("28-usb-grammar-consent")
            clickWithoutDelay("Allow and continue")
            waitFor("Replace Text", keyboard = true, timeoutMillis = 65_000)
            val suggestion = previewText()
            assertTrue(suggestion.isNotBlank())
            assertEquals(draft, practiceText())
            capture("29-usb-grammar-preview")
            clickKeyboardControl("Replace Text")
            editedText(suggestion)
            capture("30-usb-grammar-applied")
        } finally {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.runOnMainSync {
                if (originalOverride == AiClient.USB_BACKEND) client.useUsbBackend()
                else client.setBackendOverride(originalOverride)
                settings.revokeQuickAi()
                if (originalConsent) settings.allowQuickAi(originalEndpoint)
            }
        }
    }

    @Test fun phaseOneDarkThemeAndHeightPreference() {
        setupKeyboard()
        val settings = LocalSettings(instrumentation.targetContext)
        try {
            instrumentation.runOnMainSync {
                settings.theme = "Dark"
                settings.keyboardHeight = KeyboardHeight.Tall
            }
            focusPracticeField()
            val tall = keyboardFrame()
            capture("22-dark-alphabet")
            openFeature("Ask AI")
            assertEquals(tall, keyboardFrame())
            capture("23-dark-ask-ai")
            clickKeyboardControl("Return to keyboard")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            eventually("keyboard closed") { true.takeIf { keyboardNode("Ask AI") == null } }
            instrumentation.runOnMainSync { settings.keyboardHeight = KeyboardHeight.Compact }
            focusPracticeField()
            assertTrue("compact keyboard is shorter", keyboardFrame().height() < tall.height())
            capture("24-dark-compact")
        } finally {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.runOnMainSync {
                settings.theme = "Light"
                settings.keyboardHeight = KeyboardHeight.Standard
            }
        }
    }

    private fun captureLoading(label: String, filename: String) {
        if (InstrumentationRegistry.getArguments().getString("phase1LoadingEvidence") == "true") {
            waitFor(label, keyboard = true, timeoutMillis = 6_000)
            capture(filename)
        }
    }

    private fun clickWithoutDelay(label: String) {
        val button = waitFor(label, keyboard = true)
        assertTrue("activated keyboard control $label", button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    protected fun focusPracticeField() {
        eventually("practice editor and visible keyboard") {
            if (keyboardNode("Ask AI") != null) return@eventually true
            instrumentation.runOnMainSync {
                val field = activity.window.decorView.practiceField()
                if (field?.hasWindowFocus() == true && field.requestFocus()) {
                    WindowCompat.getInsetsController(activity.window, field).show(WindowInsetsCompat.Type.ime())
                }
            }
            null
        }
        startingLanguage?.let { waitFor(it.label, keyboard = true) }
    }

    protected fun practiceText(): String {
        var value: String? = null
        instrumentation.runOnMainSync {
            value = activity.window.decorView.practiceField()?.text?.toString()
        }
        return checkNotNull(value) { "Practice field was unavailable" }
    }

    private fun View.practiceField(): EditText? {
        if (this is EditText) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).practiceField()?.let { return it }
        }
        return null
    }

    private fun selectLayout(desired: TypingLanguage) {
        if (keyboardNode(desired.label) == null) {
            clickKeyboardControl("Tools menu")
            clickKeyboardControl("Typing language")
            val labels = listOf("English", "Direct Bangla", "Bangla phonetic", "Raw Banglish")
            clickKeyboardControl(labels[desired.ordinal])
            waitFor(desired.label, keyboard = true)
        }
        editedText("")
    }

    private fun command(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun type(letters: String, suffix: String? = null, physical: Boolean = false) {
        val keys = letters.map(Char::toString) + listOfNotNull(suffix)
        keys.forEach { label -> if (physical) touchKey(label) else clickKeyboardControl(label) }
    }

    private fun selectAll() {
        clickKeyboardControl("Select all")
        eventually("entire draft selected") {
            var selected = false
            instrumentation.runOnMainSync {
                val field = checkNotNull(activity.window.decorView.practiceField())
                selected = field.length() > 0 && field.selectionStart == 0 && field.selectionEnd == field.length()
            }
            true.takeIf { selected }
        }
    }

    private fun selectAllFromTools() {
        clickKeyboardControl("Tools menu")
        clickKeyboardControl("Text editing")
        selectAll()
        clickKeyboardControl("Back to tools")
        clickKeyboardControl("Close tools")
    }

    private fun clear() {
        selectAllFromTools()
        clickKeyboardControl("Backspace")
        editedText("")
    }

    protected fun editedText(expected: String) {
        eventually("editor text $expected") { true.takeIf { practiceText() == expected } }
    }

    protected fun touchKey(label: String) = touch(label, settledKeyBounds(label))

    private fun touch(label: String, rect: Rect) {
        assertTrue("empty control bounds for $label", !rect.isEmpty)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0).also { event ->
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue("touch was injected for $label", automation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
        }
        SystemClock.sleep(150)
    }

    protected fun clickKeyboardControl(label: String) {
        val button = waitFor(label, keyboard = true)
        assertTrue("activated keyboard control $label", button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(150)
    }

    private fun settledKeyBounds(label: String): Rect {
        // A mode change can publish new nodes before the resized IME has settled on screen.
        data class KeyFrame(val window: Rect, val key: Rect, val space: Rect?)
        fun snapshot(): KeyFrame? {
            val window = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
            val root = window.root ?: return null
            val key = root.first { it.matches(label) } ?: return null
            val space = root.first { it.contentDescription?.toString() == "Space" }?.let(::bounds)
            return KeyFrame(Rect().also(window::getBoundsInScreen), bounds(key), space)
        }

        return eventually("stable screen bounds for $label") {
            val first = snapshot() ?: return@eventually null
            SystemClock.sleep(150)
            val second = snapshot()
            if (first != second) return@eventually null
            SystemClock.sleep(150)
            snapshot()?.takeIf { first == it }?.key
        }
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    private fun keyboardNode(label: String) = find(label, true)
    protected fun waitFor(label: String, keyboard: Boolean = false, timeoutMillis: Long = 12_000) =
        eventually(label, timeoutMillis) { find(label, keyboard) }

    private fun find(label: String, keyboard: Boolean) = visibleRoots(keyboard).firstNotNullOfOrNull { root ->
        root.first { it.matches(label) }
    }

    private fun AccessibilityNodeInfo.matches(label: String) =
        text?.toString() == label || contentDescription?.toString() == label ||
            (Build.VERSION.SDK_INT >= 30 && stateDescription?.toString() == label)

    private fun visibleRoots(keyboard: Boolean) = automation.windows.filter {
        it.type == if (keyboard) AccessibilityWindowInfo.TYPE_INPUT_METHOD else AccessibilityWindowInfo.TYPE_APPLICATION
    }.mapNotNull(AccessibilityWindowInfo::getRoot)

    private fun AccessibilityNodeInfo.first(includeHidden: Boolean = false, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if ((includeHidden || isVisibleToUser) && match(this)) return this
        for (index in 0 until childCount) {
            getChild(index)?.first(includeHidden, match)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.activeLabels(): List<String> = buildList {
        if (!isVisibleToUser) return@buildList
        (contentDescription ?: text)?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        for (index in 0 until childCount) addAll(getChild(index)?.activeLabels().orEmpty())
    }

    private fun <T : Any> eventually(label: String, timeoutMillis: Long = 12_000, read: () -> T?): T {
        val end = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < end) {
            read()?.let { return it }
            SystemClock.sleep(120)
        }
        val app = visibleRoots(false).flatMap { it.activeLabels() }.take(25)
        val keyboard = visibleRoots(true).flatMap { it.activeLabels() }.take(16)
        throw AssertionError("Could not find $label. App: $app. Keyboard: $keyboard")
    }
}
