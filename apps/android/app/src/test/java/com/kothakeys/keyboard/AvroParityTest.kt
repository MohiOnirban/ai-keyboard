package com.kothakeys.keyboard

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AvroParityTest {
    @Test fun contextualOutputsMatchBundledUpstreamParser() {
        val shared = generateSequence(File(".").canonicalFile) { it.parentFile }
            .map { File(it, "shared") }.first { File(it, "avro/grammar.json").isFile }
        val grammar = JSONObject(File(shared, "avro/grammar.json").readText())
        val fixture = JSONObject(File(shared, "tests/avro-parity.json").readText())
        val engine = Avro(grammar)
        val patterns = grammar.getJSONArray("patterns")
        val prefixes = fixture.getJSONArray("prefixes").strings()
        val suffixes = fixture.getJSONArray("suffixes").strings()
        val inputs = sortedSetOf<String>()
        for (index in 0 until patterns.length()) {
            val token = patterns.getJSONObject(index).getString("find")
            for (prefix in prefixes) {
                for (suffix in suffixes) inputs.add(prefix + token + suffix)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        for (input in inputs) {
            digest.update((input + "\t" + engine.render(input) + "\n").toByteArray(Charsets.UTF_8))
        }
        assertEquals(fixture.getInt("uniqueInputs"), inputs.size)
        assertEquals(fixture.getString("sha256"), digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
