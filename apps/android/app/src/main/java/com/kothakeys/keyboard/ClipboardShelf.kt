package com.kothakeys.keyboard

import org.json.JSONArray
import org.json.JSONObject

internal data class SavedClip(val text: String, val savedAt: Long, val pinned: Boolean = false)

internal class ClipboardShelf(encoded: String, private val now: Long, private val retentionDays: Int) {
    private val clips = mutableListOf<SavedClip>()
    val items: List<SavedClip> get() = clips.sortedWith(compareByDescending<SavedClip> { it.pinned }.thenByDescending { it.savedAt })

    init {
        val array = runCatching { JSONArray(encoded) }.getOrNull()
        if (array != null) for (index in 0 until minOf(array.length(), 24)) {
            val item = array.optJSONObject(index) ?: continue
            val clip = SavedClip(item.optString("text"), item.optLong("saved"), item.optBoolean("pin"))
            if (clip.text.isNotBlank() && clip.text.length <= 1600 && clips.none { it.text == clip.text } &&
                (clip.pinned || now - clip.savedAt in 0 until retentionDays.coerceIn(1, 30) * 86_400_000L)) clips.add(clip)
        }
    }

    fun save(text: String): Boolean {
        if (text.isBlank() || text.length > 1600) return false
        val old = clips.firstOrNull { it.text == text }
        if (old == null && clips.size >= 24) {
            val oldest = clips.filterNot { it.pinned }.minByOrNull { it.savedAt } ?: return false
            clips.remove(oldest)
        }
        clips.remove(old)
        clips.add(SavedClip(text, now, old?.pinned == true))
        return true
    }

    fun pin(text: String) {
        val index = clips.indexOfFirst { it.text == text }
        if (index >= 0) clips[index] = clips[index].copy(pinned = !clips[index].pinned, savedAt = now)
    }

    fun remove(text: String) { clips.removeAll { it.text == text } }
    fun clear() { clips.clear() }
    fun encode(): String = JSONArray(items.map { clip -> JSONObject().put("text", clip.text)
        .put("saved", clip.savedAt).put("pin", clip.pinned) }).toString()
}
