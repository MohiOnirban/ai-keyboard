package com.kothakeys.keyboard

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

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
            clickKeyboardControl("Toggle emoji keyboard")
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
        clickKeyboardControl("Open AI assistant")
        clickKeyboardControl("Generate")
        waitFor("Replace", keyboard = true)
        val suggestion = previewText()
        clickKeyboardControl("Replace")
        editedText(suggestion)

        clickKeyboardControl("Editing tools")
        selectAll()
        clickKeyboardControl("Editing tools")
        clickKeyboardControl("Open AI assistant")
        clickKeyboardControl("Generate")
        waitFor("Replace", keyboard = true)
        type("", "Space")
        type("x")
        clickKeyboardControl("Replace")
        waitFor("Insert safely", keyboard = true)
        clickKeyboardControl("Close AI panel")
        editedText(" x")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    protected fun setupKeyboard() {
        assumeTrue("requires an isolated Android 35 emulator", Build.HARDWARE.contains("ranchu") && Build.VERSION.SDK_INT >= 35)
        val flags = automation.serviceInfo
        flags.flags = flags.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = flags
        val context = instrumentation.targetContext
        startingLanguage?.let { language ->
            instrumentation.runOnMainSync { LocalSettings(context).typingLanguage = language }
        }
        val component = ComponentName(context, KothaIme::class.java).flattenToShortString()
        command("ime enable $component")
        command("ime set $component")
        val launch = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity = instrumentation.startActivitySync(launch) as MainActivity
        val appName = instrumentation.targetContext.getString(R.string.app_name)
        waitFor("$appName is your active keyboard.")
    }

    protected fun selectEnglishLayout() = selectLayout(TypingLanguage.English)

    protected fun previewText(): String = eventually("generated suggestion") {
        val preview = keyboardNode("Close AI panel")?.parent?.parent?.getChild(1)
        preview?.takeIf { it.className == "android.widget.TextView" }
            ?.text?.toString()?.takeIf(String::isNotBlank)
    }

    protected fun focusPracticeField() {
        eventually("practice editor window focus") {
            var focused = false
            instrumentation.runOnMainSync {
                val field = activity.window.decorView.practiceField()
                if (field?.hasWindowFocus() == true && field.requestFocus()) {
                    WindowCompat.getInsetsController(activity.window, field).show(WindowInsetsCompat.Type.ime())
                    focused = true
                }
            }
            true.takeIf { focused }
        }
        waitFor("Open AI assistant", keyboard = true)
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
        clickKeyboardControl("Editing tools")
        selectAll()
        clickKeyboardControl("Backspace")
        clickKeyboardControl("Editing tools")
        editedText("")
    }

    protected fun editedText(expected: String) {
        eventually("editor text $expected") {
            visibleRoots(false).firstNotNullOfOrNull { root ->
                root.first(includeHidden = true) {
                    it.className == "android.widget.EditText" && (if (it.isShowingHintText) "" else it.text?.toString().orEmpty()) == expected
                }
            }
        }
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
            val key = root.first { it.text?.toString() == label || it.contentDescription?.toString() == label } ?: return null
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
        root.first { it.text?.toString() == label || it.contentDescription?.toString() == label }
    }

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
