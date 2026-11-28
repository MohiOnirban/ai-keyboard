package com.kothakeys.keyboard

import android.icu.text.BreakIterator
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

internal data class EditorTraits(
    val sensitive: Boolean = false,
    val numeric: Boolean = false,
    val address: Boolean = false,
    val url: Boolean = false,
    val noSuggestions: Boolean = false,
    val enterAction: Int = EditorInfo.IME_ACTION_NONE,
    val phone: Boolean = false,
    val decimal: Boolean = false,
    val signed: Boolean = false,
    val dateTime: Boolean = false,
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
                enterAction = action,
                phone = kind == InputType.TYPE_CLASS_PHONE,
                decimal = kind == InputType.TYPE_CLASS_NUMBER && !sensitive && info.inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0,
                signed = kind == InputType.TYPE_CLASS_NUMBER && !sensitive && info.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0,
                dateTime = kind == InputType.TYPE_CLASS_DATETIME,
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
                if (text.length > 3201 + afterLimit || surrounding.selectionStart !in 0..text.length ||
                    surrounding.selectionEnd !in 0..text.length) return null
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

    /** A fresh absolute position is required before moving or replacing a host range. */
    fun editSnapshot(connection: InputConnection, proveWordEnd: Boolean = false): TextSnapshot? {
        if (Build.VERSION.SDK_INT >= 31) {
            val sample = connection.getSurroundingText(1601, 64, 0) ?: return null
            if (sample.offset < 0 || sample.text.length > 3265 ||
                sample.selectionStart !in 0..sample.text.length || sample.selectionEnd !in 0..sample.text.length) return null
            val text = sample.text.toString()
            val suffix = text.substring(maxOf(sample.selectionStart, sample.selectionEnd))
            val needEnd = proveWordEnd && sample.offset <= 4096 && suffix.length < 64 &&
                suffix.all { it.isLetter() || Character.getType(it) in 6..8 || it in "'’\u200c\u200d" }
            val complete = if (needEnd) connection.getExtractedText(ExtractedTextRequest(), 0) else null
            val proven = complete != null && complete.partialStartOffset == -1 && complete.partialEndOffset == -1 &&
                complete.startOffset == 0 && complete.text?.length == sample.offset + text.length &&
                complete.selectionStart == sample.offset + sample.selectionStart &&
                complete.selectionEnd == sample.offset + sample.selectionEnd &&
                complete.text?.subSequence(sample.offset, complete.text.length)?.toString() == text
            return TextSnapshot(text, sample.offset, sample.selectionStart, sample.selectionEnd, proven)
        }
        val sample = connection.getExtractedText(ExtractedTextRequest().apply { if (!proveWordEnd) hintMaxChars = 3265 }, 0) ?: return null
        val text = sample.text ?: return null
        if (sample.partialStartOffset >= 0 || sample.startOffset < 0 || sample.selectionStart !in 0..text.length ||
            sample.selectionEnd !in 0..text.length || kotlin.math.abs(sample.selectionStart - sample.selectionEnd) > 1600) return null
        val left = (minOf(sample.selectionStart, sample.selectionEnd) - 1601).coerceAtLeast(0)
        val right = (maxOf(sample.selectionStart, sample.selectionEnd) + 64).coerceAtMost(text.length)
        return TextSnapshot(text.subSequence(left, right).toString(), sample.startOffset + left,
            sample.selectionStart - left, sample.selectionEnd - left,
            proveWordEnd && sample.partialEndOffset == -1 && sample.startOffset == 0 && right == text.length)
    }

    fun replace(connection: InputConnection, target: RewriteTarget, suggestion: String): Boolean {
        if (!target.stillMatches(snapshot(connection))) return false
        if (target.snapshot.start != target.snapshot.end) return connection.commitText(suggestion, 1)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return connection.replaceText(target.start, target.end, suggestion, 1, null)
    }

    fun replaceRange(connection: InputConnection, range: EditorRange, replacement: String): Boolean {
        if (!range.matches(editSnapshot(connection, proveWordEnd = range.needsEndProof))) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return connection.replaceText(range.start, range.start + range.value.length, replacement, 1, null)
        }
        connection.beginBatchEdit()
        try {
            connection.finishComposingText()
            if (!connection.setSelection(range.start, range.start + range.value.length)) return false
            if (connection.getSelectedText(0)?.toString().orEmpty() == range.value &&
                connection.commitText(replacement, 1)) return true
            connection.setSelection(range.selectionStart, range.selectionEnd)
            return false
        } finally {
            connection.endBatchEdit()
        }
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
