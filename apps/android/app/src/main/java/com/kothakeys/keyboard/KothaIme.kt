package com.kothakeys.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import org.json.JSONObject

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
    private val inputMethods get() = getSystemService(InputMethodManager::class.java)
    private val panel by lazy {
        AiPanel(AiClient.get(this), settings, ::refreshPanel, ::updatePanelEditor, ::returnToTyping,
            ::pastedInput, ::copySuggestion, ::replace, ::insert)
    }
    private lateinit var views: KeyboardViews
    private lateinit var grid: KeyboardGrid
    private var layout: LinearLayout? = null
    private var headerHost: LinearLayout? = null
    private var body: LinearLayout? = null
    private var panelEditor: TextView? = null
    private var visibleHeader: Header? = null
    private var traits = EditorTraits(sensitive = true)
    private var content = ContentMode.Typing
    private var state = TypingState(TypingLanguage.English)
    private var editingTypingState: TypingState? = null
    private var languageReturnMode = ContentMode.Typing
    private var lastShift = 0L
    private var toolbarPinned = false
    private var word = ""
    private var panelWord = ""
    private var panelRenderedWord = ""
    private var repeating = false
    private val repeatDelete = object : Runnable {
        override fun run() {
            if (!repeating) return
            delete()
            handler.postDelayed(this, 65)
        }
    }

    override fun onCreateInputView(): View {
        updateDesign(KeyboardViews(this, KeyboardColors.from(settings.darkTheme()), settings.keyboardHeight))
        headerHost = views.vertical()
        body = views.vertical()
        visibleHeader = null
        return views.vertical().apply {
            layout = this
            val side = views.dp(4)
            val top = views.dp(2)
            val bottom = views.dp(4)
            setPadding(side, top, side, bottom)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val safe = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.mandatorySystemGestures() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(side + safe.left, top, side + safe.right, bottom + safe.bottom)
                renderHeader()
                insets
            }
            addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) renderHeader()
            }
            doOnAttach { ViewCompat.requestApplyInsets(it) }
            setBackgroundColor(views.colors.background)
            addView(headerHost, LinearLayout.LayoutParams(-1, views.dp(views.headerHeight)))
            addView(body, LinearLayout.LayoutParams(-1, views.dp(views.bodyHeight)))
            render()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetUi()
        traits = EditorTraits.from(attribute)
        editor.update(attribute?.initialSelStart ?: -1, attribute?.initialSelEnd ?: -1)
        render()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val design = KeyboardViews(this, KeyboardColors.from(settings.darkTheme()), settings.keyboardHeight)
        if (layout != null && (views.colors != design.colors || views.bodyHeight != design.bodyHeight)) {
            updateDesign(design)
            layout?.setBackgroundColor(views.colors.background)
            body?.layoutParams = LinearLayout.LayoutParams(-1, views.dp(views.bodyHeight))
            visibleHeader = null
        }
        render()
    }

    private fun updateDesign(design: KeyboardViews) {
        views = design
        grid = KeyboardGrid(views, ::type, ::toggleShift, ::changeLayer, ::cycleLanguage, ::showLanguages,
            ::delete, ::startRepeat, ::stopRepeat, ::enter)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        finishWord()
        returnToTyping()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        resetUi()
        traits = EditorTraits(sensitive = true)
        editor.update(-1, -1)
        render()
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        editor.update(newSelStart, newSelEnd)
        if (word.isNotEmpty() && (newSelStart != newSelEnd || candidatesEnd >= 0 && newSelStart != candidatesEnd)) {
            forgetWord()
            currentInputConnection?.finishComposingText()
            renderHeader()
        }
    }

    private fun resetUi() {
        stopRepeat()
        panel.reset()
        clearPanelWord()
        forgetWord()
        content = ContentMode.Typing
        state = TypingState(settings.typingLanguage)
        editingTypingState = null
        languageReturnMode = ContentMode.Typing
        lastShift = 0L
        toolbarPinned = false
    }

    private fun render() {
        renderHeader()
        renderBody()
    }

    private fun renderHeader() {
        val host = headerHost ?: return
        val root = layout ?: return
        val width = if (root.width > 0) root.width - root.paddingLeft - root.paddingRight
            else resources.displayMetrics.widthPixels - root.paddingLeft - root.paddingRight
        val slots = (width / views.dp(48)).coerceAtLeast(1)
        val predictions = if (toolbarPinned) emptyList() else candidates().take(3)
        val header = Header(content, panel.editing, traits, slots, predictions)
        if (header == visibleHeader) return
        visibleHeader = header
        panelEditor = null
        host.removeAllViews()
        val row = views.horizontal()
        when {
            panel.editing && content.isAi -> {
                panelEditor = views.label(panel.editorLabel, 15f).apply {
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.START
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(views.dp(7), 0, views.dp(5), 0)
                }
                views.gridItem(row, checkNotNull(panelEditor), 3.6f)
                views.gridItem(row, views.key("Done", "Done editing", accented = true, fontSize = 13f) { panel.finishEditing() }, 1f)
            }
            content != ContentMode.Typing -> {
                val close = views.toolIcon(ToolbarIcon.Back, ToolbarIcon.Back.description, false) {
                    if (content == ContentMode.Language && languageReturnMode.isAi) {
                        content = languageReturnMode
                        render()
                    } else returnToTyping()
                }
                row.addView(close, LinearLayout.LayoutParams(views.dp(48), -1))
                row.addView(views.label(content.icon?.description ?: "Typing language", 17f, strong = true))
            }
            predictions.isNotEmpty() -> {
                row.addView(views.toolIcon(ToolbarIcon.Toolbar, ToolbarIcon.Toolbar.description, false) {
                    toolbarPinned = true
                    renderHeader()
                }, LinearLayout.LayoutParams(views.dp(48), -1))
                predictions.forEach { candidate ->
                    views.gridItem(row, views.key(candidate, fontSize = 15f) {
                        currentInputConnection?.commitText(candidate, 1)
                        forgetWord()
                        renderHeader()
                    })
                }
            }
            else -> renderActions(row, slots)
        }
        host.addView(row, LinearLayout.LayoutParams(-1, -1))
    }

    private fun renderActions(row: LinearLayout, slots: Int) {
        if (!traits.aiAllowed) {
            val label = if (traits.sensitive) "Private field" else if (traits.numeric) "Numbers" else "Email / URL"
            row.addView(views.label(label, 13f).apply { setPadding(views.dp(9), 0, 0, 0) },
                LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(views.toolIcon(ToolbarIcon.More, ToolbarIcon.More.description, false) { open(ContentMode.More) },
                LinearLayout.LayoutParams(views.dp(48), -1))
            return
        }
        val shown = toolbarModes(slots)
        shown.forEach { target ->
            val icon = checkNotNull(target.icon)
            views.gridItem(row, views.toolIcon(icon, icon.description, target.isAi) { open(target) })
        }
    }

    private fun toolbarModes(slots: Int) = when {
        slots >= 8 -> PRIMARY + UTILITIES + listOf(ContentMode.More, ContentMode.Voice)
        slots >= 7 -> PRIMARY + UTILITIES + ContentMode.More
        slots >= 6 -> PRIMARY + ContentMode.Emoji + ContentMode.More
        slots >= 5 -> PRIMARY + ContentMode.More
        else -> PRIMARY.take((slots - 1).coerceAtLeast(0)) + ContentMode.More
    }

    private fun renderBody() {
        val host = body ?: return
        host.removeAllViews()
        when {
            content.isAi && !panel.editing && traits.aiAllowed -> panel.render(host, views)
            content == ContentMode.Emoji && traits.aiAllowed -> host.addView(grid.emojis(enterLabel()), fill())
            content == ContentMode.Clipboard -> renderClipboard(host)
            content == ContentMode.More -> renderMore(host)
            content == ContentMode.Language -> renderLanguages(host)
            content == ContentMode.Voice -> renderVoice(host)
            else -> host.addView(grid.layout(activeState(), traits, enterLabel()), fill())
        }
    }

    private fun activeState() = if (traits.address || traits.sensitive) state.copy(language = TypingLanguage.English) else state

    private fun fill() = LinearLayout.LayoutParams(-1, -1)

    private fun open(target: ContentMode) {
        if ((target.isAi || target == ContentMode.Emoji || target == ContentMode.Clipboard || target == ContentMode.Voice)
            && !traits.aiAllowed) return
        feedback()
        stopRepeat()
        finishWord()
        content = target
        if (target.isAi) {
            val draft = currentInputConnection?.let { editor.snapshot(it) }
                ?.target(draftAllowed = Build.VERSION.SDK_INT >= 34)
            panel.show(target, draft)
        }
        render()
    }

    private fun returnToTyping() {
        stopRepeat()
        editingTypingState?.let { state = it }
        editingTypingState = null
        clearPanelWord()
        panel.reset()
        content = ContentMode.Typing
        render()
    }

    private fun refreshPanel() {
        if (!content.isAi) return
        if (panel.editing && editingTypingState == null) {
            editingTypingState = state
            state = TypingState(state.language)
            clearPanelWord()
        } else if (!panel.editing && editingTypingState != null) {
            state = checkNotNull(editingTypingState)
            editingTypingState = null
            clearPanelWord()
        }
        render()
    }

    private fun updatePanelEditor() {
        panelEditor?.text = panel.editorLabel
    }

    private fun renderMore(host: LinearLayout) = utility(host) { box ->
        box.addView(views.horizontal().apply {
            views.equal(this, views.action("←", "Move cursor left") { move(false) })
            views.equal(this, views.action("→", "Move cursor right") { move(true) })
            if (!traits.sensitive) views.equal(this, views.action("Select all") { contextAction(android.R.id.selectAll) })
        })
        if (traits.aiAllowed) {
            val visible = toolbarModes(visibleHeader?.slots ?: 8)
            (PRIMARY + UTILITIES + ContentMode.Voice).filterNot { it in visible }.chunked(2).forEach { pair ->
                box.addView(views.horizontal().apply {
                    pair.forEach { choice ->
                        views.equal(this, views.action(checkNotNull(choice.icon).description) { open(choice) })
                    }
                })
            }
        }
        if (!traits.sensitive && !traits.numeric && !traits.address) {
            box.addView(views.action("Typing language") { showLanguages() })
        }
        box.addView(views.action("Switch system keyboard") { inputMethods.showInputMethodPicker() })
    }

    private fun renderClipboard(host: LinearLayout) = utility(host) { box ->
        box.addView(views.label("Use Android’s clipboard in this field."))
        box.addView(views.action("Paste clipboard", accent = true) { contextAction(android.R.id.paste) })
        box.addView(views.horizontal().apply {
            views.equal(this, views.action("Copy selection") { contextAction(android.R.id.copy) })
            views.equal(this, views.action("Cut selection") { contextAction(android.R.id.cut) })
            views.equal(this, views.action("Select all") { contextAction(android.R.id.selectAll) })
        })
    }

    private fun showLanguages() {
        finishWord()
        languageReturnMode = content
        content = ContentMode.Language
        render()
    }

    private fun renderLanguages(host: LinearLayout) = utility(host) { box ->
        val choices = listOf("English", "Direct Bangla", "Bangla phonetic", "Raw Banglish")
        TypingLanguage.entries.forEachIndexed { index, language ->
            box.addView(views.action(choices[index], accent = language == state.language) { selectLanguage(language) })
        }
    }

    private fun renderVoice(host: LinearLayout) = utility(host) { box ->
        val voice = if (Build.VERSION.SDK_INT >= 28) inputMethods.enabledInputMethodList.firstNotNullOfOrNull { method ->
            inputMethods.getEnabledInputMethodSubtypeList(method, true)
                .firstOrNull { it.mode.equals("voice", ignoreCase = true) }?.let { method.id to it }
        } else null
        box.addView(views.label(if (voice != null) "Use your enabled Android voice keyboard to dictate."
            else "Enable a voice input method in Android keyboard settings to dictate."))
        if (voice != null && Build.VERSION.SDK_INT >= 28) {
            box.addView(views.action("Start voice input", accent = true) {
                val started = runCatching { switchInputMethod(voice.first, voice.second) }.isSuccess
                if (!started) inputMethods.showInputMethodPicker()
            })
        } else {
            box.addView(views.action("Switch system keyboard") { inputMethods.showInputMethodPicker() })
        }
    }

    private fun utility(host: LinearLayout, populate: (LinearLayout) -> Unit) {
        val box = views.vertical().apply { setPadding(views.dp(6), 0, views.dp(6), views.dp(4)) }
        populate(box)
        host.addView(views.scrollPanel(box))
    }

    private fun cycleLanguage() {
        val next = TypingLanguage.entries[(state.language.ordinal + 1) % TypingLanguage.entries.size]
        selectLanguage(next)
    }

    private fun selectLanguage(language: TypingLanguage) {
        finishWord()
        clearPanelWord()
        feedback()
        state = TypingState(language)
        if (editingTypingState == null) settings.typingLanguage = language
        if (content == ContentMode.Language) content = languageReturnMode
        render()
    }

    private fun changeLayer(layer: KeyLayer) {
        stopRepeat()
        finishWord()
        clearPanelWord()
        state = state.copy(layer = layer, shift = state.language == TypingLanguage.Bangla && state.shift)
        render()
    }

    private fun toggleShift() {
        feedback()
        if (activeState().language == TypingLanguage.Bangla) {
            state = state.copy(shift = !state.shift)
        } else {
            val now = SystemClock.uptimeMillis()
            state = when {
                state.caps -> state.copy(caps = false, shift = false)
                state.shift && now - lastShift < 450 -> state.copy(caps = true, shift = false)
                else -> state.copy(shift = !state.shift)
            }
            lastShift = now
        }
        renderBody()
    }

    private fun startRepeat() {
        repeating = true
        repeatDelete.run()
    }

    private fun type(value: String) {
        feedback()
        if (panel.editing) {
            typeInPanel(value)
            releaseShift()
            return
        }
        val connection = currentInputConnection ?: return
        val wordKey = value.length == 1 && value[0].lowercaseChar() in 'a'..'z' && state.layer == KeyLayer.Alphabet && content != ContentMode.Emoji
        val useComposition = wordKey && when (state.language) {
            TypingLanguage.Phonetic -> traits.aiAllowed
            TypingLanguage.English, TypingLanguage.Banglish -> traits.wordAssistance && settings.correction
            else -> false
        }
        if (useComposition) {
            if (word.length >= 64) finishWord()
            word += value
            val displayedWord = if (state.language == TypingLanguage.Phonetic) phonetics.candidates(word).first() else word
            if (!connection.setComposingText(displayedWord, 1)) {
                connection.finishComposingText()
                connection.commitText(value, 1)
                forgetWord()
            }
        } else {
            val correction = if (value == " " && state.language == TypingLanguage.English) englishCorrection(word) else null
            if (correction != null) {
                connection.commitText(correction, 1)
                forgetWord()
            } else finishWord()
            connection.commitText(value, 1)
            toolbarPinned = false
        }
        releaseShift()
        renderHeader()
    }

    private fun typeInPanel(value: String) {
        if (state.language == TypingLanguage.Phonetic && value.length == 1 && value[0].lowercaseChar() in 'a'..'z') {
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
        if (state.shift && state.layer == KeyLayer.Alphabet && content != ContentMode.Emoji && state.language != TypingLanguage.Bangla) {
            state = state.copy(shift = false)
            renderBody()
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
            val displayedWord = if (state.language == TypingLanguage.Phonetic && word.isNotEmpty()) phonetics.candidates(word).first() else word
            connection.setComposingText(displayedWord, 1)
            if (word.isEmpty()) connection.finishComposingText()
        } else editor.delete(connection, traits.sensitive)
        renderHeader()
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
        renderHeader()
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
        panel.editing || content != ContentMode.Typing || state.layer != KeyLayer.Alphabet || word.isEmpty() -> emptyList()
        state.language == TypingLanguage.Phonetic && traits.aiAllowed -> phonetics.candidates(word)
        state.language == TypingLanguage.English && traits.wordAssistance -> englishCandidates(word)
        state.language == TypingLanguage.Banglish && traits.wordAssistance -> phonetics.banglishCandidates(word)
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
        renderHeader()
    }

    private fun contextAction(id: Int) {
        if (traits.sensitive || panel.editing) return
        finishWord()
        currentInputConnection?.let { connection ->
            if (id == android.R.id.selectAll) editor.selectAll(connection)
            else connection.performContextMenuAction(id)
        }
        renderHeader()
    }

    private fun pastedInput(): String? {
        if (!traits.aiAllowed) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text ?: return null
        return text.toString().takeIf { it.isNotBlank() && it.length <= 1600 }
    }

    private fun copySuggestion(text: String) {
        if (!traits.aiAllowed) return
        val clip = ClipData.newPlainText("AI suggestion", text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
    }

    private fun replace(target: RewriteTarget, suggestion: String): Boolean {
        if (!traits.aiAllowed) return false
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

    private data class Header(
        val content: ContentMode,
        val editingAssistant: Boolean,
        val traits: EditorTraits,
        val slots: Int,
        val predictions: List<String>,
    )

    private companion object {
        val PRIMARY = listOf(ContentMode.Grammar, ContentMode.Tone, ContentMode.Ask, ContentMode.Translation)
        val UTILITIES = listOf(ContentMode.Emoji, ContentMode.Clipboard)
    }

    private fun stopRepeat() {
        repeating = false
        handler.removeCallbacks(repeatDelete)
    }
}

private val ContentMode.isAi: Boolean
    get() = this == ContentMode.Grammar || this == ContentMode.Tone || this == ContentMode.Ask || this == ContentMode.Translation
