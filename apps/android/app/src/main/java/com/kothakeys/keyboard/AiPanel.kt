package com.kothakeys.keyboard

import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar

internal class AiPanel(
    private val client: AiClient,
    private val settings: LocalSettings,
    private val refresh: () -> Unit,
    private val editorChanged: () -> Unit,
    private val returnToTyping: () -> Unit,
    private val paste: () -> String?,
    private val copy: (String) -> Unit,
    private val use: (RewriteTarget, String) -> Boolean,
    private val insert: (String) -> Boolean,
    private val isCurrentTarget: (RewriteTarget) -> Boolean,
) {
    var editing = false
        private set
    val editingText: String get() = suggestion ?: note
    private var feature = ContentMode.Ask
    private var mode = "Rewrite"
    private var style = ""
    private var intensity = "balanced"
    private var relationship = ""
    private var translationSource = "auto"
    private var translationTarget = "bn"
    private var rewrite: RewriteTarget? = null
    private var note = ""
    private var instruction = ""
    private var editingInstruction = false
    private var suggestion: String? = null
    private val alternatives = mutableListOf<String>()
    private var status: String? = null
    private var task: AiTask? = null
    private var requestVersion = 0
    private var consentOrigin: String? = null
    private var stale = false
    private var moreOptions = false

    fun show(feature: ContentMode, target: RewriteTarget?) {
        reset()
        this.feature = feature
        rewrite = target
        if (feature == ContentMode.Translation) {
            translationTarget = if (target?.text?.any { it in '\u0980'..'\u09ff' } == true) "en" else "bn"
        }
        style = when (feature) {
            ContentMode.Grammar -> "fix grammar"
            ContentMode.Tone -> "My Style"
            ContentMode.Translation -> "translation"
            else -> ""
        }
        if (feature == ContentMode.Grammar && target != null) generate()
    }

    fun reset() {
        cancelTask()
        consentOrigin = null
        editing = false
        feature = ContentMode.Ask
        mode = "Rewrite"
        style = ""
        intensity = "balanced"
        relationship = ""
        translationSource = "auto"
        translationTarget = "bn"
        rewrite = null
        note = ""
        instruction = ""
        editingInstruction = false
        suggestion = null
        alternatives.clear()
        status = null
        stale = false
        moreOptions = false
    }

    fun hostChanged() {
        val target = replacementTarget() ?: return
        if ((task != null || consentOrigin != null) && !isCurrentTarget(target)) {
            cancelTask()
            consentOrigin = null
            status = "Draft changed. Reopen this tool to use the current draft."
            refresh()
        }
    }

    private fun cancelTask() {
        requestVersion++
        task?.cancel()
        task = null
    }

    fun type(text: String) { replaceTail("", text) }

    fun replaceTail(old: String, next: String): Boolean {
        if (!editing) return false
        val current = editorValue
        if (!current.endsWith(old)) return false
        val edited = current.dropLast(old.length) + next
        if (edited.length > if (editingInstruction) 240 else if (suggestion == null) 1600 else 4800) return false
        setEditorValue(edited)
        return true
    }

    fun delete(graphemeLength: (String) -> Int) {
        if (editing) setEditorValue(editorValue.dropLast(graphemeLength(editorValue)))
    }

    fun finishEditing() {
        editing = false
        editingInstruction = false
        refresh()
    }

    val editorLabel: String
        get() = editorValue.ifEmpty { if (editingInstruction) "Type your instructions" else "Type here" }

    private val editorValue: String
        get() = if (editingInstruction) instruction else editingText

    private fun setEditorValue(value: String) {
        when {
            editingInstruction -> instruction = value
            suggestion != null -> suggestion = value
            else -> { if (note != value) alternatives.clear(); note = value }
        }
        editorChanged()
    }

    fun render(parent: LinearLayout, views: KeyboardViews) {
        val box = views.vertical().apply { setPadding(views.dp(5), views.dp(2), views.dp(5), views.dp(5)) }
        when {
            consentOrigin != null -> renderConsent(box, views)
            task != null -> renderLoading(box, views)
            suggestion != null -> renderPreview(box, views)
            else -> renderRequest(box, views)
        }
        status?.let { box.addView(views.label(it, 13f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }) }
        parent.addView(views.scrollPanel(box))
    }

    private fun renderConsent(box: LinearLayout, views: KeyboardViews) {
        val origin = checkNotNull(consentOrigin)
        box.addView(views.label("Allow quick AI actions?", 17f, strong = true))
        box.addView(views.label("Grammar and tone choices send the displayed text and your writing preferences to the configured AI service."))
        box.addView(views.label(resolvedInput(), 14f).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(views.dp(4), views.dp(4), views.dp(4), views.dp(4))
        })
        box.addView(views.horizontal().apply {
            views.equal(this, views.action("Allow and continue", accent = true) {
                consentOrigin = null
                if (client.configuredBackend() == origin) settings.allowQuickAi(origin)
                generate()
            }, 1.5f)
            views.equal(this, views.action("Not now") {
                consentOrigin = null
                status = null
                refresh()
            })
        })
    }

    private fun renderToneChoices(box: LinearLayout, views: KeyboardViews) {
        if (feature != ContentMode.Tone) return
        box.addView(views.chips(tones, style) { tone ->
            if (style == tone && (task != null || suggestion != null)) return@chips
            style = tone
            suggestion = null
            generate()
        })
    }

    private fun renderLoading(box: LinearLayout, views: KeyboardViews) {
        renderToneChoices(box, views)
        val message = when (feature) {
            ContentMode.Grammar -> "Checking grammar…"
            ContentMode.Translation -> "Translating…"
            else -> "Generating…"
        }
        box.addView(views.label(message, 17f, strong = true).apply {
            setPadding(views.dp(6), views.dp(10), 0, views.dp(5))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
        box.addView(ProgressBar(box.context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(views.colors.accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, views.dp(4)).apply {
            setMargins(views.dp(6), views.dp(5), views.dp(6), views.dp(9))
        })
        box.addView(views.label("Your draft stays in the message field."))
        box.addView(views.action("Cancel generation") {
            cancelTask()
            status = "Generation canceled."
            refresh()
        })
    }

    private fun renderRequest(box: LinearLayout, views: KeyboardViews) {
        if (feature == ContentMode.Ask) {
            box.addView(views.chips(Communication.modes, mode) {
                mode = it
                note = ""
                status = null
                refresh()
            })
        }
        renderToneChoices(box, views)
        if (feature == ContentMode.Translation) renderLanguages(box, views)

        val input = resolvedInput()
        val help = when {
            mode == "Reply" -> "Add the message you want to answer."
            mode == "Express" -> "Add the idea or feeling you want to express."
            else -> "Select text or provide your own input."
        }
        box.addView(views.label(input.takeIf(String::isNotBlank) ?: help, 15f).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(views.dp(6), views.dp(3), views.dp(6), views.dp(3))
        })
        if (mode != "Rewrite" || rewrite == null) {
            val inputActions = views.horizontal()
            views.equal(inputActions, views.action("Paste input") {
                val text = paste()
                if (text == null) status = "Clipboard has no text within 1,600 characters."
                else { if (note != text) alternatives.clear(); note = text; status = null }
                refresh()
            })
            views.equal(inputActions, views.action(if (note.isEmpty()) "Type input" else "Edit input") {
                editText()
            })
            box.addView(inputActions)
        }

        val footer = views.horizontal()
        val action = when (feature) {
            ContentMode.Grammar -> "Check grammar"
            ContentMode.Translation -> "Translate"
            else -> "Generate"
        }
        views.equal(footer, views.action(action, accent = true) { generate() }, 1.2f)
        if (feature == ContentMode.Ask) {
            views.equal(footer, views.action(if (moreOptions) "Fewer options" else "Writing options") {
                moreOptions = !moreOptions
                refresh()
            })
        }
        box.addView(footer)
        box.addView(views.label("Sends this text and your writing preferences to AI.", 12f).apply {
            setPadding(views.dp(5), views.dp(3), 0, 0)
        })
        if (feature == ContentMode.Ask && moreOptions) renderOptions(box, views)
    }

    private fun renderLanguages(box: LinearLayout, views: KeyboardViews) {
        val languages = Communication.languages.mapValues { (key, value) -> if (key == "auto") "Detect language" else value }
        val sources = languages.values.toList()
        val targets = languages.filterKeys { it != "auto" }.values.toList()
        box.addView(views.label("From", 12f, strong = true))
        box.addView(views.chips(sources, languages[translationSource]) { chosen ->
            translationSource = languages.entries.first { it.value == chosen }.key
            refresh()
        })
        box.addView(views.label("To", 12f, strong = true))
        box.addView(views.chips(targets, languages[translationTarget]) { chosen ->
            translationTarget = languages.entries.first { it.value == chosen }.key
            refresh()
        })
    }

    private fun renderOptions(box: LinearLayout, views: KeyboardViews) {
        val favorites = (listOf("Style: Auto") + settings.favorites + Communication.styles).distinct()
        box.addView(views.chips(favorites, style.ifEmpty { "Style: Auto" }) {
            style = it.takeUnless { name -> name == "Style: Auto" }.orEmpty()
            refresh()
        })
        val controls = views.horizontal()
        views.equal(controls, views.action(intensity.replaceFirstChar(Char::uppercase), "Intensity: $intensity") {
            intensity = next(Communication.intensities, intensity)
            refresh()
        })
        views.equal(controls, views.action(relationship.ifEmpty { "Relationship" }.replaceFirstChar(Char::uppercase),
            "Relationship: ${relationship.ifEmpty { "none" }}") {
            relationship = next(Communication.relationships, relationship)
            refresh()
        })
        box.addView(controls)
        box.addView(views.action(if (instruction.isBlank()) "Add custom instructions" else "Edit custom instructions") {
            editText(instructions = true)
        })
    }

    private fun renderPreview(box: LinearLayout, views: KeyboardViews) {
        renderToneChoices(box, views)
        val text = suggestion ?: return
        box.addView(views.label(text.ifEmpty { "Your suggestion is empty." }, 17f).apply {
            id = R.id.ai_suggestion
            background = views.rounded(views.colors.key)
            setPadding(views.dp(10), views.dp(8), views.dp(10), views.dp(8))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = views.dp(3) }
        })
        val selected = replacementTarget()
        val row = views.horizontal()
        val applyTitle = when {
            stale -> "Insert safely"
            selected != null -> "Replace Text"
            else -> "Insert"
        }
        views.equal(row, views.action(applyTitle, accent = true) {
            if (text.isNotBlank()) {
                val applied = if (stale || selected == null) insert(text) else use(selected, text)
                if (applied) returnToTyping() else {
                    stale = true
                    status = "Draft changed or the editor declined the edit. Insert safely or copy."
                    refresh()
                }
            }
        }, 1.6f)
        views.equal(row, views.action("Edit", "Edit suggestion") { editText() })
        views.equal(row, views.action("Copy", "Copy suggestion") {
            if (text.isNotBlank()) copy(text)
        })
        box.addView(row)
        val secondary = views.horizontal()
        views.equal(secondary, views.action("Regenerate") { generate() })
        if (feature != ContentMode.Grammar) views.equal(secondary, views.action("Options", "Edit generation options") {
            suggestion = null
            status = null
            refresh()
        })
        box.addView(secondary)
        if (feature == ContentMode.Tone) alternatives.filter { it != text }.forEach { alternative ->
            box.addView(views.action(alternative, "Use alternate suggestion") {
                suggestion = alternative
                refresh()
            }.apply { gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        }
    }

    private fun editText(instructions: Boolean = false) {
        editing = true
        editingInstruction = instructions
        status = null
        refresh()
    }

    private fun resolvedInput() = replacementTarget()?.text ?: note
    private fun replacementTarget() = rewrite.takeIf { mode == "Rewrite" }

    private fun generate() {
        val input = resolvedInput()
        if (input.isBlank() || input.length > 1600) {
            status = "Provide between 1 and 1,600 characters."
            refresh()
            return
        }
        if (feature == ContentMode.Translation && translationSource == translationTarget) {
            status = "Choose a different target language."
            refresh()
            return
        }
        if (replacementTarget()?.let { !isCurrentTarget(it) } == true) {
            status = "Draft changed. Reopen this tool to use the current draft."
            refresh()
            return
        }
        cancelTask()
        status = null
        stale = false
        if (feature == ContentMode.Grammar || feature == ContentMode.Tone) {
            val origin = client.configuredBackend()
            if (origin.isNotEmpty() && !settings.quickAiConsentFor(origin)) {
                consentOrigin = origin
                refresh()
                return
            }
        }
        val preferences = settings.requestPreferences()
        val additions = when {
            feature == ContentMode.Translation -> "Source language: ${Communication.languages.getValue(translationSource)}."
            instruction.isNotBlank() -> instruction
            else -> ""
        }
        if (additions.isNotEmpty()) {
            val custom = listOf(additions, preferences.optString("custom")).filter(String::isNotBlank).joinToString("\n")
            preferences.put("custom", custom.take(500).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it })
        }
        val language = if (feature == ContentMode.Translation) translationTarget else settings.aiLanguage
        val request = GenerationRequest(mode, input, style, intensity, relationship, language, preferences)
        val version = requestVersion
        val requestedFeature = feature
        task = client.generate(request) { result ->
            if (version != requestVersion) return@generate
            task = null
            result.onSuccess {
                suggestion = it.suggestion
                if (requestedFeature == ContentMode.Tone && it.suggestion !in alternatives) {
                    if (alternatives.size == 3) alternatives.removeAt(0)
                    alternatives.add(it.suggestion)
                }
            }.onFailure { status = it.message ?: "AI is temporarily unavailable." }
            refresh()
        }
        refresh()
    }

    private fun next(values: List<String>, value: String) = values[(values.indexOf(value) + 1) % values.size]

    private companion object {
        val tones = listOf("My Style", "Casual", "Professional", "Polite", "Romantic", "Funny", "Empathetic", "Shorter", "Expand")
    }
}
