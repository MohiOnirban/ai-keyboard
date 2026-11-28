package com.kothakeys.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

/** Coordinates in letter-key pitches. Q is (0,0), A is (0.5,1), Z is (1.5,2). */
internal data class GlidePoint(val x: Double, val y: Double)

internal class GlideDecoder(vocabulary: Map<String, Int>) {
    private data class Template(val word: String, val length: Double, val shape: ByteArray, val prior: Double)
    private data class Match(val word: String, val cost: Double, val error: Double)
    private val groups = HashMap<Pair<Char, Char>, MutableList<Template>>()

    init {
        for ((word, frequency) in vocabulary) {
            if (word.length !in 2..24 || frequency <= 0 || word.any { it !in centers }) continue
            val path = word.map { centers.getValue(it) }
            val length = length(path)
            if (length < 0.75) continue
            val coords = resample(path, length)
            val template = Template(word, length, ByteArray(coords.size) { (coords[it] * 20).roundToInt().toByte() },
                ln(frequency + 1.0).coerceAtMost(16.0))
            groups.getOrPut(word.first() to word.last()) { mutableListOf() }.add(template)
        }
    }

    fun candidates(points: List<GlidePoint>, contextual: List<String> = emptyList()): List<String> {
        if (points.size !in 3..512 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return emptyList()
        val length = length(points)
        if (length !in 0.85..120.0) return emptyList()
        val observed = resample(points, length)
        fun endpoints(p: GlidePoint) = centers.filterValues { distance(it, p) <= 0.98 }.keys
        val preferred = contextual.map(String::lowercase)
        val best = mutableListOf<Match>()
        for (start in endpoints(points.first())) for (end in endpoints(points.last())) {
            for (template in groups[start to end].orEmpty()) {
                val ratio = length / template.length
                if (ratio !in 0.48..2.05) continue
                var squared = 0.0
                for (i in observed.indices) {
                    val delta = observed[i] - (template.shape[i].toInt() and 255) / 20.0
                    squared += delta * delta
                }
                val error = squared / SAMPLES
                if (error > 0.92) continue
                val index = preferred.indexOf(template.word)
                val context = if (index in 0..2) 0.08 - index * 0.02 else 0.0
                val cost = error + 0.24 * abs(ln(ratio)) - 0.012 * template.prior - context
                val insertion = best.indexOfFirst { cost < it.cost || cost == it.cost && template.word < it.word }
                if (insertion >= 0) best.add(insertion, Match(template.word, cost, error))
                else if (best.size < 3) best.add(Match(template.word, cost, error))
                if (best.size > 3) best.removeAt(3)
            }
        }
        val first = best.firstOrNull() ?: return emptyList()
        if (first.error > 0.55 || first.error > 0.32 && best.size > 1 && best[1].cost - first.cost < 0.07) return emptyList()
        return best.map(Match::word)
    }

    private companion object {
        const val SAMPLES = 24
        val centers = buildMap {
            for ((y, row) in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").withIndex()) {
                val inset = (10 - row.length) / 2.0
                for ((x, letter) in row.withIndex()) put(letter, GlidePoint(x + inset, y.toDouble()))
            }
        }
        fun distance(a: GlidePoint, b: GlidePoint) = hypot(a.x - b.x, a.y - b.y)
        fun length(points: List<GlidePoint>): Double {
            var length = 0.0
            for (i in 1 until points.size) length += distance(points[i - 1], points[i])
            return length
        }
        fun resample(points: List<GlidePoint>, total: Double): DoubleArray {
            val result = DoubleArray(SAMPLES * 2)
            var i = 1
            var start = 0.0
            var span = distance(points[0], points[1])
            for (sample in 0 until SAMPLES) {
                val at = sample * total / (SAMPLES - 1)
                while (i < points.lastIndex && start + span < at) {
                    start += span
                    i++
                    span = distance(points[i - 1], points[i])
                }
                val t = if (span == 0.0) 0.0 else ((at - start) / span).coerceIn(0.0, 1.0)
                val a = points[i - 1]
                val b = points[i]
                result[2 * sample] = a.x + t * (b.x - a.x)
                result[2 * sample + 1] = a.y + t * (b.y - a.y)
            }
            return result
        }
    }
}
