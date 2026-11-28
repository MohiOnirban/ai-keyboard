package com.kothakeys.keyboard

import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

private data class Case(val kind: String, val language: TypingLanguage, val context: String, val input: String, val expected: String)

fun main(args: Array<String>) {
    val corpus = File(args[0]).readLines()
    val words = args.getOrNull(2)?.let { File(it).readLines() }.orEmpty()
    val start = System.nanoTime()
    val model = OfflinePrediction(corpus.asSequence(), words.asSequence())
    val cold = System.nanoTime() - start
    val cases = File(args[1]).readLines().drop(1).map { line ->
        val row = line.split('\t')
        val language = when (row[1]) {
            "en" -> TypingLanguage.English
            "bn" -> TypingLanguage.Bangla
            else -> TypingLanguage.Banglish
        }
        Case(row[0], language, row[2], row[3], row[4])
    }
    val profiles = cases.associateWith { c -> PredictionProfile(if (c.kind == "personal") setOf(c.expected) else emptySet()) }
    val contexts = cases.associateWith { c -> OfflinePrediction.contextBefore(c.context + " ") }

    fun query(c: Case): List<String> {
        val context = contexts.getValue(c)
        val profile = profiles.getValue(c)
        return when (c.kind) {
            "keep" -> listOf(model.autocorrect(c.language, c.input, context, profile) ?: c.input)
            "next" -> model.next(c.language, context, profile)
            else -> model.suggestions(c.language, c.input, context, profile)
        }
    }

    for (c in cases) {
        val result = query(c)
        val correction = if (c.kind == "spelling") model.autocorrect(c.language, c.input, contexts.getValue(c), profiles.getValue(c)) else null
        println(listOf(c.kind, c.language.name, c.input, c.expected, result.firstOrNull().orEmpty(),
            result.any { it.equals(c.expected, ignoreCase = c.kind != "keep") }, correction.orEmpty()).joinToString("\t"))
    }
    val timing = mutableListOf<Long>()
    var consumed = 0
    repeat(32) { trial ->
        for (c in cases) {
            val elapsed = measureNanoTime { consumed += query(c).size }
            if (trial >= 8) timing.add(elapsed)
        }
    }
    timing.sort()
    println("TIMING\t${cold / 1e6}\t${timing.size}\t${timing[timing.size / 2] / 1e6}\t${timing[ceil(0.95 * timing.size).toInt() - 1] / 1e6}\t$consumed")
}
