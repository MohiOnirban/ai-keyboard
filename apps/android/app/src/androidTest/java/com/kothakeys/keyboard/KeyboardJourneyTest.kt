package com.kothakeys.keyboard

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
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
            touchKey("তোমাকে")
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

        showToolbar()
        clickKeyboardControl("More options")
        selectAll()
        clickKeyboardControl("Return to keyboard")
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
        assumeTrue("requires an isolated Android 35 emulator", Build.HARDWARE.contains("ranchu") && Build.VERSION.SDK_INT >= 35)
        val flags = automation.serviceInfo
        flags.flags = flags.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = flags
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            LocalSettings(context).apply {
                typingLanguage = startingLanguage ?: TypingLanguage.English
                theme = "Light"
                keyboardHeight = KeyboardHeight.Standard
            }
        }
        if (InstrumentationRegistry.getArguments().getString("kothaMock") == "true" &&
            BuildConfig.BACKEND_URL == "http://10.0.2.2:4000") {
            val ready = CountDownLatch(1)
            AiClient.get(context).usage { ready.countDown() }
            assertTrue("mock session ready", ready.await(12, TimeUnit.SECONDS))
        }
        val component = ComponentName(context, KothaIme::class.java).flattenToShortString()
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
        showToolbar()
        if (keyboardNode(name) == null) clickKeyboardControl("More options")
        clickKeyboardControl(name)
    }

    private fun showToolbar() {
        if (keyboardNode("Show keyboard toolbar") != null) clickKeyboardControl("Show keyboard toolbar")
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
        if (InstrumentationRegistry.getArguments().getString("phase1Evidence") != "true") return
        val dir = checkNotNull(instrumentation.targetContext.getExternalFilesDir("phase1"))
        val bitmap = checkNotNull(automation.takeScreenshot())
        try {
            File(dir, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun phaseOneGridAndModeHeight() {
        setupKeyboard()
        focusPracticeField()
        selectLayout(TypingLanguage.English)
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
        waitFor("More symbols", keyboard = true)
        capture("04-symbols")
        clickKeyboardControl("More symbols")
        waitFor("Primary symbols", keyboard = true)
        capture("05-secondary-symbols")
        clickKeyboardControl("Numeric keypad")
        val zero = bounds(waitFor("0", keyboard = true))
        val two = bounds(waitFor("2", keyboard = true))
        assertTrue("zero aligns under the middle numeric column", abs(zero.centerX() - two.centerX()) <= 1)
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

    @Test fun phaseOneAiPanelsUseMockWithoutOverwritingDraft() {
        val arguments = InstrumentationRegistry.getArguments()
        val delayedMock = arguments.getString("phase1LoadingEvidence") == "true" && BuildConfig.BACKEND_URL == "http://127.0.0.1:4133"
        assumeTrue(BuildConfig.BACKEND_URL == "http://10.0.2.2:4000" || delayedMock)
        assumeTrue(arguments.getString("kothaMock") == "true")
        setupKeyboard()
        focusPracticeField()
        val frame = keyboardFrame()

        setPracticeText("im on teh bus")
        openFeature("Fix Grammar")
        clickWithoutDelay("Check grammar")
        captureLoading("Checking grammar…", "14-fix-grammar-loading")
        assertEquals(frame, keyboardFrame())
        waitFor("Replace Text", keyboard = true)
        assertEquals("I'm on the bus.", previewText())
        assertEquals("im on teh bus", practiceText())
        capture("15-fix-grammar-preview")
        clickKeyboardControl("Replace Text")
        editedText("I'm on the bus.")

        setPracticeText("can you check this today?")
        openFeature("Improve & Tone")
        clickKeyboardControl("Professional")
        clickWithoutDelay("Generate")
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
        var current = eventually("current typing layout") {
            TypingLanguage.entries.firstOrNull { keyboardNode(it.label) != null }
        }
        repeat(TypingLanguage.entries.size - 1) {
            if (current == desired) {
                editedText("")
                return
            }
            val expected = TypingLanguage.entries[(current.ordinal + 1) % TypingLanguage.entries.size]
            clickKeyboardControl("Change typing language")
            waitFor(expected.label, keyboard = true)
            current = expected
        }
        check(current == desired) { "Could not switch to ${desired.label}" }
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
            visibleRoots(false).firstNotNullOfOrNull { root ->
                root.first(includeHidden = true) { node ->
                    val length = node.text?.length ?: 0
                    node.className == "android.widget.EditText" && length > 0 &&
                        node.textSelectionStart == 0 && node.textSelectionEnd == length
                }
            }
        }
    }

    private fun clear() {
        showToolbar()
        clickKeyboardControl("More options")
        selectAll()
        clickKeyboardControl("Return to keyboard")
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
