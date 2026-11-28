package com.kothakeys.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

class KothaIme : InputMethodService() {
    private val settings by lazy { LocalSettings(this) }
    private val phonetics by lazy {
        val overrides = assets.open("phonetic.json").bufferedReader().use { JSONObject(it.readText()) }
        val grammar = assets.open("avro/grammar.json").bufferedReader().use { JSONObject(it.readText()) }
        Phonetics(overrides, Avro(grammar))
    }
    private val predictionWorker = Executors.newSingleThreadExecutor()
    private var prediction: OfflinePrediction? = null
    private var predictionJob: Future<*>? = null
    private var predictionRequest: PredictionRequest? = null
    private var predictionGeneration = 0
    private var predictionResult = emptyList<Candidate>()
    private val editor = EditorAccess()
    private val handler = Handler(Looper.getMainLooper())
    private val clipboard get() = getSystemService(ClipboardManager::class.java)
    private val inputMethods get() = getSystemService(InputMethodManager::class.java)
    private val panel by lazy {
        AiPanel(AiClient.get(this), settings, ::refreshPanel, ::updatePanelEditor, ::returnToTyping,
            ::pastedInput, ::copySuggestion, ::replace, ::insert, ::isCurrentTarget)
    }
    private lateinit var views: KeyboardViews
    private lateinit var grid: KeyboardGrid
    private var layout: LinearLayout? = null
    private var headerHost: LinearLayout? = null
    private var suggestionHost: LinearLayout? = null
    private var body: LinearLayout? = null
    private var panelEditor: TextView? = null
    private var visibleHeader: Header? = null
    private var visibleSuggestions: List<Candidate>? = null
    private var traits = EditorTraits(sensitive = true)
    private var content = ContentMode.Typing
    private var state = TypingState(TypingLanguage.English)
    private var editingTypingState: TypingState? = null
    private var languageReturnMode = ContentMode.Typing
    private var dashboardChild = false
    private var voiceAvailable = false
    private var lastShift = 0L
    private var word = ""
    private var wordContext = PredictionContext()
    private var wordAssisted = false
    private var directWord = ""
    private var correctionUndo: CorrectionUndo? = null
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

    override fun onCreate() {
        super.onCreate()
        predictionWorker.execute {
            val offline = try {
                assets.open("predict.tsv").bufferedReader().use { corpus ->
                    assets.open("wordfreq.tsv").bufferedReader().use { dictionary ->
                        OfflinePrediction(corpus.lineSequence(), dictionary.lineSequence())
                    }
                }
            } catch (error: IOException) {
                Log.w("KothaIme", "Bundled typing data is unavailable", error)
                return@execute
            }
            handler.post {
                if (!predictionWorker.isShutdown) {
                    prediction = offline
                    invalidatePrediction()
                    renderSuggestions()
                }
            }
        }
    }

    override fun onDestroy() {
        invalidatePrediction()
        predictionWorker.shutdownNow()
        stopRepeat()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        updateDesign(KeyboardViews(this, KeyboardColors.from(settings.darkTheme()), settings.keyboardHeight))
        headerHost = views.vertical()
        suggestionHost = views.vertical()
        body = views.vertical()
        visibleHeader = null
        visibleSuggestions = null
        return views.vertical().apply {
            layout = this
            val side = views.dp(2)
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
            addView(suggestionHost, LinearLayout.LayoutParams(-1, views.dp(views.suggestionHeight)))
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
        voiceAvailable = traits.aiAllowed && voiceTarget() != null
        val design = KeyboardViews(this, KeyboardColors.from(settings.darkTheme()), settings.keyboardHeight)
        if (layout != null && (views.colors != design.colors || views.bodyHeight != design.bodyHeight)) {
            updateDesign(design)
            layout?.setBackgroundColor(views.colors.background)
            visibleHeader = null
            visibleSuggestions = null
        }
        render()
    }

    private fun updateDesign(design: KeyboardViews) {
        views = design
        grid = KeyboardGrid(views, ::type, ::toggleShift, ::changeLayer, ::cycleLanguage, ::showLanguages,
            ::delete, ::startRepeat, ::stopRepeat, ::enter) { open(ContentMode.Emoji) }
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
        val connection = currentInputConnection ?: return
        if (word.isNotEmpty() && (newSelStart != newSelEnd || candidatesEnd >= 0 && newSelStart != candidatesEnd)) {
            forgetWord()
            connection.finishComposingText()
        }
        if (newSelStart != newSelEnd || newSelStart < oldSelStart) directWord = ""
        if (content.isAi) panel.hostChanged()
        if (newSelStart != newSelEnd || newSelStart != correctionUndo?.cursor) correctionUndo = null
        renderSuggestions()
    }

    private fun resetUi() {
        stopRepeat()
        panel.reset()
        invalidatePrediction()
        directWord = ""
        clearPanelWord()
        forgetWord()
        content = ContentMode.Typing
        state = TypingState(settings.typingLanguage)
        editingTypingState = null
        languageReturnMode = ContentMode.Typing
        dashboardChild = false
        lastShift = 0L
        correctionUndo = null
    }

    private fun render() {
        renderHeader()
        renderSuggestions()
        renderBody()
    }

    private fun renderHeader() {
        val host = headerHost ?: return
        val root = layout ?: return
        val width = if (root.width > 0) root.width - root.paddingLeft - root.paddingRight
            else resources.displayMetrics.widthPixels - root.paddingLeft - root.paddingRight
        val slots = (width / views.dp(44)).coerceAtLeast(1)
        val header = Header(content, panel.editing, traits, slots, voiceAvailable, dashboardChild, languageReturnMode)
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
                val tools = content == ContentMode.More
                val nested = dashboardChild || content == ContentMode.Language && languageReturnMode == ContentMode.More
                val title = when (content) {
                    ContentMode.More -> "Tools"
                    ContentMode.Language -> "Typing language"
                    else -> checkNotNull(content.icon).description
                }
                val icon = if (tools) ToolbarIcon.Close else ToolbarIcon.Back
                val description = if (nested && !tools) "Back to tools" else icon.description
                val close = views.toolIcon(icon, description, tools, ::returnFromPanel)
                if (tools) row.addView(views.label(title, 17f, strong = true).apply {
                    setPaddingRelative(views.dp(12), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(close, LinearLayout.LayoutParams(views.dp(48), -1))
                if (!tools) row.addView(views.label(title, 17f, strong = true))
            }
            else -> renderActions(row, slots)
        }
        host.addView(row, LinearLayout.LayoutParams(-1, -1))
    }

    private fun renderActions(row: LinearLayout, slots: Int) {
        if (!traits.aiAllowed) {
            row.addView(views.toolIcon(ToolbarIcon.More, ToolbarIcon.More.description, true) { open(ContentMode.More) },
                LinearLayout.LayoutParams(views.dp(48), -1))
            val label = if (traits.sensitive) "Private field" else if (traits.numeric) "Numbers" else "Email / URL"
            row.addView(views.label(label, 13f), LinearLayout.LayoutParams(0, -2, 1f))
            return
        }
        val shown = toolbarModes(slots)
        shown.forEach { target ->
            val icon = checkNotNull(target.icon)
            views.gridItem(row, views.toolIcon(icon, icon.description, target.isAi || target == ContentMode.More) {
                open(target)
            })
        }
    }

    private fun toolbarModes(slots: Int): List<ContentMode> {
        val end = if (voiceAvailable && slots > 1) listOf(ContentMode.Voice) else emptyList()
        val main = (PRIMARY + UTILITIES).take((slots - 1 - end.size).coerceAtLeast(0))
        return listOf(ContentMode.More) + main + end
    }

    private fun renderSuggestions() {
        val host = suggestionHost ?: return
        val reserveStrip = content == ContentMode.Typing && traits.wordAssistance
        val show = reserveStrip && state.layer == KeyLayer.Alphabet && (settings.suggestions || correctionUndo != null)
        host.visibility = if (reserveStrip) View.VISIBLE else View.GONE
        val stripPixels = if (!reserveStrip && traits.wordAssistance) host.layoutParams.height else 0
        val bodyHeight = views.dp(views.bodyHeight) + stripPixels
        body?.let { if (it.layoutParams.height != bodyHeight) it.layoutParams = LinearLayout.LayoutParams(-1, bodyHeight) }
        val suggestions = if (show) candidates() else emptyList()
        if (visibleSuggestions == suggestions) return
        visibleSuggestions = suggestions
        host.removeAllViews()
        if (suggestions.isEmpty()) return
        val row = views.horizontal()
        for (index in 0..2) {
            if (index != 0) {
                row.addView(View(this).apply {
                    setBackgroundColor(views.colors.secondary)
                    alpha = 0.3f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(views.dp(1), views.dp(16)))
            }
            val candidate = suggestions.getOrNull(index)
            if (candidate == null) views.gridSpacer(row, 1f)
            else {
                val name = if (candidate.undo) "Undo" else "Suggestion ${candidate.label}"
                views.gridItem(row, views.suggestion(candidate.label, name, undo = candidate.undo) {
                    acceptCandidate(candidate)
                })
            }
        }
        host.addView(row, fill())
    }

    private fun renderBody() {
        val host = body ?: return
        host.removeAllViews()
        when {
            content.isAi && !panel.editing && traits.aiAllowed -> panel.render(host, views)
            content == ContentMode.Emoji && !traits.sensitive && !traits.numeric -> host.addView(grid.emojis(enterLabel()), fill())
            content == ContentMode.Clipboard -> renderClipboard(host)
            content == ContentMode.More -> renderMore(host)
            content == ContentMode.Language -> renderLanguages(host)
            content == ContentMode.Theme -> renderTheme(host)
            content == ContentMode.TextEditing -> renderTextEditing(host)
            content == ContentMode.Layout -> renderLayouts(host)
            content == ContentMode.Voice -> renderVoice(host)
            else -> host.addView(grid.layout(activeState(), traits, enterLabel(), panel.editing), fill())
        }
    }

    private fun activeState() = if (traits.address || traits.sensitive) state.copy(language = TypingLanguage.English) else state

    private fun fill() = LinearLayout.LayoutParams(-1, -1)

    private fun open(target: ContentMode) {
        if ((target.isAi || target == ContentMode.Clipboard || target == ContentMode.Voice) && !traits.aiAllowed) return
        if (target == ContentMode.Emoji && (traits.sensitive || traits.numeric)) return
        feedback()
        stopRepeat()
        if (target.isAi) {
            correctionUndo = null
            finishWord()
        }
        val fromTools = content == ContentMode.More
        if (content.isAi && !target.isAi) panel.reset()
        content = target
        dashboardChild = fromTools && target != ContentMode.More
        if (target.isAi) {
            val draft = currentInputConnection?.let { editor.snapshot(it) }
                ?.target(draftAllowed = Build.VERSION.SDK_INT >= 34)
            panel.show(target, draft)
        }
        render()
    }

    private fun returnFromPanel() {
        when {
            content == ContentMode.Language && languageReturnMode != ContentMode.Typing -> content = languageReturnMode
            dashboardChild -> {
                if (content.isAi) resetPanel()
                content = ContentMode.More
            }
            else -> {
                returnToTyping()
                return
            }
        }
        dashboardChild = false
        render()
    }

    private fun returnToTyping() {
        stopRepeat()
        resetPanel()
        content = ContentMode.Typing
        dashboardChild = false
        render()
    }

    private fun resetPanel() {
        editingTypingState?.let { state = it }
        editingTypingState = null
        clearPanelWord()
        panel.reset()
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

    private fun renderMore(host: LinearLayout) {
        val tools = buildList<Triple<String, ToolbarIcon, () -> Unit>> {
            add(Triple("Theme", ToolbarIcon.Theme) { open(ContentMode.Theme) })
            if (traits.aiAllowed) add(Triple("Clipboard", ToolbarIcon.Clipboard) { open(ContentMode.Clipboard) })
            if (!traits.sensitive && !traits.numeric) add(Triple("Emoji", ToolbarIcon.Emoji) { open(ContentMode.Emoji) })
            if (!traits.sensitive && !traits.numeric && !traits.address) {
                add(Triple("Typing language", ToolbarIcon.Language, ::showLanguages))
            }
            add(Triple("Text editing", ToolbarIcon.Edit) { open(ContentMode.TextEditing) })
            add(Triple("Keyboard layout", ToolbarIcon.Layout) { open(ContentMode.Layout) })
            if (traits.aiAllowed) add(Triple("Voice input", ToolbarIcon.Voice) { open(ContentMode.Voice) })
            add(Triple("Settings", ToolbarIcon.Settings, ::openSettings))
            if (traits.aiAllowed) {
                val visible = toolbarModes(visibleHeader?.slots ?: 8)
                PRIMARY.filterNot { it in visible }.forEach { choice ->
                    val icon = checkNotNull(choice.icon)
                    add(Triple(icon.description, icon) { open(choice) })
                }
            }
        }
        val rows = tools.chunked(2)
        val box = views.vertical().apply { setPadding(views.dp(5), 0, views.dp(5), views.dp(4)) }
        rows.forEach { pair ->
            views.rowFill(box, views.horizontal().apply {
                pair.forEach { (title, icon, action) -> views.gridItem(this, views.dashboardTool(title, icon, action)) }
                if (pair.size == 1) views.gridSpacer(this, 1f)
            })
        }
        repeat((4 - rows.size).coerceAtLeast(0)) { views.rowFill(box, View(this)) }
        host.addView(box, fill())
    }

    private fun renderTextEditing(host: LinearLayout) = utility(host) { box ->
        box.addView(views.horizontal().apply {
            views.equal(this, views.action("←", "Move cursor left") { feedback(); move(false) })
            views.equal(this, views.action("→", "Move cursor right") { feedback(); move(true) })
        })
        if (!traits.sensitive) box.addView(views.action("Select all") { contextAction(android.R.id.selectAll) })
    }

    private fun renderTheme(host: LinearLayout) = utility(host) { box ->
        box.addView(views.horizontal().apply {
            listOf("System", "Light", "Dark").forEach { theme ->
                views.equal(this, views.action(theme, accent = settings.theme == theme) {
                    settings.theme = theme
                    updateDesign(KeyboardViews(this@KothaIme, KeyboardColors.from(settings.darkTheme()), settings.keyboardHeight))
                    layout?.setBackgroundColor(views.colors.background)
                    visibleHeader = null
                    visibleSuggestions = null
                    render()
                })
            }
        })
    }

    private fun renderLayouts(host: LinearLayout) = utility(host) { box ->
        if (!traits.numeric) {
            listOf("Alphabet", "Symbols", "Secondary symbols", "Numeric keypad").chunked(2).forEachIndexed { row, titles ->
                box.addView(views.horizontal().apply {
                    titles.forEachIndexed { column, title ->
                        val layer = KeyLayer.entries[row * 2 + column]
                        views.equal(this, views.action(title, accent = state.layer == layer) {
                            content = ContentMode.Typing
                            dashboardChild = false
                            changeLayer(layer)
                        })
                    }
                })
            }
        }
        box.addView(views.action("Switch system keyboard") { inputMethods.showInputMethodPicker() })
    }

    private fun openSettings() {
        requestHideSelf(0)
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN_SETTINGS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
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
        correctionUndo = null
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

    @Suppress("DEPRECATION") // Some Android voice providers publish only the legacy locale field.
    private fun voiceTarget(): Pair<String, InputMethodSubtype>? {
        if (Build.VERSION.SDK_INT < 28) return null
        val preferred = if (state.language == TypingLanguage.English) "en" else "bn"
        val voices = inputMethods.enabledInputMethodList.flatMap { method ->
            inputMethods.getEnabledInputMethodSubtypeList(method, true)
                .filter { it.mode.equals("voice", ignoreCase = true) }
                .map { method.id to it }
        }
        return voices.firstOrNull { (_, subtype) ->
            val locale = subtype.languageTag.ifBlank { subtype.locale.replace('_', '-') }
            Locale.forLanguageTag(locale).language == preferred
        } ?: voices.firstOrNull()
    }

    private fun renderVoice(host: LinearLayout) = utility(host) { box ->
        val voice = voiceTarget()
        box.addView(views.label(if (voice != null)
            "Choose English or বাংলা in your Android voice provider where available."
            else "Enable a voice input method in Android keyboard settings to dictate."))
        if (voice != null && Build.VERSION.SDK_INT >= 28) {
            box.addView(views.action("Start voice input", accent = true) {
                val started = runCatching { switchInputMethod(voice.first, voice.second) }.isSuccess
                if (!started) inputMethods.showInputMethodPicker()
            })
        } else {
            box.addView(views.action("Open Android keyboard settings") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
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
        correctionUndo = null
        finishWord()
        clearPanelWord()
        feedback()
        state = TypingState(language)
        if (editingTypingState == null) settings.typingLanguage = language
        if (content == ContentMode.Language) {
            content = if (languageReturnMode == ContentMode.More) ContentMode.Typing else languageReturnMode
        }
        render()
    }

    private fun changeLayer(layer: KeyLayer) {
        stopRepeat()
        correctionUndo = null
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
        correctionUndo = null
        val language = state.language
        val composingLetter = value.length == 1 && value[0].lowercaseChar() in 'a'..'z'
        val composingApostrophe = language == TypingLanguage.English && word.lastOrNull()?.isLetter() == true &&
            (value == "'" || value == "’")
        val useComposition = (composingLetter || composingApostrophe) && state.layer == KeyLayer.Alphabet &&
            content == ContentMode.Typing && when (language) {
                TypingLanguage.Phonetic -> traits.aiAllowed
                TypingLanguage.English, TypingLanguage.Banglish -> traits.wordAssistance
                TypingLanguage.Bangla -> false
            }
        if (useComposition) {
            if (word.length >= 64) finishWord()
            val first = word.isEmpty()
            if (first && traits.wordAssistance) {
                val before = beforeCursor(connection)
                wordAssisted = before == null || startsIndependentWord(before)
                wordContext = if (wordAssisted && before != null) OfflinePrediction.contextBefore(before) else PredictionContext()
            }
            val input = if (first && language == TypingLanguage.English && wordContext.sentenceStart &&
                settings.automaticCapitalization) value.uppercase(Locale.ROOT) else value
            word += input
            if (!connection.setComposingText(displayedWord(), 1)) {
                connection.finishComposingText()
                connection.commitText(input, 1)
                forgetWord()
            }
        } else if (traits.wordAssistance && content == ContentMode.Typing && (value == " " || value == "\n")) {
            commitBoundary(connection, value)
        } else if (language == TypingLanguage.Bangla && content == ContentMode.Typing && state.layer == KeyLayer.Alphabet &&
            traits.wordAssistance && settings.learning && value.all { it in '\u0980'..'\u09ff' && !it.isDigit() || it in "\u200c\u200d" }) {
            if (connection.commitText(value, 1)) directWord = (directWord + value).takeLast(48)
        } else {
            finishWord()
            connection.commitText(value, 1)
        }
        releaseShift()
        renderSuggestions()
    }

    private fun startsIndependentWord(before: String): Boolean {
        if (OfflinePrediction.protectedTokenPrefix(before)) return false
        return before.lastOrNull()?.let { !it.isLetterOrDigit() && it !in ".-'’" } ?: true
    }

    private fun commitBoundary(connection: InputConnection, delimiter: String) {
        val language = state.language
        val original = word
        val visible = displayedWord()
        if (visible.isNotEmpty() && wordAssisted && (settings.learning || settings.correction && language == TypingLanguage.English) &&
            beforeCursor(connection)?.endsWith(visible) == true &&
            connection.getTextAfterCursor(1, 0)?.none { it.isLetterOrDigit() || it in "@._/" } == true) {
            val likelyName = !wordContext.sentenceStart && original.firstOrNull()?.isUpperCase() == true &&
                original.any(Char::isLowerCase)
            val corrected = if (!likelyName && settings.correction && language == TypingLanguage.English) {
                prediction?.autocorrect(language, original, wordContext, settings.predictionProfile(language))
            } else null
            val cursor = if (corrected == null) null else cursorPosition(connection)
            if (corrected != null && cursor != null && cursor >= original.length &&
                connection.commitText(corrected + delimiter, 1)) {
                correctionUndo = CorrectionUndo(original, corrected, delimiter, cursor - original.length + corrected.length + delimiter.length)
                forgetWord()
                return
            }
            finishWord()
            if (connection.commitText(delimiter, 1)) settings.learnWord(language, visible)
            return
        }
        val direct = directWord.takeIf { it.isNotEmpty() && settings.learning }
        val before = if (direct != null) beforeCursor(connection) else null
        val learnDirect = direct != null && before?.endsWith(direct) == true && startsIndependentWord(before.dropLast(direct.length))
        finishWord()
        if (connection.commitText(delimiter, 1) && learnDirect) settings.learnWord(language, checkNotNull(direct))
    }

    private fun displayedWord() = if (state.language == TypingLanguage.Phonetic) phonetics.candidates(word).firstOrNull().orEmpty() else word

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
        if (undoCorrection(connection, keepDelimiter = false)) {
            renderSuggestions()
            return
        }
        if (word.isNotEmpty()) {
            word = word.dropLast(1)
            connection.setComposingText(displayedWord(), 1)
            if (word.isEmpty()) {
                connection.finishComposingText()
                forgetWord()
            }
        } else {
            directWord = ""
            editor.delete(connection, traits.sensitive)
        }
        renderSuggestions()
    }

    private fun enter() {
        feedback()
        if (panel.editing) {
            typeInPanel("\n")
            return
        }
        correctionUndo = null
        val connection = currentInputConnection ?: return
        val action = traits.enterAction
        if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (traits.wordAssistance && content == ContentMode.Typing) commitBoundary(connection, "\n")
            else {
                finishWord()
                connection.commitText("\n", 1)
            }
        } else {
            finishWord()
            if (!connection.performEditorAction(action)) connection.commitText("\n", 1)
        }
        renderSuggestions()
    }

    private fun enterLabel(): String = if (panel.editing) "↵" else when (traits.enterAction) {
        EditorInfo.IME_ACTION_DONE -> "Done"
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        else -> "↵"
    }

    private fun candidates(): List<Candidate> {
        val undo = correctionUndo?.let { listOf(Candidate("Undo", undo = true)) }.orEmpty()
        val connection = currentInputConnection ?: return undo
        if (!settings.suggestions) return undo
        val language = state.language
        val before = if (word.isEmpty()) beforeCursor(connection).orEmpty() else ""
        val direct = if (language == TypingLanguage.Bangla) {
            OfflinePrediction.tailWord(before)?.takeIf { tail -> tail.any { it in '\u0980'..'\u09ff' } }.orEmpty()
        } else ""
        val context = if (word.isNotEmpty()) wordContext else OfflinePrediction.contextBefore(before.dropLast(direct.length))
        val phonetic = if (word.isNotEmpty() && language == TypingLanguage.Phonetic) phonetics.candidates(word) else emptyList()
        val request = PredictionRequest(language, word, direct, context, phonetic, settings.predictionProfile(language),
            settings.nextWord, wordAssisted)
        val model = prediction
        if (request != predictionRequest) {
            invalidatePrediction()
            predictionRequest = request
            predictionResult = if (word.isEmpty() || !wordAssisted) emptyList()
                else listOf(Candidate(phonetic.firstOrNull() ?: word, composing = word))
            if (model != null && (word.isNotEmpty() && wordAssisted || direct.isNotEmpty() ||
                    request.nextEnabled && context.previous != null)) startPrediction(model, request)
        }
        return undo + predictionResult.take(3 - undo.size)
    }

    private fun invalidatePrediction() {
        predictionJob?.cancel(false)
        predictionJob = null
        predictionRequest = null
        predictionResult = emptyList()
        predictionGeneration++
    }

    private fun startPrediction(model: OfflinePrediction, request: PredictionRequest) {
        val generation = predictionGeneration
        predictionJob = predictionWorker.submit {
            val (language, raw, direct, context, phonetic, profile) = request
            val matches = when {
                phonetic.isNotEmpty() -> {
                    val primary = phonetic.first()
                    val completion = model.complete(language, primary, context, profile)
                    val alternative = (phonetic.drop(1).filter { it != raw } + completion).firstOrNull { it != primary }
                    listOfNotNull(primary, alternative, raw).distinct().map { Candidate(it, composing = raw) }
                }
                raw.isNotEmpty() -> {
                    val results = model.suggestions(language, raw, context, profile)
                    val legacy = if (language == TypingLanguage.Banglish) phonetics.banglishCandidates(raw) else emptyList()
                    (results + legacy).distinct().take(3).map { Candidate(it, composing = raw) }
                }
                direct.isNotEmpty() -> model.suggestions(language, direct, context, profile).map { Candidate(it, replacing = direct) }
                request.nextEnabled -> model.next(language, context, profile).map { Candidate(it, nextAfter = context) }
                else -> emptyList()
            }
            handler.post {
                if (generation == predictionGeneration && !predictionWorker.isShutdown) {
                    predictionResult = matches
                    renderSuggestions()
                }
            }
        }
    }

    private fun acceptCandidate(candidate: Candidate) {
        if (!traits.wordAssistance || content != ContentMode.Typing || candidate !in visibleSuggestions.orEmpty()) return
        val connection = currentInputConnection ?: return
        val language = state.language
        feedback()
        var accepted = false
        when {
            candidate.undo -> undoCorrection(connection, keepDelimiter = true)
            candidate.composing != null && word == candidate.composing && wordAssisted -> {
                if (beforeCursor(connection)?.endsWith(displayedWord()) == true && connection.commitText(candidate.label, 1)) {
                    if (settings.learning && candidate.label == word) {
                        prediction?.autocorrect(language, word, wordContext, settings.predictionProfile(language))?.let {
                            settings.rejectCorrection(language, word, it)
                        }
                    }
                    forgetWord()
                    accepted = true
                }
            }
            candidate.replacing != null && candidate.label != candidate.replacing -> {
                accepted = replaceTail(connection, candidate.replacing, candidate.label)
                directWord = ""
            }
            candidate.nextAfter != null && candidate.nextAfter == beforeCursor(connection)?.let(OfflinePrediction::contextBefore) -> {
                correctionUndo = null
                accepted = connection.commitText(candidate.label + " ", 1)
            }
        }
        if (accepted && settings.learning) {
            candidate.label.split(' ').forEach { settings.learnWord(language, it, accepted = true) }
        }
        renderSuggestions()
    }

    private fun beforeCursor(connection: InputConnection): String? {
        if (!traits.wordAssistance || editor.start != editor.end) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val around = connection.getSurroundingText(96, 0, 0) ?: return null
            if (around.selectionStart != around.selectionEnd) return null
            return around.text.substring(0, around.selectionStart)
        }
        if (editor.start < 0 || !connection.getSelectedText(0).isNullOrEmpty()) return null
        return connection.getTextBeforeCursor(96, 0)?.toString()
    }

    private fun cursorPosition(connection: InputConnection): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val around = connection.getSurroundingText(0, 0, 0) ?: return null
            return (around.offset + around.selectionStart).takeIf { around.offset >= 0 && around.selectionStart == around.selectionEnd }
        }
        return editor.start.takeIf { it >= 0 && it == editor.end && connection.getSelectedText(0).isNullOrEmpty() }
    }

    private fun undoCorrection(connection: InputConnection, keepDelimiter: Boolean): Boolean {
        val undo = correctionUndo ?: return false
        correctionUndo = null
        val replacement = if (keepDelimiter) undo.original + undo.delimiter else undo.original
        if (!traits.wordAssistance || !replaceTail(connection, undo.corrected + undo.delimiter, replacement, undo.cursor)) return false
        settings.rejectCorrection(state.language, undo.original, undo.corrected)
        return true
    }

    private fun replaceTail(connection: InputConnection, old: String, replacement: String, expectedCursor: Int? = null): Boolean {
        val cursor = cursorPosition(connection) ?: return false
        if (cursor < old.length || (expectedCursor != null && cursor != expectedCursor)) return false
        if (beforeCursor(connection)?.endsWith(old) != true) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return connection.replaceText(cursor - old.length, cursor, replacement, 1, null)
        }
        connection.beginBatchEdit()
        try {
            if (!connection.setSelection(cursor - old.length, cursor)) return false
            if (connection.getSelectedText(0).toString() == old && connection.commitText(replacement, 1)) return true
            connection.setSelection(cursor, cursor)
            return false
        } finally {
            connection.endBatchEdit()
        }
    }

    private fun finishWord() {
        directWord = ""
        if (word.isEmpty()) return
        currentInputConnection?.finishComposingText()
        forgetWord()
    }

    private fun forgetWord() {
        word = ""
        wordContext = PredictionContext()
        wordAssisted = false
    }

    private fun move(right: Boolean) {
        if (panel.editing) return
        correctionUndo = null
        finishWord()
        currentInputConnection?.let { editor.move(it, right) }
        renderSuggestions()
    }

    private fun contextAction(id: Int) {
        if (traits.sensitive || panel.editing) return
        correctionUndo = null
        finishWord()
        currentInputConnection?.let { connection ->
            if (id == android.R.id.selectAll) editor.selectAll(connection)
            else connection.performContextMenuAction(id)
        }
        renderSuggestions()
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

    private fun isCurrentTarget(target: RewriteTarget): Boolean =
        traits.aiAllowed && currentInputConnection?.let { target.stillMatches(editor.snapshot(it)) } == true

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
        val voiceAvailable: Boolean,
        val dashboardChild: Boolean,
        val languageReturnMode: ContentMode,
    )

    private data class Candidate(
        val label: String,
        val composing: String? = null,
        val replacing: String? = null,
        val nextAfter: PredictionContext? = null,
        val undo: Boolean = false,
    )

    private data class PredictionRequest(
        val language: TypingLanguage,
        val word: String,
        val direct: String,
        val context: PredictionContext,
        val phonetic: List<String>,
        val profile: PredictionProfile,
        val nextEnabled: Boolean,
        val wordAssisted: Boolean,
    )

    private data class CorrectionUndo(val original: String, val corrected: String, val delimiter: String, val cursor: Int)

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
