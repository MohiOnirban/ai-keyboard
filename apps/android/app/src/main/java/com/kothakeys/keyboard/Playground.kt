package com.kothakeys.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun Playground(settings: LocalSettings, client: AiClient, onUsage: (Usage) -> Unit) {
    var mode by remember { mutableStateOf("Rewrite") }
    var input by remember { mutableStateOf("") }
    val styles = (listOf("Automatic") + settings.favorites + Communication.styles).distinct()
    var style by remember { mutableStateOf(styles.first()) }
    var intensity by remember { mutableStateOf("balanced") }
    var relationship by remember { mutableStateOf("") }
    var task by remember { mutableStateOf<AiTask?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var suggestion by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(Unit) { onDispose { task?.cancel() } }

    fun generate() {
        if (input.isBlank()) {
            status = "Enter a message first."
            return
        }
        suggestion = null
        status = null
        editing = false
        task = client.generate(GenerationRequest(
            mode, input, style.takeUnless { it == "Automatic" }.orEmpty(), intensity,
            relationship, settings.aiLanguage, settings.requestPreferences()
        )) { result ->
            task = null
            result.onSuccess {
                suggestion = it.suggestion
                onUsage(it.usage)
            }.onFailure { status = it.message ?: "AI is temporarily unavailable." }
        }
    }

    KothaSection("Message playground") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Communication.modes.forEach { value ->
                FilterChip(mode == value, {
                    task?.cancel()
                    task = null
                    mode = value
                    suggestion = null
                    status = null
                }, { Text(value) })
            }
        }
        val label = when (mode) {
            "Rewrite" -> "Message to rewrite"
            "Reply" -> "Message you received"
            else -> "Thought or feeling"
        }
        OutlinedTextField(
            input, { if (it.length <= 1600) input = it }, label = { Text(label) }, minLines = 3,
            modifier = Modifier.fillMaxWidth(), supportingText = { Text("${input.length} / 1,600") }
        )
        if (input.isEmpty()) TextButton(onClick = {
            input = when (mode) {
                "Rewrite" -> "অনেক দিন দেখা হয় না। এই শুক্রবার সময় হবে?"
                "Reply" -> "Aj onek pressure gelo. Ektu kotha bolte parba?"
                else -> "বন্ধুকে বলতে চাই, ওর পাশে থাকতে চাই।"
            }
        }) { Text("Use a sample message") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Style", styles, style) { style = it }
            Choice("Intensity", Communication.intensities, intensity) { intensity = it }
            val relationships = listOf("None") + Communication.relationships.filter(String::isNotEmpty)
            Choice("Relationship", relationships, relationship.ifEmpty { "None" }) {
                relationship = it.takeUnless { value -> value == "None" }.orEmpty()
            }
        }
        if (task == null) Button(onClick = ::generate) { Text(if (suggestion == null) "Generate" else "Regenerate") }
        else {
            Text("Generating…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = {
                task?.cancel()
                task = null
                status = "Generation canceled."
            }) { Text("Cancel generation") }
        }
        status?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Text(
            "Generate sends this text and your chosen preferences to the configured AI service.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    val result = suggestion
    if (result != null) KothaSection("Your suggestion") {
        if (editing) OutlinedTextField(
            result, { if (it.length <= 4800) suggestion = it }, label = { Text("Edit suggestion") },
            modifier = Modifier.fillMaxWidth(), minLines = 2
        ) else Text(result, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done editing" else "Edit") }
            TextButton(onClick = {
                val clip = ClipData.newPlainText("AI suggestion", result)
                clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            }, enabled = result.isNotBlank()) { Text("Copy") }
        }
    }
}
