package com.kothakeys.keyboard

import java.io.File
import kotlin.math.ceil

fun main(args: Array<String>) {
    val frequency = mutableMapOf<String, Int>()
    File(args[0]).forEachLine { line ->
        val row = line.split('\t')
        if (row[0] == "en") frequency[row[1]] = row[2].toInt()
    }
    val start = System.nanoTime()
    val decoder = GlideDecoder(frequency)
    val cold = System.nanoTime() - start
    val elapsed = mutableListOf<Double>()
    File(args[1]).forEachLine { line ->
        val (word, path) = line.split('\t')
        val points = path.split(' ').map {
            val (x, y) = it.split(',')
            GlidePoint(x.toDouble(), y.toDouble())
        }
        val clock = System.nanoTime()
        val matches = decoder.candidates(points)
        elapsed.add((System.nanoTime() - clock) / 1e6)
        println((listOf(word) + matches).joinToString("\t"))
    }
    elapsed.sort()
    println("TIMING\t${cold / 1e6}\t${elapsed[ceil(elapsed.size * 0.95).toInt() - 1]}")
}
