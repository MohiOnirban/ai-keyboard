package com.kothakeys.keyboard

internal class TypingHistory(serialized: String = "") {
    private data class Entry(val tag: String, val word: String)
    private data class Observation(val display: String, val count: Int)
    private data class Rejection(val entry: Entry, val suggestion: String)

    private val words = linkedMapOf<Entry, Observation>()
    private val pairs = linkedMapOf<Pair<Entry, String>, Int>()
    private val rejections = linkedSetOf<Rejection>()

    init {
        serialized.lineSequence().forEach { line ->
            val fields = line.split('\t')
            if (fields.size == 5 && fields[0] == "pair" && fields[1] in tags) {
                val count = fields[4].toIntOrNull()?.takeIf { it in 1..255 } ?: return@forEach
                val first = safeWord(fields[2]) ?: return@forEach
                val second = safeWord(fields[3]) ?: return@forEach
                putPair(Entry(fields[1], first) to second, count)
                return@forEach
            }
            if (fields.size != 4 || fields[1] !in tags) return@forEach
            val display = safeWord(fields[2]) ?: return@forEach
            val entry = Entry(fields[1], OfflinePrediction.wordKey(display))
            when (fields[0]) {
                "word" -> fields[3].toIntOrNull()?.takeIf { it in 1..255 }?.let { put(entry, Observation(display, it)) }
                "reject" -> safeCorrection(fields[3])?.let { put(Rejection(entry, OfflinePrediction.wordKey(it))) }
            }
        }
    }

    fun record(language: TypingLanguage, raw: String, accepted: Boolean): Boolean {
        val display = safeWord(raw)?.takeIf { OfflinePrediction.isWordFor(language, it) } ?: return false
        val key = Entry(language.predictionTag, OfflinePrediction.wordKey(display))
        val existing = words[key]
        val count = ((existing?.count ?: 0) + if (accepted) 3 else 1).coerceAtMost(255)
        val spelling = if (accepted || existing == null) display else existing.display
        if (existing == Observation(spelling, count)) return false
        put(key, Observation(spelling, count))
        return true
    }

    fun recordPair(language: TypingLanguage, previous: String, next: String): Boolean {
        val first = safeWord(previous)?.let(OfflinePrediction::wordKey) ?: return false
        val second = safeWord(next) ?: return false
        if (!OfflinePrediction.isWordFor(language, first) || !OfflinePrediction.isWordFor(language, second)) return false
        val key = Entry(language.predictionTag, first) to second
        val count = pairs[key] ?: 0
        if (count == 255) return false
        putPair(key, count + 1)
        return true
    }

    private fun putPair(key: Pair<Entry, String>, count: Int) {
        pairs.remove(key)
        pairs[key] = count
        if (pairs.size > 240) pairs.remove(pairs.keys.first())
    }

    fun next(language: TypingLanguage, previous: String): Map<String, Int> {
        val entry = Entry(language.predictionTag, OfflinePrediction.wordKey(previous))
        return pairs.filter { (key, count) -> key.first == entry && count >= 2 }
            .mapKeys { it.key.second }
    }

    fun reject(language: TypingLanguage, raw: String, correction: String): Boolean {
        val display = safeWord(raw)?.takeIf { OfflinePrediction.isWordFor(language, it) } ?: return false
        val next = safeCorrection(correction)?.takeIf { OfflinePrediction.isWordFor(language, it) } ?: return false
        val original = Entry(language.predictionTag, OfflinePrediction.wordKey(display))
        val rejection = Rejection(original, OfflinePrediction.wordKey(next))
        if (rejection in rejections) return false
        put(rejection)
        return true
    }

    fun profile(language: TypingLanguage, dictionary: Set<String>) = PredictionProfile(
        dictionary = dictionary,
        learned = words.asSequence().filter { (entry, observation) ->
            entry.tag == language.predictionTag && observation.count >= 2
        }.associate { (_, value) -> value.display to value.count },
        rejected = rejections.filter { it.entry.tag == language.predictionTag }
            .groupBy({ it.entry.word }, Rejection::suggestion).mapValues { (_, suggestions) -> suggestions.toSet() },
    )

    fun serialized() = buildString {
        for ((key, count) in pairs) append("pair\t${key.first.tag}\t${key.first.word}\t${key.second}\t$count\n")
        for ((entry, value) in words) append("word\t${entry.tag}\t${value.display}\t${value.count}\n")
        for ((entry, suggestion) in rejections) append("reject\t${entry.tag}\t${entry.word}\t$suggestion\n")
    }

    private fun put(entry: Entry, value: Observation) {
        words.remove(entry)
        words[entry] = value
        if (words.size > 240) words.remove(words.keys.first())
    }

    private fun put(rejection: Rejection) {
        rejections.add(rejection)
        if (rejections.size > 120) rejections.remove(rejections.first())
    }

    private companion object {
        val tags = setOf("en", "bn", "banglish")

        fun safeWord(text: String) = OfflinePrediction.dictionaryWord(text)?.takeIf { it.length in 1..32 }
        fun safeCorrection(text: String): String? {
            val words = text.split(' ')
            if (words.size !in 1..8 || text.length > 80) return null
            return text.takeIf { words.all { safeWord(it) != null } }
        }
    }
}
