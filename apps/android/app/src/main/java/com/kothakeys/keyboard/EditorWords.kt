package com.kothakeys.keyboard

/** A witnessed editor range. Small anchors protect selection and Undo against host-side edits. */
internal data class EditorRange(
    val start: Int, val value: String, val left: String, val right: String,
    val selectionStart: Int, val selectionEnd: Int, val needsEndProof: Boolean = false,
) {
    fun matches(snapshot: TextSnapshot?): Boolean {
        snapshot ?: return false
        if (needsEndProof && !snapshot.documentEndVerified) return false
        val first = start - snapshot.offset
        val last = first + value.length
        return first >= 0 && last <= snapshot.text.length &&
            snapshot.offset + snapshot.selectionStart == selectionStart &&
            snapshot.offset + snapshot.selectionEnd == selectionEnd &&
            snapshot.text.substring(first, last) == value &&
            snapshot.text.substring(0, first).takeLast(16) == left &&
            snapshot.text.substring(last).take(16) == right
    }

    fun after(replacement: String): EditorRange {
        val cursor = start + replacement.length
        return copy(value = replacement, selectionStart = cursor, selectionEnd = cursor)
    }
}

internal fun TextSnapshot.range(first: Int, last: Int): EditorRange? {
    if (offset < 0 || first !in 0..last || last > text.length || start < 0 || end > text.length) return null
    return EditorRange(offset + first, text.substring(first, last), text.substring(0, first).takeLast(16),
        text.substring(last).take(16), offset + selectionStart, offset + selectionEnd)
}

internal data class EditorWord(val range: EditorRange, val before: String, val following: String?)

/** Snapshot requests 64 UTF-16 units after selection. A token crossing the sample edge cannot be edited. */
internal fun TextSnapshot.wordAtCursor(): EditorWord? {
    if (offset < 0 || start < 0 || end > text.length || end - start > 48) return null
    fun wordChar(c: Char) = c.isLetter() || Character.getType(c) in 6..8 || c in "'’\u200c\u200d"
    var first = start
    var last = end
    while (first > 0 && wordChar(text[first - 1])) first--
    while (last < text.length && wordChar(text[last])) last++
    if (first == last || first == 0 && offset > 0 || last == text.length && (!documentEndVerified || last - end >= 64)) return null
    if (last - first > 48 || !text.substring(first, last).all(::wordChar)) return null
    val token = range(first, last) ?: return null
    if (OfflinePrediction.dictionaryWord(token.value) == null) return null
    val before = text.substring(0, first)
    val after = text.substring(last)
    val leftToken = before.takeLastWhile { !it.isWhitespace() }
    val rightToken = after.takeWhile { !it.isWhitespace() }
    if (OfflinePrediction.protectedTokenPrefix(leftToken + token.value + rightToken) ||
        leftToken.endsWith('-') || rightToken.startsWith('-')) return null
    val following = after.takeIf { it.firstOrNull()?.isWhitespace() == true }?.trimStart(' ', '\t')
        ?.takeWhile(::wordChar)?.takeIf(String::isNotEmpty)
    return EditorWord(token.copy(needsEndProof = last == text.length), before.takeLast(96), following)
}
