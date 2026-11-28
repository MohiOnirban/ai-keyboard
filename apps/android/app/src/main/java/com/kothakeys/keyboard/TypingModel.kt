package com.kothakeys.keyboard

import org.json.JSONObject
import java.util.Locale

internal enum class TypingLanguage(val label: String) {
    English("English"), Bangla("বাংলা"), Phonetic("বাংলা · phonetic"), Banglish("Banglish")
}

internal class Phonetics(overrides: JSONObject, private val avro: Avro) {
    private val words = overrides.getJSONObject("words").let { entries ->
        entries.keys().asSequence().associateWith(entries::getString)
    }

    fun banglishCandidates(raw: String): List<String> {
        if (raw.length < 2) return emptyList()
        val lower = raw.lowercase(Locale.ROOT)
        return words.keys.asSequence().filter { it != lower && it.startsWith(lower) }
            .sortedWith(compareBy({ it.length }, { it })).take(3).map { matchWordCase(it, raw) }.toList()
    }

    fun candidates(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        val lower = raw.lowercase(Locale.ROOT)
        val parsed = avro.render(raw)
        val exact = words[lower]
        val hasExplicitAvroCase = raw != lower && avro.render(lower) != parsed
        val primary = if (hasExplicitAvroCase) parsed else exact ?: parsed
        val spelling = if (hasExplicitAvroCase) exact else parsed.takeIf { it != primary }
        val completion = if (lower.length < 3) null else words.asSequence()
            .filter { (key, value) -> key != lower && key.startsWith(lower) && value != primary }
            .minWithOrNull(compareBy({ it.key.length }, { it.key }))
            ?.value
        return listOfNotNull(primary, spelling ?: completion, raw).distinct()
    }
}

internal fun matchWordCase(value: String, source: String): String = when {
    source.isEmpty() -> value
    source.length > 1 && source.all(Char::isUpperCase) -> value.uppercase(Locale.ROOT)
    source.first().isUpperCase() -> value.replaceFirstChar(Char::uppercase)
    else -> value
}

internal data class KeySpec(val text: String, val alternate: String? = null) {
    val label: String
        get() = if (text.length == 1 && Character.getType(text[0]) in 6..8) "◌$text" else text
}

private fun row(letters: String, alternates: List<String?> = emptyList()) =
    letters.split(' ').mapIndexed { index, value -> KeySpec(value, alternates.getOrNull(index)) }

internal object KeyboardLayouts {
    val english = listOf(
        row("q w e r t y u i o p", "1234567890".map(Char::toString)),
        row("a s d f g h j k l"),
        row("z x c v b n m")
    )
    val bangla = listOf(
        row("অ আ ই উ এ ও ক খ গ ঘ", listOf("ঔ", "ঐ", "ঈ", "ঊ", "ঋ", "ঌ", "১", "২", "৩", "৪")),
        row("চ ছ জ ট ড ত দ ন প ব", listOf("ঞ", "ঝ", "ঙ", "ঠ", "ঢ", "থ", "ধ", "ণ", "ফ", "ভ")),
        row("ম য র ল শ স হ া ি ্", listOf("ৎ", "য়", "ড়", "ঢ়", "ষ", "ঃ", "ঁ", "ী", "ৃ", "ং")),
        row("ী ু ূ ে ৈ ো ৌ ং ঁ ঃ")
    )
    val banglaExtra = listOf(
        row("ঈ ঊ ঋ ঐ ঔ ঙ ঞ ঝ ঠ ঢ"),
        row("ণ থ ধ ফ ভ ষ য় ড় ঢ় ৎ"),
        row("া ি ী ু ূ ৃ ে ৈ ো ৌ"),
        row("১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০")
    )
    val symbols = listOf(
        row("1 2 3 4 5 6 7 8 9 0"),
        row("@ # $ _ & - + ( ) /", listOf("©", "®", "৳", "%", "§", "−", "=", "[", "]", "÷")),
        row("* \" ' : ; ! ?"),
    )
    val symbolsExtra = listOf(
        row("~ ` | • √ π ÷ × § ∆", listOf(null, null, null, "·", "±", null, "≤", "≥")),
        row("£ ¢ € ¥ ^ ° = { } \\", listOf("৳", "₹", null, null, null, null, "≠", "<", ">")),
        row("% © ® ™ ✓ [ ]", listOf("‰", "«", "»", "…")),
    )
    val emoji = listOf(
        row("😀 😂 🥹 😊 😅 🙂 🙃 😍 🥰 😘"),
        row("😭 😔 😌 😎 🙄 😳 🤭 🤔 🤗 😴"),
        row("❤️ 💚 🫶 🙏 👍 👎 👀 🔥 ✨ 🎉"),
        row("💯 😂 🤝 💌 🌸 🌿 🌧️ ☕ 🇧🇩 👨‍👩‍👧")
    )
}

internal data class TextSnapshot(
    val text: String,
    val offset: Int,
    val selectionStart: Int,
    val selectionEnd: Int
) {
    val start get() = minOf(selectionStart, selectionEnd)
    val end get() = maxOf(selectionStart, selectionEnd)

    fun target(maxLength: Int = 1600, draftAllowed: Boolean = true): RewriteTarget? {
        if (offset < 0 || start < 0 || end > text.length) return null
        if (start != end) {
            if (end - start > maxLength) return null
            val selection = text.substring(start, end)
            return selection.takeIf(String::isNotBlank)?.let { RewriteTarget(this, offset + start, offset + end, it) }
        }
        if (!draftAllowed || end == 0 || end > maxLength || offset != 0 || end != text.length) return null
        val draft = text.substring(0, end)
        return draft.takeIf(String::isNotBlank)?.let { RewriteTarget(this, 0, end, it) }
    }
}

internal data class RewriteTarget(val snapshot: TextSnapshot, val start: Int, val end: Int, val text: String) {
    fun stillMatches(current: TextSnapshot?) = current == snapshot
}
