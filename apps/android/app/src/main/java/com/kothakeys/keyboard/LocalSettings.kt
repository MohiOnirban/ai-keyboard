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

    var keyboardHeight: KeyboardHeight
        get() = KeyboardHeight.entries.firstOrNull { it.name == get("keyboard_height") } ?: KeyboardHeight.Standard
        set(value) = put("keyboard_height", value.name)

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit().putBoolean("haptics", value).apply() }

    var suggestions: Boolean
        get() = prefs.getBoolean("predictive_suggestions", true)
        set(value) { prefs.edit().putBoolean("predictive_suggestions", value).apply() }

    var nextWord: Boolean
        get() = prefs.getBoolean("next_word_prediction", true)
        set(value) { prefs.edit().putBoolean("next_word_prediction", value).apply() }

    var automaticCapitalization: Boolean
        get() = prefs.getBoolean("automatic_capitalization", false)
        set(value) { prefs.edit().putBoolean("automatic_capitalization", value).apply() }

    var learning: Boolean
        get() = prefs.getBoolean("personalized_learning", false)
        set(value) { prefs.edit().putBoolean("personalized_learning", value).apply() }

    var correction: Boolean
        get() = prefs.getBoolean("correction", true)
        set(value) { prefs.edit().putBoolean("correction", value).apply() }

    val personalDictionary: Set<String>
        get() = prefs.getStringSet("personal_dictionary", emptySet()).orEmpty()

    private var historyPayload: String? = null
    private var history = TypingHistory()
    private var dictionaryOnly = PredictionProfile()
    private val profiles = mutableMapOf<TypingLanguage, PredictionProfile>()

    val hasLearnedTyping: Boolean
        get() = get("learned_typing").isNotEmpty()

    fun predictionProfile(language: TypingLanguage): PredictionProfile {
        val dictionary = personalDictionary
        if (dictionary != dictionaryOnly.dictionary) {
            dictionaryOnly = PredictionProfile(dictionary)
            profiles.clear()
        }
        if (!learning) return dictionaryOnly
        loadLearning()
        return profiles.getOrPut(language) { history.profile(language, dictionary) }
    }

    fun learnWord(language: TypingLanguage, word: String, accepted: Boolean = false) {
        if (!learning) return
        loadLearning()
        if (history.record(language, word, accepted)) saveLearning()
    }

    fun rejectCorrection(language: TypingLanguage, word: String, correction: String) {
        if (!learning) return
        loadLearning()
        if (history.reject(language, word, correction)) saveLearning()
    }

    fun clearLearnedTyping() {
        prefs.edit().remove("learned_typing").apply()
        loadLearning()
    }

    private fun loadLearning() {
        val saved = get("learned_typing")
        if (saved == historyPayload) return
        history = TypingHistory(saved)
        historyPayload = saved
        profiles.clear()
    }

    private fun saveLearning() {
        val saved = history.serialized()
        put("learned_typing", saved)
        historyPayload = saved
        profiles.clear()
    }

    fun saveDictionaryWord(raw: String, replacing: String? = null): Boolean {
        val word = OfflinePrediction.dictionaryWord(raw) ?: return false
        val current = personalDictionary
        val duplicate = current.firstOrNull { it.equals(word, ignoreCase = true) }
        if (duplicate != null && duplicate != replacing) return false
        if (replacing == null && current.size >= 250) return false
        prefs.edit().putStringSet("personal_dictionary", current - setOfNotNull(replacing) + word).apply()
        return true
    }

    fun removeDictionaryWord(word: String) {
        prefs.edit().putStringSet("personal_dictionary", personalDictionary - word).apply()
    }

    fun quickAiConsentFor(origin: String): Boolean =
        origin.isNotEmpty() && get("quick_ai_consent") == credentialPreferenceKey(origin)

    fun allowQuickAi(origin: String) {
        if (origin.isNotEmpty()) put("quick_ai_consent", credentialPreferenceKey(origin))
    }

    fun revokeQuickAi() {
        prefs.edit().remove("quick_ai_consent").apply()
    }

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

    fun clear() {
        prefs.edit().clear().apply()
        loadLearning()
    }

    private fun get(key: String, fallback: String = "") = prefs.getString(key, fallback) ?: fallback
    private fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
