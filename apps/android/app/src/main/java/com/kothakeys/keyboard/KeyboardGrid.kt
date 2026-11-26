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
) {
    fun layout(state: TypingState, traits: EditorTraits, enterLabel: String): LinearLayout {
        val grid = views.vertical()
        when {
            traits.numeric || state.layer == KeyLayer.Numbers -> numbers(grid, traits, enterLabel)
            state.layer == KeyLayer.Symbols || state.layer == KeyLayer.ExtraSymbols -> symbols(grid, state, enterLabel)
            state.language == TypingLanguage.Bangla -> bangla(grid, state, enterLabel)
            else -> alphabet(grid, state, traits, enterLabel)
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

    private fun alphabet(grid: LinearLayout, state: TypingState, traits: EditorTraits, enterLabel: String) {
        fun letters(index: Int, line: LinearLayout) {
            KeyboardLayouts.english[index].forEach { spec ->
                glyph(line, if (state.shift || state.caps) spec.copy(text = spec.text.uppercase(Locale.ROOT)) else spec)
            }
        }
        addRow(grid) { letters(0, this) }
        addRow(grid) {
            views.gridSpacer(this, 0.5f)
            letters(1, this)
            views.gridSpacer(this, 0.5f)
        }
        addRow(grid) {
            function(this, if (state.caps) "⇪" else "⇧", "Shift. Double tap for caps lock.", 1.5f,
                accented = state.shift || state.caps, fontSize = 21f, action = shift)
            letters(2, this)
            backspace(this, 1.5f)
        }
        addRow(grid) {
            function(this, "?123", "Toggle numbers and symbols", 1.43f) { layer(KeyLayer.Symbols) }
            punctuation(this, traits, 0.87f)
            if (!traits.sensitive && !traits.address) language(this, 1.08f)
            space(this, state.language, if (traits.sensitive || traits.address) 5.26f else 4.18f)
            function(this, ".", "Period", 0.87f) { type(".") }
            enter(this, enterLabel, 1.57f)
        }
    }

    private fun bangla(grid: LinearLayout, state: TypingState, enterLabel: String) {
        val rows = if (state.shift) KeyboardLayouts.banglaExtra else KeyboardLayouts.bangla
        rows.forEach { specs -> addRow(grid) { specs.forEach { glyph(this, it, fontSize = 18f) } } }
        addRow(grid) {
            function(this, "⇧", "More Bangla letters", 1.1f, accented = state.shift, fontSize = 20f, action = shift)
            function(this, "?123", "Toggle numbers and symbols", 1.35f) { layer(KeyLayer.Symbols) }
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
            val marker = if (extra) "1/2" else "2/2"
            function(this, marker, if (extra) "Primary symbols" else "More symbols", 1.5f) {
                layer(if (extra) KeyLayer.Symbols else KeyLayer.ExtraSymbols)
            }
            rows[2].forEach { glyph(this, it) }
            backspace(this, 1.5f)
        }
        addRow(grid) {
            function(this, "ABC", "Alphabet layout", 1.43f) { layer(KeyLayer.Alphabet) }
            function(this, ",", "Comma", 0.87f) { type(",") }
            function(this, "123", "Numeric keypad", 1.08f) { layer(KeyLayer.Numbers) }
            space(this, state.language, 4.18f)
            function(this, ".", "Period", 0.87f) { type(".") }
            enter(this, enterLabel, 1.57f)
        }
    }

    private fun numbers(grid: LinearLayout, traits: EditorTraits, enterLabel: String) {
        val extras = when {
            traits.phone -> listOf("+", "(", ")", "*", "#", "-", ",")
            traits.dateTime -> listOf("/", null, null, ":", null, null, null)
            traits.numeric -> listOf(null, null, null, null, null,
                "-".takeIf { traits.signed }, ".".takeIf { traits.decimal })
            else -> listOf("÷", "×", "-", "+", "%", "=", ".")
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
            if (traits.numeric) views.gridSpacer(this, 2.2f)
            else {
                function(this, "ABC", "Alphabet layout", 1.1f) { layer(KeyLayer.Alphabet) }
                function(this, "?123", "Symbol layout", 1.1f) { layer(KeyLayer.Symbols) }
            }
            digit(this, "0")
            numericOperator(this, extras[6], 2.2f)
            enter(this, enterLabel, 1.7f)
        }
    }

    private fun digit(row: LinearLayout, value: String, weight: Float = 2.2f) =
        views.gridItem(row, views.key(value, fontSize = 23f) { type(value) }, weight)

    private fun numericOperator(row: LinearLayout, value: String?, weight: Float = 1.7f) {
        if (value == null) views.gridSpacer(row, weight)
        else function(row, value, when (value) {
            "÷" -> "Divide"; "×" -> "Multiply"; "+" -> "Plus"; "-" -> "Minus"
            "%" -> "Percent"; "=" -> "Equals"; "." -> "Decimal point"
            else -> value
        }, weight, fontSize = 18f) { type(value) }
    }

    private fun glyph(row: LinearLayout, spec: KeySpec, fontSize: Float = 19f) {
        views.gridItem(row, views.key(spec.label, alternate = spec.alternate, fontSize = fontSize) { type(spec.text) }.apply {
            spec.alternate?.let { alternate -> setOnLongClickListener { type(alternate); true } }
        })
    }

    private fun punctuation(row: LinearLayout, traits: EditorTraits, weight: Float) {
        val mark = when { traits.url -> "/"; traits.address -> "@"; else -> "," }
        function(row, mark, if (mark == ",") "Comma" else mark, weight) { type(mark) }
    }

    private fun language(row: LinearLayout, weight: Float) {
        val key = views.key("文", "Change typing language", function = true, fontSize = 16f, action = changeLanguage)
        key.setOnLongClickListener { chooseLanguage(); true }
        views.gridItem(row, key, weight)
    }

    private fun space(row: LinearLayout, language: TypingLanguage, weight: Float) {
        views.gridItem(row, views.key(language.label, "Space", fontSize = 13f) { type(" ") }.apply {
            if (Build.VERSION.SDK_INT >= 30) stateDescription = language.label
        }, weight)
    }

    @SuppressLint("ClickableViewAccessibility") // This listener observes release; the key still handles clicks.
    private fun backspace(row: LinearLayout, weight: Float) {
        val key = views.key("⌫", "Backspace", function = true, action = delete)
        key.setOnLongClickListener { startRepeat(); true }
        key.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) stopRepeat()
            false
        }
        views.gridItem(row, key, weight)
    }

    private fun enter(row: LinearLayout, label: String, weight: Float) =
        function(row, label, if (label == "↵") "Enter" else "Enter: $label", weight,
            accented = true, fontSize = if (label == "↵") 21f else 13f, action = enter)

    private fun function(
        row: LinearLayout, label: String, description: String, weight: Float,
        accented: Boolean = false, fontSize: Float = 13f, action: () -> Unit,
    ) = views.gridItem(row, views.key(label, description, accented, function = true, fontSize = fontSize, action = action), weight)

    private fun addRow(grid: LinearLayout, populate: LinearLayout.() -> Unit) =
        views.rowFill(grid, views.horizontal().apply(populate))
}
