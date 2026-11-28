package com.kothakeys.keyboard

import java.text.Normalizer
import java.util.Locale
import kotlin.math.log2

internal data class PredictionContext(
    val previous: String? = null,
    val previousPrevious: String? = null,
    val sentenceStart: Boolean = false,
)

internal data class PredictionProfile(
    val dictionary: Set<String> = emptySet(),
    val learned: Map<String, Int> = emptyMap(),
    val rejected: Map<String, Set<String>> = emptyMap(),
) {
    private val known = (dictionary + learned.keys).map(OfflinePrediction::wordKey).toSet()
    fun keeps(word: String) = word in known
    fun rejects(original: String, candidate: String) = rejected[original]?.contains(candidate) == true
}

internal class OfflinePrediction(lines: Sequence<String>, frequencyLines: Sequence<String> = emptySequence()) {
    private class LanguageData {
        val words = mutableMapOf<String, Int>()
        val authored = mutableSetOf<String>()
        val following = mutableMapOf<String, MutableMap<String, Int>>()
        val afterPair = mutableMapOf<Pair<String, String>, MutableMap<String, Int>>()
        val starts = mutableMapOf<String, Int>()
        val apostrophes = mutableMapOf<String, MutableList<String>>()
        lateinit var alphabetical: List<String>
        lateinit var shortPrefixes: Map<String, List<String>>
        lateinit var lengths: Map<Int, List<String>>

        fun addWord(word: String, count: Int) {
            words[word] = ((words[word] ?: 0).toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        fun index() {
            alphabetical = words.keys.sorted()
            lengths = alphabetical.groupBy(String::length)
            val prefixes = mutableMapOf<String, MutableList<String>>()
            words.keys.sortedWith(compareByDescending<String> { words[it] }.thenBy { it }).forEach { word ->
                for (length in 1..minOf(2, word.length)) {
                    val group = prefixes.getOrPut(word.substring(0, length)) { mutableListOf() }
                    if (group.size < 6) group.add(word)
                }
                if ('\'' in word) apostrophes.getOrPut(word.replace("'", "")) { mutableListOf() }.add(word)
            }
            shortPrefixes = prefixes
        }

        fun prefixWords(prefix: String): Sequence<String> {
            if (prefix.length <= 2) return shortPrefixes[prefix].orEmpty().asSequence()
            val position = alphabetical.binarySearch(prefix)
            val start = if (position >= 0) position else -position - 1
            return (start until alphabetical.size).asSequence().map(alphabetical::get).takeWhile { it.startsWith(prefix) }
        }

        fun weight(word: String) = log2((words[word] ?: 0) + 1.0)
    }

    private data class Related(
        val previous: Map<String, Int> = emptyMap(),
        val pair: Map<String, Int> = emptyMap(),
        val start: Map<String, Int> = emptyMap(),
    ) {
        fun weight(word: String) = log2((previous[word] ?: 0) + 1.0) * 3 +
            log2((pair[word] ?: 0) + 1.0) * 6 +
            log2((start[word] ?: 0) + 1.0) * 2
    }

    private enum class Edit(val prior: Double) {
        Apostrophe(32.0), Transposed(16.0), Repeated(15.0), InsertDelete(12.0),
        Neighbor(11.0), Substitute(7.0), Split(14.0),
    }

    private data class RankedEdit(val word: String, val edit: Edit, val score: Double, val frequency: Int)
    private data class RankedWord(val word: String, val score: Double)

    private val languages = mapOf("en" to LanguageData(), "banglish" to LanguageData(), "bn" to LanguageData())

    init {
        lines.forEach { line ->
            val tab = line.indexOf('\t')
            val data = languages[line.substringBefore('\t')]
            if (tab < 0 || data == null) return@forEach
            val sentence = wordKey(line.substring(tab + 1))
            var previous: MatchResult? = null
            var previousPrevious: MatchResult? = null
            for (match in wordPattern.findAll(sentence)) {
                val word = match.value
                data.addWord(word, 1)
                data.authored.add(word)
                val last = previous
                if (last != null && sentence.substring(last.range.last + 1, match.range.first).all(::inWordGap)) {
                    data.following.getOrPut(last.value, ::mutableMapOf).merge(word, 1, Int::plus)
                    previousPrevious?.let { prior ->
                        data.afterPair.getOrPut(prior.value to last.value, ::mutableMapOf).merge(word, 1, Int::plus)
                    }
                    previousPrevious = last
                } else {
                    data.starts.merge(word, 1, Int::plus)
                    previousPrevious = null
                }
                previous = match
            }
        }
        frequencyLines.forEach { line ->
            val fields = line.split('\t', limit = 3)
            if (fields.size != 3) return@forEach
            val word = dictionaryWord(fields[1]) ?: return@forEach
            val language = languages[fields[0]] ?: return@forEach
            val count = fields[2].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            language.addWord(wordKey(word), count)
        }
        languages.values.forEach(LanguageData::index)
    }

    fun complete(language: TypingLanguage, prefix: String, previous: String?, personal: Set<String>) =
        complete(language, prefix, PredictionContext(previous), PredictionProfile(personal))

    fun complete(language: TypingLanguage, prefix: String, context: PredictionContext, profile: PredictionProfile): List<String> {
        val data = languageData(language)
        return completions(language, wordKey(prefix), data, related(data, context), profile)
            .take(MAX_SUGGESTIONS).map { display(language, it.word, prefix) }
    }

    private fun completions(
        language: TypingLanguage, prefix: String, data: LanguageData, related: Related, profile: PredictionProfile,
    ): List<RankedWord> {
        if (prefix.isEmpty()) return emptyList()
        val ranked = linkedMapOf<String, RankedWord>()
        fun add(word: String, boost: Double, normalized: String = word) {
            if (normalized == prefix || !normalized.startsWith(prefix)) return
            val value = RankedWord(word, 11 + data.weight(normalized) + related.weight(normalized) + boost -
                0.6 * (normalized.length - prefix.length))
            if (value.score > (ranked[normalized]?.score ?: Double.NEGATIVE_INFINITY)) ranked[normalized] = value
        }
        for (word in data.prefixWords(prefix)) add(word, 0.0)
        for (word in related.previous.keys + related.pair.keys + related.start.keys) add(word, 0.0)
        for (word in profile.dictionary) if (isWordFor(language, word)) add(word, 24.0, wordKey(word))
        for ((word, count) in profile.learned) add(word, 15.0 + log2(count + 1.0), wordKey(word))
        return ranked.values.sortedWith(compareByDescending<RankedWord> { it.score }.thenBy { it.word })
    }

    fun next(language: TypingLanguage, previous: String): List<String> = next(language, PredictionContext(previous))

    fun next(language: TypingLanguage, context: PredictionContext, profile: PredictionProfile = PredictionProfile()): List<String> {
        if (context.previous == null) return emptyList()
        val data = languageData(language)
        val related = related(data, context)
        val personal = profile.learned.entries.associate { wordKey(it.key) to it.value }
        return (related.pair.keys + related.previous.keys)
            .sortedWith(compareByDescending<String> { related.pair[it] ?: 0 }
                .thenByDescending { related.previous[it] ?: 0 }.thenByDescending { personal[it] ?: 0 }
                .thenByDescending { data.words[it] ?: 0 }.thenBy { it })
            .take(MAX_SUGGESTIONS).map { display(language, it, "") }
    }

    fun suggestions(
        language: TypingLanguage,
        word: String,
        context: PredictionContext = PredictionContext(),
        profile: PredictionProfile = PredictionProfile(),
    ): List<String> {
        if (word.isEmpty()) return emptyList()
        val data = languageData(language)
        val key = wordKey(word)
        val contextWords = related(data, context)
        val edits = edits(language, key, data, contextWords, profile)
        val correction = if (language == TypingLanguage.English) chooseAutomatic(word, edits, data, contextWords, profile) else null
        val completions = completions(language, key, data, contextWords, profile)
        val preferredCompletion = completions.firstOrNull()?.takeIf {
            if (key.length < 2 || key in data.words || profile.keeps(key)) return@takeIf false
            val candidate = wordKey(it.word)
            profile.keeps(candidate) || (contextWords.pair[candidate] ?: 0) > 0 || (contextWords.previous[candidate] ?: 0) >= 2
        }
        val preferred = (correction?.word ?: preferredCompletion?.word)?.let { display(language, it, word) }
        val corrections = edits.map { RankedWord(it.word, it.score) }
        val order = compareByDescending<RankedWord> { it.score }.thenBy { it.word }
        // Leave space for both an edit and a completion when the strip has no confident leading candidate.
        val alternatives = listOfNotNull(corrections.firstOrNull(), completions.firstOrNull()).sortedWith(order) +
            (corrections + completions).sortedWith(order)
        return (listOfNotNull(preferred, word) + alternatives.map { display(language, it.word, word) })
            .distinct().take(MAX_SUGGESTIONS)
    }

    fun spelling(language: TypingLanguage, word: String, personal: Set<String>): List<String> {
        val data = languageData(language)
        return edits(language, wordKey(word), data, Related(), PredictionProfile(personal))
            .take(MAX_SUGGESTIONS).map { display(language, it.word, word) }
    }

    fun autocorrect(language: TypingLanguage, word: String, personal: Set<String>): String? =
        autocorrect(language, word, PredictionContext(), PredictionProfile(personal))

    fun autocorrect(
        language: TypingLanguage, word: String, context: PredictionContext, profile: PredictionProfile = PredictionProfile(),
    ): String? {
        if (language != TypingLanguage.English) return null
        val data = languageData(language)
        val related = related(data, context)
        return chooseAutomatic(word, edits(language, wordKey(word), data, related, profile), data, related, profile)
            ?.let { display(language, it.word, word) }
    }

    private fun chooseAutomatic(
        word: String, options: List<RankedEdit>, data: LanguageData, related: Related, profile: PredictionProfile,
    ): RankedEdit? {
        val best = options.firstOrNull() ?: return null
        if (word.drop(1).any(Char::isUpperCase) && word.any(Char::isLowerCase)) return null
        val key = wordKey(word)
        if (profile.rejects(key, wordKey(best.word))) return null
        val margin = best.score - (options.getOrNull(1)?.score ?: 0.0)
        val strongContext = (related.pair[best.word] ?: 0) >= 1 || (related.previous[best.word] ?: 0) >= 2
        val literalFrequency = data.words[key] ?: 0
        if (literalFrequency > 0) {
            // Subtitle frequencies also contain unpunctuated dialogue. Require independent context before replacing one.
            val supportsContraction = strongContext || (related.previous[best.word] ?: 0) >= 1 ||
                (related.start[best.word] ?: 0) >= 2
            if (best.edit != Edit.Apostrophe || literalFrequency >= 50_000 || key in data.authored ||
                !supportsContraction || word.none(Char::isLowerCase)) return null
        }
        val confident = when (best.edit) {
            Edit.Apostrophe -> best.frequency >= 2 && margin >= 3
            Edit.Transposed -> key.length >= 3 && best.frequency >= 2 && margin >= 2
            Edit.Repeated -> key.length >= 4 && best.frequency >= 5 && margin >= 2
            Edit.InsertDelete, Edit.Neighbor -> key.length >= 4 && strongContext && margin >= 4
            Edit.Substitute, Edit.Split -> false
        }
        return best.takeIf { confident }
    }

    private fun edits(
        language: TypingLanguage, key: String, data: LanguageData, related: Related, profile: PredictionProfile,
    ): List<RankedEdit> {
        if (key.length !in 2..MAX_EDIT_LENGTH || profile.keeps(key) || language == TypingLanguage.Banglish) {
            return emptyList()
        }
        if (language == TypingLanguage.English && key.any { it !in 'a'..'z' && it != '\'' }) return emptyList()
        val ranked = linkedMapOf<String, RankedEdit>()
        fun add(word: String, edit: Edit, count: Int = data.words[word] ?: 0, personalBoost: Double = 0.0) {
            if (count == 0 && personalBoost == 0.0) return
            val score = edit.prior + log2(count + 1.0) + related.weight(word) + personalBoost
            if (score > (ranked[word]?.score ?: Double.NEGATIVE_INFINITY)) ranked[word] = RankedEdit(word, edit, score, count)
        }
        if (language == TypingLanguage.English) {
            for (word in data.apostrophes[key].orEmpty()) add(word, Edit.Apostrophe)
        }
        if (key in data.words) return ranked.values.sortedByDescending(RankedEdit::score)
        if (language == TypingLanguage.English) {
            if (key.length >= 3) {
                for (index in 0..key.length) {
                    val head = key.substring(0, index)
                    val tail = key.substring(index)
                    for (letter in 'a'..'z') {
                        val repeated = head.lastOrNull() == letter || tail.firstOrNull() == letter
                        add(head + letter + tail, if (repeated) Edit.Repeated else Edit.InsertDelete)
                    }
                    if (tail.isEmpty()) continue
                    val after = tail.substring(1)
                    val original = tail[0]
                    val repeated = head.lastOrNull() == original || after.firstOrNull() == original
                    add(head + after, if (repeated) Edit.Repeated else Edit.InsertDelete)
                    if (tail.length >= 2 && original != tail[1]) {
                        add(head + tail[1] + original + tail.substring(2), Edit.Transposed)
                    }
                    for (letter in 'a'..'z') if (letter != original) {
                        add(head + letter + after, if (nearby(original, letter)) Edit.Neighbor else Edit.Substitute)
                    }
                    if (head.isNotEmpty() && (head.length > 1 || head in setOf("a", "i")) && tail.length > 1) {
                        val splitUses = data.following[head]?.get(tail) ?: 0
                        if (splitUses > 0) add("$head $tail", Edit.Split, splitUses, data.weight(head) * 0.4 + data.weight(tail) * 0.4)
                    }
                }
            }
        } else if (key.length >= 3) {
            for (length in key.length - 1..key.length + 1) {
                for (word in data.lengths[length].orEmpty()) classify(key, word)?.let { add(word, it) }
            }
        }
        if (key.length >= 3) {
            for (word in profile.dictionary) if (isWordFor(language, word)) {
                classify(key, wordKey(word))?.let { add(word, it, personalBoost = 12.0) }
            }
            for ((word, count) in profile.learned) classify(key, wordKey(word))?.let {
                add(word, it, personalBoost = 8.0 + log2(count + 1.0))
            }
        }
        return ranked.values.sortedWith(compareByDescending<RankedEdit> { it.score }.thenBy { it.word })
    }

    private fun related(data: LanguageData, context: PredictionContext): Related {
        val previous = context.previous?.let(::wordKey)
        val prior = context.previousPrevious?.let(::wordKey)
        return Related(
            data.following[previous].orEmpty(),
            if (prior != null && previous != null) data.afterPair[prior to previous].orEmpty() else emptyMap(),
            if (context.sentenceStart) data.starts else emptyMap(),
        )
    }

    private fun languageData(language: TypingLanguage) = languages.getValue(language.predictionTag)

    private fun display(language: TypingLanguage, value: String, source: String): String {
        val cased = if (language == TypingLanguage.English) englishPronoun.replace(value) { "I" } else value
        return matchWordCase(cased, source)
    }

    companion object {
        private const val MAX_SUGGESTIONS = 3
        private const val MAX_EDIT_LENGTH = 24
        private val wordPattern = Regex("[\\p{L}][\\p{L}\\p{M}\\u200c\\u200d]*(?:['’][\\p{L}][\\p{L}\\p{M}\\u200c\\u200d]*)*")
        private val englishPronoun = Regex("(?<![a-z])i(?=[' ]|$)")
        private val openingQuotes = "\"'“‘([{"
        private val closingQuotes = "\"'”’)]}"
        private val terminators = ".!?।…"
        private val networkTokenMarks = "@/\\:_#=+%"
        private val keyPositions = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").flatMapIndexed { y, row ->
            row.mapIndexed { x, key -> key to (y to (x * 4 + y)) }
        }.toMap()

        fun dictionaryWord(raw: String): String? {
            val trimmed = Normalizer.normalize(raw.trim().replace('’', '\''), Normalizer.Form.NFC)
            val normalized = wordKey(trimmed)
            return trimmed.takeIf {
                normalized.length in 1..48 && wordPattern.matches(normalized) &&
                    normalized.all { it in 'a'..'z' || it in '\u0980'..'\u09ff' || it in "'\u200c\u200d" } &&
                    (normalized.none { it in 'a'..'z' } || normalized.none { it in '\u0980'..'\u09ff' })
            }
        }

        fun tailWord(text: String): String? =
            wordPattern.findAll(text).lastOrNull()?.takeIf { it.range.last == text.lastIndex }?.value

        fun previousWord(text: String) = contextBefore(text).previous

        fun contextBefore(before: String): PredictionContext {
            val end = before.indexOfLast { !inWordGap(it) && it !in openingQuotes }
            val gap = before.substring(end + 1)
            val phrase = before.substring(0, end + 1)
            val lastPunctuation = phrase.trimEnd { it in closingQuotes }.lastOrNull()
            if (end < 0 || '\n' in gap || '\r' in gap || lastPunctuation?.let { it in terminators } == true) {
                return PredictionContext(sentenceStart = true)
            }
            if (gap.isEmpty() || protectedTokenPrefix(phrase)) return PredictionContext()
            val words = wordPattern.findAll(phrase).toList()
            val last = words.lastOrNull()?.takeIf { it.range.last == end } ?: return PredictionContext()
            val prior = words.getOrNull(words.lastIndex - 1)?.takeIf {
                before.substring(it.range.last + 1, last.range.first).all(::inWordGap)
            }
            return PredictionContext(last.value, prior?.value)
        }

        fun protectedTokenPrefix(before: String): Boolean {
            val fragment = before.takeLastWhile { !it.isWhitespace() }.trimStart { it in openingQuotes }
            // A trailing period may begin a new sentence. A period inside a token may identify a host.
            return fragment.any { it in networkTokenMarks || it.isDigit() } ||
                fragment.trimEnd('.').contains('.') || fragment.startsWith("www.", ignoreCase = true)
        }

        fun wordKey(word: String): String =
            Normalizer.normalize(word.replace('’', '\''), Normalizer.Form.NFC).lowercase(Locale.ROOT)

        fun isWordFor(language: TypingLanguage, word: String): Boolean {
            val hasBangla = word.any { it in '\u0980'..'\u09ff' }
            return hasBangla == (language.predictionTag == "bn")
        }

        private fun inWordGap(char: Char) = char.isWhitespace() && char != '\n' && char != '\r'

        private fun nearby(a: Char, b: Char): Boolean {
            val (rowA, xA) = keyPositions[a] ?: return false
            val (rowB, xB) = keyPositions[b] ?: return false
            return kotlin.math.abs(rowA - rowB) <= 1 && kotlin.math.abs(xA - xB) <= 4
        }

        private fun classify(a: String, b: String): Edit? {
            if (a == b) return null
            if (a.length == b.length) {
                val first = a.indices.firstOrNull { a[it] != b[it] } ?: return null
                if (a.regionMatches(first + 1, b, first + 1, a.length - first - 1)) {
                    return if (nearby(a[first], b[first])) Edit.Neighbor else Edit.Substitute
                }
                if (first + 1 < a.length && a[first] == b[first + 1] && a[first + 1] == b[first] &&
                    a.regionMatches(first + 2, b, first + 2, a.length - first - 2)) return Edit.Transposed
                return null
            }
            val (short, long) = if (a.length < b.length) a to b else b to a
            if (long.length - short.length != 1) return null
            val first = short.indices.firstOrNull { short[it] != long[it] } ?: short.length
            if (!short.regionMatches(first, long, first + 1, short.length - first)) return null
            if (long[first] == '\'') return Edit.Apostrophe
            return if (short.getOrNull(first - 1) == long[first] || short.getOrNull(first) == long[first]) {
                Edit.Repeated
            } else Edit.InsertDelete
        }
    }
}

internal val TypingLanguage.predictionTag: String
    get() = when (this) {
        TypingLanguage.English -> "en"
        TypingLanguage.Banglish -> "banglish"
        TypingLanguage.Bangla, TypingLanguage.Phonetic -> "bn"
    }
