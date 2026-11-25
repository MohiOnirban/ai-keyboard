package com.kothakeys.keyboard

import android.widget.LinearLayout

internal class AiPanel(
    private val client: AiClient,
    private val settings: LocalSettings,
    private val refresh: () -> Unit,
    private val paste: () -> String?,
    private val copy: (String) -> Unit,
    private val use: (RewriteTarget?, String) -> Boolean,
    private val insert: (String) -> Boolean
) {
    var open = false
        private set
    var editing = false
        private set
    private var mode = "Rewrite"
    private var style = ""
    private var intensity = "balanced"
    private var relationship = ""
    private var rewrite: RewriteTarget? = null
    private var note = ""
    private var suggestion: String? = null
    private var status: String? = null
    private var task: AiTask? = null
    private var usage: Usage? = null
    private var stale = false
    private var moreOptions = false

    fun show(target: RewriteTarget?) {
        reset()
        open = true
        rewrite = target
        refresh()
    }

    fun reset() {
        task?.cancel()
        task = null
        open = false
        editing = false
        mode = "Rewrite"
        style = ""
        intensity = "balanced"
        relationship = ""
        rewrite = null
        note = ""
        suggestion = null
        status = null
        usage = null
        stale = false
        moreOptions = false
    }

    fun close() {
        reset()
        refresh()
    }

    fun type(text: String) { replaceTail("", text) }

    fun replaceTail(old: String, next: String): Boolean {
        if (!editing) return false
        val current = suggestion ?: note
        if (!current.endsWith(old)) return false
        val edited = current.dropLast(old.length) + next
        if (suggestion != null) {
            if (edited.length > 4800) return false
            suggestion = edited
        } else {
            if (edited.length > 1600) return false
            note = edited
        }
        refresh()
        return true
    }

    fun delete(graphemeLength: (String) -> Int) {
        if (!editing) return
        val current = suggestion
        if (current != null) suggestion = current.dropLast(graphemeLength(current))
        else note = note.dropLast(graphemeLength(note))
        refresh()
    }

    fun render(parent: LinearLayout, views: KeyboardViews) {
        if (!open) return
        val box = views.vertical().apply {
            setPadding(views.dp(4), views.dp(2), views.dp(4), views.dp(6))
        }
        val title = views.horizontal()
        views.weighted(title, views.label("Message assistant", strong = true), 3f)
        views.weighted(title, views.key("Close", "Close AI panel", height = 36) { close() })
        box.addView(title)
        when {
            editing -> renderEditor(box, views)
            suggestion != null -> renderPreview(box, views)
            task != null -> {
                box.addView(views.label("Generating… You can keep typing."))
                box.addView(views.key("Cancel generation", height = 36) {
                    task?.cancel()
                    task = null
                    status = "Generation canceled."
                    refresh()
                })
            }
            else -> renderRequest(box, views)
        }
        status?.let { message -> box.addView(views.label(message).apply { accessibilityLiveRegion = 1 }) }
        usage?.let { allowance -> box.addView(views.label(allowance.label, 12f)) }
        parent.addView(views.scrollPanel(box))
    }

    private fun renderEditor(box: LinearLayout, views: KeyboardViews) {
        val text = suggestion ?: note
        val editedValue = views.label(text.takeIf(String::isNotEmpty) ?: "Use the keyboard to enter text.", 17f)
        editedValue.maxLines = 5
        box.addView(editedValue)
        box.addView(views.key("Done editing", height = 36, accented = true) {
            editing = false
            refresh()
        })
    }

    private fun renderRequest(box: LinearLayout, views: KeyboardViews) {
        box.addView(views.chips(Communication.modes, mode) {
            mode = it
            status = null
            refresh()
        })
        val input = if (mode == "Rewrite") rewrite?.text else note
        val help = when (mode) {
            "Rewrite" -> "Select text or place the cursor at the end of your draft, then reopen AI."
            "Reply" -> "Paste the message you want to answer or type it here."
            else -> "Type the thought or feeling you want to express."
        }
        box.addView(views.label(input?.takeIf(String::isNotBlank)?.take(140) ?: help).apply { maxLines = 2 })
        if (mode != "Rewrite") {
            val row = views.horizontal()
            views.weighted(row, views.key("Paste input", height = 36, fontSize = 13f) {
                val clipboard = paste()
                if (clipboard == null) status = "Clipboard has no text within 1,600 characters."
                else {
                    note = clipboard
                    status = null
                }
                refresh()
            })
            views.weighted(row, views.key(if (note.isEmpty()) "Type input" else "Edit input", height = 36, fontSize = 13f) {
                editing = true
                status = null
                refresh()
            })
            box.addView(row)
        }
        val favorites = (listOf("") + settings.favorites + Communication.styles).distinct()
        box.addView(views.chips(favorites, style, { it.ifEmpty { "Style: Auto" } }) {
            style = it
            refresh()
        })
        val footer = views.horizontal()
        views.weighted(footer, views.key("Generate", height = 36, accented = true) { generate() }, 1.4f)
        views.weighted(footer, views.key(if (moreOptions) "Fewer options" else "More options", height = 36, fontSize = 13f) {
            moreOptions = !moreOptions
            refresh()
        })
        box.addView(views.label("Generate sends this text and your writing preferences to the AI service.", 12f))
        box.addView(footer)
        if (moreOptions) {
            val controls = views.horizontal()
            views.weighted(controls, views.key(intensity.replaceFirstChar(Char::uppercase), "Intensity: $intensity", height = 36, fontSize = 13f) {
                intensity = next(Communication.intensities, intensity)
                refresh()
            })
            views.weighted(controls, views.key(relationship.ifEmpty { "Relationship" }.replaceFirstChar(Char::uppercase), "Relationship: ${relationship.ifEmpty { "none" }}", height = 36, fontSize = 13f) {
                relationship = next(Communication.relationships, relationship)
                refresh()
            })
            box.addView(controls)
        }
    }

    private fun renderPreview(box: LinearLayout, views: KeyboardViews) {
        val text = suggestion ?: return
        box.addView(views.label(text.ifEmpty { "Your suggestion is empty." }, 17f))
        val actions = views.horizontal()
        views.weighted(actions, views.key(if (stale) "Insert safely" else if (mode == "Rewrite") "Replace" else "Insert", accented = true, height = 36, fontSize = 13f) {
            if (text.isNotBlank()) {
                val applied = if (stale || mode != "Rewrite") insert(text) else use(rewrite, text)
                if (applied) close() else {
                    stale = true
                    status = "Draft changed or the editor refused the change. Insert safely or copy."
                    refresh()
                }
            }
        })
        views.weighted(actions, views.key("Edit", "Edit suggestion", height = 36, fontSize = 13f) {
            editing = true
            refresh()
        })
        views.weighted(actions, views.key("Copy", "Copy suggestion", height = 36, fontSize = 13f) {
            if (text.isNotBlank()) copy(text)
        })
        box.addView(actions)
        box.addView(views.key("Regenerate", height = 36) { generate() })
    }

    private fun generate() {
        val input = if (mode == "Rewrite") rewrite?.text.orEmpty() else note
        if (input.isBlank() || input.length > 1600) {
            status = "Provide between 1 and 1,600 characters."
            refresh()
            return
        }
        suggestion = null
        status = null
        stale = false
        task = client.generate(GenerationRequest(mode, input, style, intensity, relationship, settings.aiLanguage, settings.requestPreferences())) { result ->
            task = null
            result.onSuccess {
                suggestion = it.suggestion
                usage = it.usage
            }.onFailure { status = it.message ?: "AI is temporarily unavailable." }
            refresh()
        }
        refresh()
    }

    private fun next(values: List<String>, value: String) = values[(values.indexOf(value) + 1) % values.size]
}
