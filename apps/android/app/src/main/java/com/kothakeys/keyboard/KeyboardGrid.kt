package com.kothakeys.keyboard

import android.annotation.SuppressLint
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import java.util.Locale

internal enum class ContentMode(val icon: ToolbarIcon? = null) {
    Typing, Grammar(ToolbarIcon.Grammar), Tone(ToolbarIcon.Tone), Ask(ToolbarIcon.Ask),
    Translation(ToolbarIcon.Translate), Emoji(ToolbarIcon.Emoji), Clipboard(ToolbarIcon.Clipboard),
    More(ToolbarIcon.More), Voice(ToolbarIcon.Voice),
    Language,
    Theme(ToolbarIcon.Theme), TextEditing(ToolbarIcon.Edit), Layout(ToolbarIcon.Layout),
}

internal enum class KeyLayer { Alphabet, Symbols, ExtraSymbols, Numbers }

internal data class TypingState(
    val language: TypingLanguage,
    val layer: KeyLayer = KeyLayer.Alphabet,
    val shift: Boolean = false,
    val caps: Boolean = false,
)

internal class KeyboardGrid(
    private val views: KeyboardViews,
    private val type: (String) -> Unit,
    private val shift: () -> Unit,
    private val layer: (KeyLayer) -> Unit,
    private val changeLanguage: () -> Unit,
    private val chooseLanguage: () -> Unit,
    private val delete: () -> Unit,
    private val startRepeat: () -> Unit,
    private val stopRepeat: () -> Unit,
    private val enter: () -> Unit,
    private val openEmoji: () -> Unit,
) {
    fun layout(state: TypingState, traits: EditorTraits, enterLabel: String, panelEditing: Boolean): LinearLayout {
        val grid = views.vertical()
        when {
            traits.numeric -> restrictedNumbers(grid, traits, enterLabel)
            state.layer == KeyLayer.Numbers -> numbers(grid, enterLabel)
            state.layer == KeyLayer.Symbols || state.layer == KeyLayer.ExtraSymbols -> symbols(grid, state, enterLabel)
            state.language == TypingLanguage.Bangla -> bangla(grid, state, enterLabel)
            else -> alphabet(grid, state, traits, enterLabel, panelEditing)
        }
        return grid
    }

    fun emojis(enterLabel: String): LinearLayout = views.vertical().apply {
        KeyboardLayouts.emoji.forEach { specs ->
            addRow(this) { specs.forEach { glyph(this, it, fontSize = 20f) } }
        }
        addRow(this) {
            function(this, ",", "Comma", 1f) { type(",") }
            function(this, "Space", "Space", 6f) { type(" ") }
            function(this, ".", "Period", 1f) { type(".") }
            backspace(this, 1f)
            enter(this, enterLabel, 1f)
        }
    }

    private fun alphabet(
        grid: LinearLayout, state: TypingState, traits: EditorTraits, enterLabel: String, panelEditing: Boolean,
    ) {
        fun letters(index: Int, line: LinearLayout) {
            KeyboardLayouts.english[index].forEach { spec ->
                val key = if (state.shift || state.caps) spec.copy(text = spec.text.uppercase(Locale.ROOT)) else spec
                glyph(line, key, fontSize = 26f)
            }
        }
        addRow(grid) { letters(0, this) }
        addRow(grid) {
            views.gridSpacer(this, 0.5f)
            letters(1, this)
            views.gridSpacer(this, 0.5f)
        }
        addRow(grid) {
            val shiftIcon = if (state.caps) ToolbarIcon.CapsLock else ToolbarIcon.Shift
            views.gridItem(this, views.typingIcon(shiftIcon, "Shift. Double tap for caps lock.",
                accented = state.shift || state.caps, action = shift), 1.5f)
            letters(2, this)
            backspace(this, 1.5f)
        }
        addRow(grid) {
            function(this, "?123", "Toggle numbers and symbols", 1.5f, fontSize = 16f, face = TypingKeyFace.Pill) {
                layer(KeyLayer.Symbols)
            }
            punctuation(this, traits, 1f)
            if (!traits.sensitive) {
                if (panelEditing) language(this, 1f)
                else views.gridItem(this, views.typingIcon(ToolbarIcon.Emoji, "Emoji shortcut",
                    function = false, action = openEmoji))
            }
            space(this, state.language, if (traits.sensitive) 5f else 4f,
                chooseLanguage = !traits.sensitive && !traits.address && !panelEditing)
            function(this, ".", "Period", 1f, fontSize = 18f) { type(".") }
            enter(this, enterLabel, 1.5f)
        }
    }

    private fun bangla(grid: LinearLayout, state: TypingState, enterLabel: String) {
        val rows = if (state.shift) KeyboardLayouts.banglaExtra else KeyboardLayouts.bangla
        rows.forEach { specs -> addRow(grid) { specs.forEach { glyph(this, it, fontSize = 18f) } } }
        addRow(grid) {
            function(this, "⇧", "More Bangla letters", 1.1f, accented = state.shift, fontSize = 20f, action = shift)
            function(this, "?123", "Toggle numbers and symbols", 1.35f, face = TypingKeyFace.Pill) { layer(KeyLayer.Symbols) }
            function(this, ",", "Comma", 0.75f) { type(",") }
            language(this, 1f)
            space(this, state.language, 2.5f)
            function(this, "।", "Bengali full stop", 0.75f) { type("।") }
            backspace(this, 1.2f)
            enter(this, enterLabel, 1.35f)
        }
    }

    private fun symbols(grid: LinearLayout, state: TypingState, enterLabel: String) {
        val extra = state.layer == KeyLayer.ExtraSymbols
        val rows = if (extra) KeyboardLayouts.symbolsExtra else KeyboardLayouts.symbols
        rows.take(2).forEach { specs -> addRow(grid) { specs.forEach { glyph(this, it) } } }
        addRow(grid) {
            val marker = if (extra) "?123" else "=\\<"
            function(this, marker, if (extra) "Primary symbols" else "More symbols", 1.5f) {
                layer(if (extra) KeyLayer.Symbols else KeyLayer.ExtraSymbols)
            }
            rows[2].forEach { glyph(this, it) }
            backspace(this, 1.5f)
        }
        addRow(grid) {
            function(this, "ABC", "Alphabet layout", 1.5f, face = TypingKeyFace.Pill) { layer(KeyLayer.Alphabet) }
            function(this, ",", "Comma", 1f) { type(",") }
            views.gridItem(this, views.typingIcon(ToolbarIcon.Numbers, function = false,
                action = { layer(KeyLayer.Numbers) }))
            space(this, state.language, 4f)
            function(this, ".", "Period", 1f, fontSize = 18f) { type(".") }
            enter(this, enterLabel, 1.5f)
        }
    }

    private fun numbers(grid: LinearLayout, enterLabel: String) {
        val top = views.horizontal()
        val operators = views.operatorStack().apply {
            listOf("+" to "Plus", "-" to "Minus", "*" to "Multiply", "/" to "Divide").forEach { (value, name) ->
                views.rowFill(this, views.stackedOperator(value, name) { type(value) })
            }
        }
        val digits = views.vertical().apply {
            (1..9).chunked(3).forEach { values ->
                addRow(this) {
                    values.forEach {
                        digit(this, it.toString(), weight = 1f, fontSize = 28f, face = TypingKeyFace.Numeric)
                    }
                }
            }
        }
        val right = views.vertical().apply {
            addRow(this) { numericOperator(this, "%", 1f, face = TypingKeyFace.Numeric) }
            addRow(this) {
                function(this, "␣", "Space", 1f, fontSize = 20f, face = TypingKeyFace.Numeric) { type(" ") }
            }
            addRow(this) { backspace(this, 1f, face = TypingKeyFace.Numeric) }
        }
        views.gridItem(top, operators, 1.43f)
        views.gridItem(top, digits, 7.14f)
        views.gridItem(top, right, 1.43f)
        views.rowFill(grid, top, 3f)
        addRow(grid) {
            function(this, "ABC", "Alphabet layout", 1.5f, face = TypingKeyFace.Pill) { layer(KeyLayer.Alphabet) }
            function(this, ",", "Comma", 1.12f, fontSize = 18f) { type(",") }
            function(this, "!?#", "Symbol layout", 1.19f) { layer(KeyLayer.Symbols) }
            digit(this, "0", weight = 2.38f, fontSize = 28f)
            numericOperator(this, "=", 1.2f)
            numericOperator(this, ".", 1.18f)
            enter(this, enterLabel, 1.43f)
        }
    }

    private fun restrictedNumbers(grid: LinearLayout, traits: EditorTraits, enterLabel: String) {
        val extras = when {
            traits.phone -> listOf("+", "(", ")", "*", "#", "-", ",")
            traits.dateTime -> listOf("/", null, null, ":", null, null, null)
            else -> listOf(null, null, null, null, null,
                "-".takeIf { traits.signed }, ".".takeIf { traits.decimal })
        }
        addRow(grid) {
            numericOperator(this, extras[0])
            listOf("1", "2", "3").forEach { digit(this, it) }
            backspace(this, 1.7f)
        }
        addRow(grid) {
            numericOperator(this, extras[1])
            listOf("4", "5", "6").forEach { digit(this, it) }
            numericOperator(this, extras[2])
        }
        addRow(grid) {
            numericOperator(this, extras[3])
            listOf("7", "8", "9").forEach { digit(this, it) }
            numericOperator(this, extras[4])
        }
        addRow(grid) {
            numericOperator(this, extras[5])
            views.gridSpacer(this, 2.2f)
            digit(this, "0")
            numericOperator(this, extras[6], 2.2f)
            enter(this, enterLabel, 1.7f)
        }
    }

    private fun digit(
        row: LinearLayout, value: String, weight: Float = 2.2f, fontSize: Float = 23f,
        face: TypingKeyFace = TypingKeyFace.Regular,
    ) = views.gridItem(row, views.key(value, fontSize = fontSize, face = face) { type(value) }, weight)

    private fun numericOperator(
        row: LinearLayout, value: String?, weight: Float = 1.7f,
        face: TypingKeyFace = TypingKeyFace.Regular,
    ) {
        if (value == null) views.gridSpacer(row, weight)
        else function(row, value, when (value) {
            "÷" -> "Divide"; "×" -> "Multiply"; "+" -> "Plus"; "-" -> "Minus"
            "%" -> "Percent"; "=" -> "Equals"; "." -> "Decimal point"
            else -> value
        }, weight, fontSize = 18f, face = face) { type(value) }
    }

    private fun glyph(row: LinearLayout, spec: KeySpec, fontSize: Float = 23f) {
        views.gridItem(row, views.key(spec.label, alternate = spec.alternate, fontSize = fontSize, face = TypingKeyFace.Regular) { type(spec.text) }.apply {
            spec.alternate?.let { alternate -> setOnLongClickListener { type(alternate); true } }
        })
    }

    private fun punctuation(row: LinearLayout, traits: EditorTraits, weight: Float) {
        val mark = when { traits.url -> "/"; traits.address -> "@"; else -> "," }
        function(row, mark, if (mark == ",") "Comma" else mark, weight, fontSize = 16f) { type(mark) }
    }

    private fun language(row: LinearLayout, weight: Float) {
        val key = views.typingIcon(ToolbarIcon.Language, action = changeLanguage)
        key.setOnLongClickListener { chooseLanguage(); true }
        views.gridItem(row, key, weight)
    }

    private fun space(row: LinearLayout, language: TypingLanguage, weight: Float, chooseLanguage: Boolean = false) {
        views.gridItem(row, views.key(language.label, "Space", fontSize = 13f, face = TypingKeyFace.Regular) {
            type(" ")
        }.apply {
            if (Build.VERSION.SDK_INT >= 30) stateDescription = language.label
            if (chooseLanguage) setOnLongClickListener { this@KeyboardGrid.chooseLanguage(); true }
        }, weight)
    }

    @SuppressLint("ClickableViewAccessibility") // This listener observes release; the key still handles clicks.
    private fun backspace(row: LinearLayout, weight: Float, face: TypingKeyFace = TypingKeyFace.Regular) {
        val key = views.typingIcon(ToolbarIcon.Delete, face = face, action = delete)
        key.setOnLongClickListener { startRepeat(); true }
        key.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) stopRepeat()
            false
        }
        views.gridItem(row, key, weight)
    }

    private fun enter(row: LinearLayout, label: String, weight: Float) {
        val icon = when (label) {
            "Done" -> ToolbarIcon.Done
            "Next", "Go" -> ToolbarIcon.Forward
            "Search" -> ToolbarIcon.Search
            "Send" -> ToolbarIcon.Send
            else -> ToolbarIcon.Return
        }
        val description = if (label == "↵") "Enter" else "Enter: $label"
        views.gridItem(row, views.typingIcon(icon, description, accented = true, face = TypingKeyFace.Pill,
            action = enter), weight)
    }

    private fun function(
        row: LinearLayout, label: String, description: String, weight: Float,
        accented: Boolean = false, fontSize: Float = 14f, face: TypingKeyFace = TypingKeyFace.Regular,
        action: () -> Unit,
    ) = views.gridItem(row, views.key(label, description, accented, function = true, fontSize = fontSize, face = face, action = action), weight)

    private fun addRow(grid: LinearLayout, populate: LinearLayout.() -> Unit) =
        views.rowFill(grid, views.horizontal().apply(populate))
}
