package com.kothakeys.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsPage(
    settings: LocalSettings,
    client: AiClient,
    usage: Usage?,
    onUsage: (Usage?) -> Unit,
    onTheme: (String) -> Unit
) {
    var version by remember { mutableIntStateOf(0) }
    var task by remember { mutableStateOf<AiTask?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var endpoint by remember { mutableStateOf(client.configuredBackend()) }
    var endpointDraft by remember { mutableStateOf(client.backendOverride()?.takeIf { it.startsWith("https://") }.orEmpty()) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    val saved = remember(version) { SettingsView(settings) }
    val quickAiAllowed = remember(version, endpoint) { settings.quickAiConsentFor(endpoint) }

    fun refreshEndpoint(message: String) {
        val configured = client.configuredBackend()
        if (endpoint != configured) {
            onUsage(null)
            status = null
        }
        endpoint = configured
        endpointDraft = client.backendOverride()?.takeIf { it.startsWith("https://") }.orEmpty()
        connectionStatus = message
    }

    DisposableEffect(Unit) { onDispose { task?.cancel() } }

    KothaSection("Free daily AI usage") {
        Text(usage?.label ?: "Refresh to check your available generations.")
        OutlinedButton(enabled = task == null, onClick = {
            status = "Checking usage…"
            task = client.usage { result ->
                task = null
                result.onSuccess {
                    onUsage(it)
                    status = null
                }.onFailure { status = it.message }
            }
        }) { Text("Refresh allowance") }
        status?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
    KothaSection("AI connection") {
        val availability = remember(endpoint) { client.backendAvailability() }
        Text(availability?.message ?: "AI service: $endpoint")
        if (BuildConfig.DEBUG) {
            Text("Use an HTTPS address from a KothaKey backend you trust. When you generate, the app sends the displayed text to that service.")
            OutlinedTextField(
                value = endpointDraft,
                onValueChange = { endpointDraft = it.take(512) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HTTPS service address") },
                placeholder = { Text("https://keyboard.example.org") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                enabled = task == null
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = task == null && endpointDraft.isNotBlank(), onClick = {
                    if (client.setBackendOverride(endpointDraft)) {
                        refreshEndpoint("Service address saved.")
                    } else {
                        connectionStatus = "Enter an HTTPS address with a hostname and optional port."
                    }
                }) { Text("Save address") }
                TextButton(enabled = task == null && client.backendOverride() != null, onClick = {
                    client.setBackendOverride(null)
                    refreshEndpoint("Build address restored.")
                }) { Text("Use build address") }
            }
            TextButton(enabled = task == null && endpoint != AiClient.USB_BACKEND, onClick = {
                client.useUsbBackend()
                refreshEndpoint("USB address selected. Connect to a computer running the development service with port 4000 reversed.")
            }) { Text("Use USB development address") }
        }
        Text("Checking the connection sends no text and uses no generations.")
        OutlinedButton(enabled = task == null && availability == null, onClick = {
            connectionStatus = "Checking connection…"
            task = client.checkConnection { result ->
                task = null
                connectionStatus = result.fold(
                    onSuccess = { "AI service is reachable." },
                    onFailure = { it.message }
                )
            }
        }) { Text("Check connection") }
        connectionStatus?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
    KothaSection("Languages and appearance") {
        Choice("Typing", TypingLanguage.entries.map { it.label }, saved.typing.label) { value ->
            settings.typingLanguage = TypingLanguage.entries.first { it.label == value }
            version++
        }
        Choice("AI messages", Communication.languages.values.toList(), Communication.languages.getValue(saved.aiLanguage)) { value ->
            settings.aiLanguage = Communication.languages.entries.first { it.value == value }.key
            version++
        }
        Choice("Appearance", listOf("System", "Light", "Dark"), saved.theme) {
            onTheme(it)
            version++
        }
        Choice("Keyboard height", KeyboardHeight.entries.map { it.label }, saved.height.label) { value ->
            settings.keyboardHeight = KeyboardHeight.entries.first { it.label == value }
            version++
        }
        Toggle("Keyboard haptics", saved.haptics) { settings.haptics = it; version++ }
    }
    KothaSection("Typing assistance") {
        Toggle("Predictive suggestions", saved.suggestions) { settings.suggestions = it; version++ }
        Toggle("English autocorrection", saved.correction) { settings.correction = it; version++ }
        Toggle("Next-word prediction", saved.nextWord) { settings.nextWord = it; version++ }
        Toggle("Automatic English capitalization", saved.capitalization) { settings.automaticCapitalization = it; version++ }
        Toggle("Separate joined words", saved.automaticSpacing) { settings.automaticSpacing = it; version++ }
        Toggle("Space after suggestions", saved.spaceAfterSuggestion) { settings.spaceAfterSuggestion = it; version++ }
        Toggle("Suggest when moving the cursor", saved.cursorSuggestions) { settings.cursorSuggestions = it; version++ }
        Toggle("Double-space period", saved.doubleSpacePeriod) { settings.doubleSpacePeriod = it; version++ }
        Toggle("Glide typing", saved.glideTyping) { settings.glideTyping = it; version++ }
        Toggle("Show glide trail", saved.gestureTrail) { settings.gestureTrail = it; version++ }
        Toggle("Voice typing", saved.voiceTyping) { settings.voiceTyping = it; version++ }
        Toggle("Personalized learning", saved.learning) { settings.learning = it; version++ }
        Text("Press Backspace or tap Undo immediately to restore the original word.")
        Text("When enabled, words and correction choices from supported text fields stay on this device. Private and specialized fields are excluded.")
        OutlinedButton(enabled = saved.hasLearnedTyping, onClick = { confirmation = "learning" }) { Text("Clear learned typing") }
    }
    DictionarySettings(settings)
    KothaSection("Clipboard") {
        Toggle("Keep clips I save", saved.clipboardHistory) { settings.clipboardHistory = it; version++ }
        if (saved.clipboardHistory) {
            Choice("Keep unpinned clips", listOf("1 day", "7 days", "30 days"),
                if (saved.retentionDays == 1) "1 day" else "${saved.retentionDays} days") {
                settings.retentionDays = it.substringBefore(' ').toInt()
                settings.clips()
                version++
            }
            Text("Save items in the keyboard clipboard. Pinned clips stay until you delete them.")
        }
    }
    KothaSection("Favorite styles") {
        Text("Favorites appear first when you open the message assistant.")
        Communication.styles.forEach { style ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = style in saved.favorites, onCheckedChange = {
                    settings.favorites = if (it) saved.favorites + style else saved.favorites - style
                    version++
                })
                Text(style)
            }
        }
    }
    KothaSection("How you like to write") {
        Communication.choices.forEach { (key, option) ->
            Choice(option.label, option.choices, saved.preferences.getValue(key)) {
                settings.setPreference(key, it)
                version++
            }
        }
        OutlinedTextField(
            saved.custom, {
                settings.custom = it
                version++
            }, label = { Text("Custom instructions") }, supportingText = { Text("Optional · up to 240 characters") },
            modifier = Modifier.fillMaxWidth(), minLines = 2
        )
    }
    KothaSection("Privacy and data") {
        Text("Typing and word suggestions work offline. AI sends the displayed text and your writing preferences after you choose a generation action.")
        Text("Quick Grammar and tone actions ask permission before their first request to the configured service.")
        OutlinedButton(enabled = quickAiAllowed, onClick = {
            settings.revokeQuickAi()
            version++
        }) { Text("Reset quick AI consent") }
        Text("The AI service may use an external provider. Its processing and retention depend on the deployment and provider.")
        Text("An anonymous installation session tracks free usage. Drafts and suggestions stay in memory on this device until you apply or copy them.")
        OutlinedButton(onClick = { confirmation = "preferences" }) { Text("Clear local preferences") }
        OutlinedButton(onClick = { confirmation = "session" }, enabled = task == null) { Text("Revoke AI session") }
    }
    KothaSection("Help and troubleshooting") {
        Text("If the keyboard is missing, open Start and check Android’s keyboard settings. More options opens Android’s keyboard picker.")
        Text("AI is unavailable in password, email and other private or specialized fields. Use a normal message field or the Playground.")
        Text("For a rewrite, select the text. On Android 14 or later, you can also place the cursor at the end of a short draft in a compatible editor.")
        Text("If AI cannot connect, you can keep typing and retry later.")
    }

    confirmation?.let { action ->
        val title = when (action) {
            "learning" -> "Clear learned typing?"
            "preferences" -> "Clear local preferences?"
            else -> "Revoke this AI session?"
        }
        val explanation = when (action) {
            "learning" -> "This clears learned words and correction choices on this device."
            "preferences" -> "This resets your keyboard and writing preferences, personal dictionary and learned typing."
            else -> "This revokes this installation’s current AI credential. It requires a connection to the service."
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title) },
            text = { Text(explanation) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (action == "learning") {
                        settings.clearLearnedTyping()
                        version++
                    } else if (action == "preferences") {
                        settings.clear()
                        onTheme("System")
                        version++
                    } else {
                        status = "Revoking session…"
                        task = client.reset { result ->
                            task = null
                            result.onSuccess {
                                onUsage(null)
                                status = "Session revoked."
                            }.onFailure { status = it.message }
                        }
                    }
                }) { Text(if (action == "session") "Revoke" else "Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked, change)
    }
}

private class SettingsView(settings: LocalSettings) {
    val typing = settings.typingLanguage
    val aiLanguage = settings.aiLanguage
    val theme = settings.theme
    val height = settings.keyboardHeight
    val haptics = settings.haptics
    val suggestions = settings.suggestions
    val correction = settings.correction
    val nextWord = settings.nextWord
    val capitalization = settings.automaticCapitalization
    val automaticSpacing = settings.automaticSpacing
    val spaceAfterSuggestion = settings.spaceAfterSuggestion
    val cursorSuggestions = settings.cursorSuggestions
    val doubleSpacePeriod = settings.doubleSpacePeriod
    val glideTyping = settings.glideTyping
    val gestureTrail = settings.gestureTrail
    val voiceTyping = settings.voiceTyping
    val clipboardHistory = settings.clipboardHistory
    val retentionDays = settings.retentionDays
    val learning = settings.learning
    val hasLearnedTyping = settings.hasLearnedTyping
    val favorites = settings.favorites
    val preferences = Communication.choices.keys.associateWith(settings::preference)
    val custom = settings.custom
}
