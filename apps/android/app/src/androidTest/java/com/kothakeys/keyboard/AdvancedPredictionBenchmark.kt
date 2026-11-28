package com.kothakeys.keyboard

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.ceil

class AdvancedPredictionBenchmark {
    @Test fun measureOfflineQueriesOnAndroid() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val start = System.nanoTime()
        val model = target.assets.open("predict.tsv").bufferedReader().use { corpus ->
            target.assets.open("wordfreq.tsv").bufferedReader().use { frequencies ->
                OfflinePrediction(corpus.lineSequence(), frequencies.lineSequence())
            }
        }
        val report = JSONObject().put("load_ms", (System.nanoTime() - start) / 1e6)
        val english = TypingLanguage.English
        val before = OfflinePrediction.contextBefore("we can ")
        val words = listOf("remembber", "explain", "bettter", "restraunt", "bringitback", "recieve", "letsgo", "community")
        var used = 0
        fun measure(name: String, run: (String) -> Int) {
            val times = ArrayList<Long>()
            repeat(20) { trial ->
                for (word in words) {
                    val t = System.nanoTime()
                    used += run(word)
                    if (trial >= 5) times.add(System.nanoTime() - t)
                }
            }
            times.sort()
            report.put(name, JSONObject().put("queries", times.size)
                .put("p50_ms", times[times.size / 2] / 1e6)
                .put("p95_ms", times[ceil(times.size * .95).toInt() - 1] / 1e6))
        }
        measure("suggestion") { model.suggestions(english, it, before).size }
        measure("autocorrect") { model.autocorrect(english, it, before)?.length ?: 0 }
        measure("cursor_range_and_suggestions") { word ->
            val text = "please $word tonight"
            val targetWord = TextSnapshot(text, 0, 9, 9).wordAtCursor()!!
            model.suggestions(english, targetWord.range.value, OfflinePrediction.contextBefore(targetWord.before)
                .copy(following = targetWord.following)).size
        }
        val glideStart = System.nanoTime()
        val decoder = GlideDecoder(model.glideVocabulary(english))
        report.put("glide_index_load_ms", (System.nanoTime() - glideStart) / 1e6)
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val centers = rows.flatMapIndexed { i, row -> row.mapIndexed { j, char ->
            char to GlidePoint(j + listOf(0.0, .5, 1.5)[i], i.toDouble())
        } }.toMap()
        measure("glide_decode") { word -> decoder.candidates(word.map(centers::getValue)).size }
        report.put("process_pss_kib", android.os.Debug.getPss())
        assertTrue(used > 0)
        File(target.getExternalFilesDir("advanced"), "prediction.json").writeText(report.toString(2))
    }
}
