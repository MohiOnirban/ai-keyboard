package com.kothakeys.keyboard

import android.icu.text.BreakIterator
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

internal data class EditorTraits(
    val sensitive: Boolean = false,
    val numeric: Boolean = false,
    val address: Boolean = false,
    val url: Boolean = false,
    val noSuggestions: Boolean = false,
    val enterAction: Int = EditorInfo.IME_ACTION_NONE
) {
    val aiAllowed get() = !sensitive && !numeric && !address
    val wordAssistance get() = aiAllowed && !noSuggestions

    companion object {
        fun from(info: EditorInfo?): EditorTraits {
            if (info == null) return EditorTraits(sensitive = true)
            val kind = info.inputType and InputType.TYPE_MASK_CLASS
            val variant = info.inputType and InputType.TYPE_MASK_VARIATION
            val numeric = kind in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)
            val address = kind == InputType.TYPE_CLASS_TEXT && variant in listOf(
                InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            )
            val sensitive = when (kind) {
                InputType.TYPE_CLASS_TEXT -> variant in listOf(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_FILTER
                )
                InputType.TYPE_CLASS_NUMBER -> variant == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> false
                else -> true
            } || (Build.VERSION.SDK_INT >= 26 && info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            val action = if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) EditorInfo.IME_ACTION_NONE
                else info.imeOptions and EditorInfo.IME_MASK_ACTION
            return EditorTraits(
                sensitive = sensitive,
                numeric = numeric,
                address = address,
                url = kind == InputType.TYPE_CLASS_TEXT && variant == InputType.TYPE_TEXT_VARIATION_URI,
                noSuggestions = info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0,
                enterAction = action
            )
        }
    }
}

internal class EditorAccess {
    var start = -1
    var end = -1
    private val graphemes = BreakIterator.getCharacterInstance()

    fun update(start: Int, end: Int) {
        this.start = start
        this.end = end
    }

    fun snapshot(connection: InputConnection, afterLimit: Int = 64): TextSnapshot? {
        if (start >= 0 && end >= 0 && kotlin.math.abs(start - end) > 1600) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val surrounding = connection.getSurroundingText(1601, afterLimit, 0)
            if (surrounding != null) {
                val text = surrounding.text
                if (text.length > 3201 + afterLimit) return null
                val left = minOf(surrounding.selectionStart, surrounding.selectionEnd)
                val selectedLength = kotlin.math.abs(surrounding.selectionEnd - surrounding.selectionStart)
                val offset = if (surrounding.offset >= 0) surrounding.offset
                    else if (start >= 0 && end >= 0 && kotlin.math.abs(start - end) == selectedLength) minOf(start, end) - left
                    else return null
                return TextSnapshot(text.toString(), offset, surrounding.selectionStart, surrounding.selectionEnd)
            }
        }
        if (start < 0 || end < 0) return null
        val selectionSize = kotlin.math.abs(start - end)
        val selected = if (selectionSize == 0) "" else connection.getSelectedText(0)?.toString() ?: return null
        if (selected.length != selectionSize) return null
        val left = connection.getTextBeforeCursor(1601, 0)?.toString() ?: return null
        val right = connection.getTextAfterCursor(afterLimit, 0)?.toString() ?: return null
        return TextSnapshot(left + selected + right, minOf(start, end) - left.length, left.length, left.length + selected.length)
    }

    fun replace(connection: InputConnection, target: RewriteTarget, suggestion: String): Boolean {
        if (!target.stillMatches(snapshot(connection))) return false
        if (target.snapshot.start != target.snapshot.end) return connection.commitText(suggestion, 1)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return connection.replaceText(target.start, target.end, suggestion, 1, null)
    }

    fun selectAll(connection: InputConnection) {
        val current = snapshot(connection, afterLimit = 1601)
        if (current != null && current.offset == 0 && current.text.length <= 1600) {
            connection.setSelection(0, current.text.length)
        } else connection.performContextMenuAction(android.R.id.selectAll)
    }

    fun insertWithoutReplacing(connection: InputConnection, text: String): Boolean {
        val knownSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connection.getSurroundingText(0, 0, 0)?.let {
                if (it.selectionStart == it.selectionEnd) return connection.commitText(text, 1)
                if (it.offset < 0) return false
                it.offset + maxOf(it.selectionStart, it.selectionEnd)
            }
        } else if (start >= 0 && end >= 0) {
            if (start == end && connection.getSelectedText(0).isNullOrEmpty()) return connection.commitText(text, 1)
            maxOf(start, end)
        } else null
        if (knownSelection == null || !connection.setSelection(knownSelection, knownSelection)) return false
        if (!connection.getSelectedText(0).isNullOrEmpty()) return false
        return connection.commitText(text, 1)
    }

    fun delete(connection: InputConnection, sensitive: Boolean) {
        // The protected editor handles deletion without disclosing even a partial password.
        if (sensitive) {
            connection.key(KeyEvent.KEYCODE_DEL)
            return
        }
        if (start < 0 || end < 0) {
            connection.key(KeyEvent.KEYCODE_DEL)
            return
        }
        if (start != end) {
            connection.commitText("", 1)
            return
        }
        val before = connection.getTextBeforeCursor(256, 0)?.toString()
        if (before.isNullOrEmpty()) {
            connection.key(KeyEvent.KEYCODE_DEL)
            return
        }
        connection.deleteSurroundingText(lastGraphemeLength(before), 0)
    }

    fun lastGraphemeLength(text: String): Int {
        if (text.isEmpty()) return 0
        graphemes.setText(text)
        graphemes.last()
        var boundary = graphemes.previous()
        while (boundary > 0 && joinsPreviousGrapheme(text, boundary)) boundary = graphemes.previous()
        return text.length - boundary.coerceAtLeast(0)
    }

    fun move(connection: InputConnection, right: Boolean) {
        connection.key(if (right) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    private fun InputConnection.key(code: Int) {
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun joinsPreviousGrapheme(text: String, boundary: Int): Boolean {
        val previous = text[boundary - 1]
        val next = text[boundary]
        if (previous == '\u200d' || next == '\u200d') return true
        if (next !in '\u0995'..'\u09b9' && next !in '\u09dc'..'\u09df') return false
        return previous == '\u09cd' || (previous == '\u200c' && text.getOrNull(boundary - 2) == '\u09cd')
    }
}
