/*
 * Kotlin adapter for the jsAvroPhonetic grammar, distributed under MPL 1.1.
 * Original jsAvroPhonetic by Rifat Nabi. Copyright OmicronLab.
 * See shared/avro/LICENSE.txt and shared/avro/NOTICE.txt.
 */
package com.kothakeys.keyboard

import org.json.JSONArray
import org.json.JSONObject

class Avro(grammar: JSONObject) {
    private data class Check(val prefix: Boolean, val scope: String, val negative: Boolean, val value: String)
    private data class Rule(val checks: List<Check>, val output: String)
    private data class Pattern(val token: String, val output: String, val rules: List<Rule>)

    private val vowels = grammar.getString("vowel")
    private val consonants = grammar.getString("consonant")
    private val caseSensitive = grammar.getString("casesensitive")
    private val byInitial = grammar.getJSONArray("patterns").objects().map { pattern ->
        val rules = pattern.optJSONArray("rules").objects().map { rule ->
            val checks = rule.getJSONArray("matches").objects().map { match ->
                val scope = match.getString("scope")
                Check(match.getString("type") == "prefix", scope.removePrefix("!"), scope.startsWith("!"), match.optString("value", ""))
            }
            Rule(checks, rule.getString("replace"))
        }
        Pattern(pattern.getString("find"), pattern.getString("replace"), rules)
    }.groupBy { it.token.first() }

    private fun JSONArray?.objects() = (0 until (this?.length() ?: 0)).map { this!!.getJSONObject(it) }

    fun render(word: String): String {
        val fixed = word.map { if (it.lowercaseChar() in caseSensitive) it else it.lowercaseChar() }.joinToString("")
        return buildString {
            var pos = 0
            while (pos < fixed.length) {
                val pattern = byInitial[fixed[pos]]?.firstOrNull { fixed.startsWith(it.token, pos) }
                if (pattern == null) {
                    append(fixed[pos++])
                    continue
                }
                val end = pos + pattern.token.length
                val rule = pattern.rules.firstOrNull { it.checks.all { check -> matches(check, fixed, pos, end) } }
                append(rule?.output ?: pattern.output)
                pos = end
            }
        }
    }

    private fun matches(check: Check, text: String, start: Int, end: Int): Boolean {
        val index = if (check.prefix) start - 1 else end
        val c = text.getOrNull(index)?.lowercaseChar()
        val matched = when (check.scope) {
            "consonant" -> c != null && c in consonants
            "vowel" -> c != null && c in vowels
            "punctuation" -> c == null || (c !in consonants && c !in vowels)
            "exact" -> {
                val first = if (check.prefix) start - check.value.length else end
                // Match the upstream parser's strict end boundary.
                first >= 0 && first + check.value.length < text.length && text.startsWith(check.value, first)
            }
            else -> false
        }
        return matched != check.negative
    }
}
