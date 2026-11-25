package com.kothakeys.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.util.Locale

class KothaIme : InputMethodService() {
    private val settings by lazy { LocalSettings(this) }
    private val phonetics by lazy {
        val overrides = assets.open("phonetic.json").bufferedReader().use { JSONObject(it.readText()) }
        val grammar = assets.open("avro/grammar.json").bufferedReader().use { JSONObject(it.readText()) }
        Phonetics(overrides, Avro(grammar))
    }
    private val editor = EditorAccess()
    private val handler = Handler(Looper.getMainLooper())
    private val clipboard get() = getSystemService(ClipboardManager::class.java)
    private val panel by lazy {
        AiPanel(AiClient.get(this), settings, ::renderPanel, ::pastedInput, ::copySuggestion, ::replace, ::insert)
    }
    private lateinit var views: KeyboardViews
    private var layout: LinearLayout? = null
    private var panelHost: LinearLayout? = null
    private var toolbarHost: LinearLayout? = null
    private var keyHost: LinearLayout? = null
    private var traits = EditorTraits(sensitive = true)
    private var language = TypingLanguage.English
    private var symbols = false
    private var emoji = false
    private var shift = false
    private var caps = false
    private var lastShift = 0L
    private var tools = false
    private var word = ""
    private var panelWord = ""
    private var panelRenderedWord = ""
    private var wasEditingPanel = false
    private var repeating = false
    private val repeatDelete = object : Runnable {
        override fun run() {
            if (!repeating) return
            delete()
            handler.postDelayed(this, 65)
        }
    }

    override fun onCreateInputView(): View {
        views = KeyboardViews(this, KeyboardColors.from(settings.darkTheme()))
        panelHost = views.vertical()
        toolbarHost = views.vertical()
        keyHost = views.vertical()
        return views.vertical().apply {
            layout = this
            val side = views.dp(4)
            val top = views.dp(3)
            val bottom = views.dp(6)
            setPadding(side, top, side, bottom)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val safe = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.mandatorySystemGestures() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(side + safe.left, top, side + safe.right, bottom + safe.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(this)
            setBackgroundColor(views.colors.background)
            addView(panelHost)
            addView(toolbarHost)
            addView(keyHost)
            renderPanel()
            renderKeys()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetUi()
        traits = EditorTraits.from(attribute)
        editor.update(attribute?.initialSelStart ?: -1, attribute?.initialSelEnd ?: -1)
        renderPanel()
        renderKeys()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val palette = KeyboardColors.from(settings.darkTheme())
        if (layout != null && views.colors != palette) setInputView(onCreateInputView())
        renderPanel()
        renderKeys()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        panel.reset()
        finishWord()
        renderPanel()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        resetUi()
        traits = EditorTraits(sensitive = true)
        editor.update(-1, -1)
        renderPanel()
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        editor.update(newSelStart, newSelEnd)
        if (word.isNotEmpty() && (newSelStart != newSelEnd || candidatesEnd >= 0 && newSelStart != candidatesEnd)) {
            forgetWord()
            currentInputConnection?.finishComposingText()
            renderToolbar()
        }
    }

    private fun resetUi() {
        stopRepeat()
        panel.reset()
        clearPanelWord()
        forgetWord()
        tools = false
        language = settings.typingLanguage
        symbols = false
        emoji = false
        shift = false
        caps = false
        lastShift = 0L
    }

    private fun renderPanel() {
        val host = panelHost ?: return
        host.removeAllViews()
        if (traits.aiAllowed) panel.render(host, views)
        if (panel.editing != wasEditingPanel) {
            wasEditingPanel = panel.editing
            clearPanelWord()
            renderKeys()
        }
        renderToolbar()
    }

    private fun renderToolbar() {
        val host = toolbarHost ?: return
        host.removeAllViews()
        val row = views.horizontal()
        if (traits.aiAllowed) {
            views.weighted(row, views.key("✦ AI", "Open AI assistant", accented = true, height = 37, fontSize = 14f) {
                if (panel.open) panel.close() else {
                    finishWord()
                    val target = currentInputConnection?.let { editor.snapshot(it) }
                        ?.target(draftAllowed = android.os.Build.VERSION.SDK_INT >= 34)
                    panel.show(target)
                }
            }, 1.1f)
        }
        val candidates = candidates()
        if (candidates.isEmpty()) {
            val label = if (traits.sensitive) "Private field" else if (traits.numeric) "Numbers"
                else if (traits.address) "Email / URL" else language.label
            views.weighted(row, views.label(label, 12f), 2.5f)
        } else {
            views.weighted(row, views.chips(candidates) { selected ->
                currentInputConnection?.commitText(selected, 1)
                forgetWord()
                renderToolbar()
            }, 2.5f)
        }
        views.weighted(row, views.key("⋯", "Editing tools", accented = tools, height = 37) {
            tools = !tools
            renderToolbar()
        }, 0.8f)
        views.weighted(row, views.key("◎", "Switch system keyboard", height = 37) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }, 0.8f)
        host.addView(row)

        if (tools) {
            val actions = views.horizontal()
            tool(actions, "←", "Move cursor left") { move(false) }
            tool(actions, "→", "Move cursor right") { move(true) }
            if (!traits.sensitive && !panel.editing) {
                tool(actions, "All", "Select all") { contextAction(android.R.id.selectAll) }
                tool(actions, "Copy", "Copy selection") { contextAction(android.R.id.copy) }
                tool(actions, "Cut", "Cut selection") { contextAction(android.R.id.cut) }
                tool(actions, "Paste", "Paste clipboard") { contextAction(android.R.id.paste) }
            }
            host.addView(actions)
        }
    }

    private fun tool(row: LinearLayout, title: String, label: String, action: () -> Unit) {
        views.weighted(row, views.key(title, label, height = 35, fontSize = 13f, action = action))
    }

    private fun renderKeys() {
        val host = keyHost ?: return
        host.removeAllViews()
        val activeLanguage = if (traits.address || traits.sensitive) TypingLanguage.English else language
        val rows = when {
            traits.numeric -> KeyboardLayouts.numeric
            emoji -> KeyboardLayouts.emoji
            symbols && shift -> KeyboardLayouts.symbolsExtra
            symbols -> KeyboardLayouts.symbols
            activeLanguage == TypingLanguage.Bangla && shift -> KeyboardLayouts.banglaExtra
            activeLanguage == TypingLanguage.Bangla -> KeyboardLayouts.bangla
            else -> KeyboardLayouts.english
        }
        rows.forEach { specs ->
            val row = views.horizontal()
            specs.forEach { original ->
                val spec = if (!symbols && !emoji && (shift || caps) && activeLanguage != TypingLanguage.Bangla)
                    original.copy(text = original.text.uppercase(Locale.ROOT)) else original
                val view = views.key(spec.label, spec.label, fontSize = if (traits.numeric || emoji) 21f else 18f) {
                    type(spec.text)
                }
                spec.alternate?.let { alternate ->
                    view.setOnLongClickListener {
                        type(alternate)
                        true
                    }
                }
                views.weighted(row, view)
            }
            host.addView(row)
        }
        val controls = views.horizontal()
        if (!traits.numeric) {
            val sign = if (caps) "⇪" else "⇧"
            val caption = when {
                symbols -> "More symbols"
                activeLanguage == TypingLanguage.Bangla -> "More Bangla letters"
                else -> "Shift. Double tap for caps lock."
            }
            views.weighted(controls, views.key(sign, caption, shift || caps, fontSize = 18f) { toggleShift(activeLanguage) }, 0.85f)
            views.weighted(controls, views.key(if (symbols) "ABC" else "123", "Toggle numbers and symbols", symbols, fontSize = 13f) {
                finishWord()
                symbols = !symbols
                emoji = false
                shift = false
                renderKeys()
            }, 0.9f)
            if (!traits.sensitive) {
                views.weighted(controls, views.key("☺", "Toggle emoji keyboard", emoji) {
                    finishWord()
                    emoji = !emoji
                    symbols = false
                    shift = false
                    renderKeys()
                }, 0.8f)
            }
            if (!traits.address && !traits.sensitive) {
                views.weighted(controls, views.key("文", "Change typing language", fontSize = 15f) {
                    cycleLanguage()
                }, 0.85f)
            }
            val punctuation = when {
                traits.url -> "/"
                traits.address -> "@"
                else -> ","
            }
            views.weighted(controls, views.key(punctuation, punctuation) { type(punctuation) }, 0.65f)
            views.weighted(controls, views.key("Space", "Space", fontSize = 13f) {
                type(" ")
            }, 2.15f)
            val period = if (!symbols && !emoji && activeLanguage == TypingLanguage.Bangla) "।" else "."
            views.weighted(controls, views.key(period, if (period == "।") "Bengali full stop" else "Period") { type(period) }, 0.65f)
        }
        val backspace = views.key("⌫", "Backspace") { delete() }
        backspace.setOnLongClickListener {
            repeating = true
            repeatDelete.run()
            true
        }
        backspace.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) stopRepeat()
            false
        }
        views.weighted(controls, backspace, 0.95f)
        views.weighted(controls, views.key(enterLabel(), "Enter: ${enterLabel()}", fontSize = 13f) { enter() }, 1.15f)
        host.addView(controls)
    }

    private fun toggleShift(activeLanguage: TypingLanguage) {
        feedback()
        if (emoji) return
        if (symbols || activeLanguage == TypingLanguage.Bangla) {
            shift = !shift
        } else {
            val now = SystemClock.uptimeMillis()
            when {
                caps -> { caps = false; shift = false }
                shift && now - lastShift < 450 -> { caps = true; shift = false }
                else -> shift = !shift
            }
            lastShift = now
        }
        renderKeys()
    }

    private fun cycleLanguage() {
        finishWord()
        clearPanelWord()
        feedback()
        language = TypingLanguage.entries[(language.ordinal + 1) % TypingLanguage.entries.size]
        settings.typingLanguage = language
        shift = false
        caps = false
        symbols = false
        emoji = false
        renderKeys()
        renderToolbar()
    }

    private fun type(value: String) {
        feedback()
        if (panel.editing) {
            typeInPanel(value)
            releaseShift()
            return
        }
        val connection = currentInputConnection ?: return
        val wordKey = value.length == 1 && value[0].lowercaseChar() in 'a'..'z' && !symbols && !emoji
        val useComposition = wordKey && when (language) {
            TypingLanguage.Phonetic -> traits.aiAllowed
            TypingLanguage.English -> traits.wordAssistance && settings.correction
            else -> false
        }
        if (useComposition) {
            if (word.length >= 64) finishWord()
            word += value
            val displayedWord = if (language == TypingLanguage.Phonetic) phonetics.candidates(word).first() else word
            if (!connection.setComposingText(displayedWord, 1)) {
                connection.finishComposingText()
                connection.commitText(value, 1)
                forgetWord()
            }
        } else {
            val correction = if (value == " " && language == TypingLanguage.English) englishCorrection(word) else null
            if (correction != null) {
                connection.commitText(correction, 1)
                forgetWord()
            } else finishWord()
            connection.commitText(value, 1)
        }
        releaseShift()
        renderToolbar()
    }

    private fun typeInPanel(value: String) {
        if (language == TypingLanguage.Phonetic && value.length == 1 && value[0].lowercaseChar() in 'a'..'z') {
            if (panelWord.length >= 64) clearPanelWord()
            updatePanelWord(panelWord + value)
        } else {
            clearPanelWord()
            panel.type(value)
        }
    }

    private fun updatePanelWord(raw: String) {
        val next = phonetics.candidates(raw).firstOrNull().orEmpty()
        if (panel.replaceTail(panelRenderedWord, next)) {
            panelWord = raw
            panelRenderedWord = next
        }
    }

    private fun clearPanelWord() {
        panelWord = ""
        panelRenderedWord = ""
    }

    private fun releaseShift() {
        if (shift && !symbols && !emoji && language != TypingLanguage.Bangla) {
            shift = false
            renderKeys()
        }
    }

    private fun delete() {
        feedback()
        if (panel.editing) {
            if (panelWord.isEmpty()) panel.delete(editor::lastGraphemeLength)
            else updatePanelWord(panelWord.dropLast(1))
            return
        }
        val connection = currentInputConnection ?: return
        if (word.isNotEmpty()) {
            word = word.dropLast(1)
            val displayedWord = if (language == TypingLanguage.Phonetic && word.isNotEmpty()) phonetics.candidates(word).first() else word
            connection.setComposingText(displayedWord, 1)
            if (word.isEmpty()) connection.finishComposingText()
        } else editor.delete(connection, traits.sensitive)
        renderToolbar()
    }

    private fun enter() {
        feedback()
        if (panel.editing) {
            typeInPanel("\n")
            return
        }
        finishWord()
        val connection = currentInputConnection ?: return
        val action = traits.enterAction
        if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED || !connection.performEditorAction(action)) {
            connection.commitText("\n", 1)
        }
        renderToolbar()
    }

    private fun enterLabel(): String = if (panel.editing) "↵" else when (traits.enterAction) {
        EditorInfo.IME_ACTION_DONE -> "Done"
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        else -> "↵"
    }

    private fun candidates(): List<String> = when {
        panel.editing || word.isEmpty() -> emptyList()
        language == TypingLanguage.Phonetic && traits.aiAllowed -> phonetics.candidates(word)
        language == TypingLanguage.English && traits.wordAssistance -> englishCandidates(word)
        else -> emptyList()
    }

    private fun finishWord() {
        if (word.isEmpty()) return
        currentInputConnection?.finishComposingText()
        forgetWord()
    }

    private fun forgetWord() {
        word = ""
    }

    private fun move(right: Boolean) {
        if (panel.editing) return
        finishWord()
        currentInputConnection?.let { editor.move(it, right) }
        renderToolbar()
    }

    private fun contextAction(id: Int) {
        if (traits.sensitive || panel.editing) return
        finishWord()
        currentInputConnection?.let { connection ->
            if (id == android.R.id.selectAll) editor.selectAll(connection)
            else connection.performContextMenuAction(id)
        }
        renderToolbar()
    }

    private fun pastedInput(): String? {
        if (!traits.aiAllowed) return null
        val clip = clipboard.primaryClip ?: return null
        val text = clip.getItemAt(0).text ?: return null
        return text.toString().takeIf { it.isNotBlank() && it.length <= 1600 }
    }

    private fun copySuggestion(text: String) {
        if (!traits.aiAllowed) return
        val clip = ClipData.newPlainText("AI suggestion", text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
    }

    private fun replace(target: RewriteTarget?, suggestion: String): Boolean {
        if (!traits.aiAllowed || target == null) return false
        finishWord()
        return currentInputConnection?.let { editor.replace(it, target, suggestion) } ?: false
    }

    private fun insert(suggestion: String): Boolean {
        if (!traits.aiAllowed) return false
        finishWord()
        return currentInputConnection?.let { editor.insertWithoutReplacing(it, suggestion) } ?: false
    }

    private fun feedback() {
        if (settings.haptics) layout?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun stopRepeat() {
        repeating = false
        handler.removeCallbacks(repeatDelete)
    }
}
