package com.kothakeys.keyboard

import android.content.Context
import android.content.res.Configuration
import org.json.JSONObject

internal object Communication {
    val styles = listOf(
        "Flirty", "Romantic", "Funny", "Poetic", "Witty", "Sarcastic", "Confident", "Bold",
        "Subtle", "Mature", "Emotional", "Respectful", "Professional", "Savage", "Comforting",
        "Apologetic", "Culturally inspired", "Custom"
    )
    val defaultFavorites = listOf("Funny", "Romantic", "Witty", "Respectful")
    val modes = listOf("Rewrite", "Reply", "Express")
    val intensities = listOf("subtle", "balanced", "bold")
    val relationships = listOf("", "friend", "crush", "partner", "family", "teacher", "colleague", "stranger")
    val languages = linkedMapOf("auto" to "Automatic", "bn" to "Bangla", "banglish" to "Banglish", "en" to "English")
    val choices = linkedMapOf(
        "length" to LinkOption("Message length", listOf("Automatic", "Short", "Medium", "Detailed")),
        "humor" to LinkOption("Humor", listOf("Automatic", "Rare", "Sometimes", "Often")),
        "formality" to LinkOption("Formality", listOf("Automatic", "Casual", "Neutral", "Formal")),
        "emoji" to LinkOption("Emoji frequency", listOf("Automatic", "Never", "Occasional", "Often"))
    )

    data class LinkOption(val label: String, val choices: List<String>)
}

internal class LocalSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("kotha_preferences", Context.MODE_PRIVATE)

    var typingLanguage: TypingLanguage
        get() = TypingLanguage.entries.firstOrNull { it.name == get("typing") } ?: TypingLanguage.English
        set(value) = put("typing", value.name)

    var theme: String
        get() = get("theme", "System")
        set(value) = put("theme", value)

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit().putBoolean("haptics", value).apply() }

    var correction: Boolean
        get() = prefs.getBoolean("correction", true)
        set(value) { prefs.edit().putBoolean("correction", value).apply() }

    var aiLanguage: String
        get() = get("ai_language", "auto")
        set(value) = put("ai_language", value)

    var favorites: List<String>
        get() = get("favorites", Communication.defaultFavorites.joinToString("|")).split('|')
            .filter { it in Communication.styles }
        set(value) = put("favorites", value.filter { it in Communication.styles }.distinct().joinToString("|"))

    var custom: String
        get() = get("custom")
        set(value) = put("custom", value.take(240))

    fun preference(key: String) = get(key, "Automatic")
    fun setPreference(key: String, value: String) = put(key, value)

    fun requestPreferences() = JSONObject().apply {
        Communication.choices.keys.forEach { key ->
            preference(key).takeUnless { it == "Automatic" }?.let { put(key, it.lowercase()) }
        }
        custom.trim().takeIf(String::isNotEmpty)?.let { put("custom", it) }
    }

    fun darkTheme() = theme == "Dark" || theme == "System" &&
        appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun clear() { prefs.edit().clear().apply() }

    private fun get(key: String, fallback: String = "") = prefs.getString(key, fallback) ?: fallback
    private fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
