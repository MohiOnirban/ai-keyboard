package com.kothakeys.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    val saved = remember(version) { SettingsView(settings) }

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
        Toggle("Keyboard haptics", saved.haptics) { settings.haptics = it; version++ }
        Toggle("English suggestions and autocorrect", saved.correction) { settings.correction = it; version++ }
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
        Text("Typing works offline. Only Generate submits the message shown and your selected writing preferences.")
        Text("The AI service may use an external provider. Its processing and retention depend on the deployment and provider.")
        Text("An anonymous installation session tracks free usage. Drafts and suggestions stay in memory on this device until you apply or copy them.")
        OutlinedButton(onClick = { confirmation = "preferences" }) { Text("Clear local preferences") }
        OutlinedButton(onClick = { confirmation = "session" }, enabled = task == null) { Text("Revoke AI session") }
    }
    KothaSection("Help and troubleshooting") {
        Text("If the keyboard is missing, open Start and check Android’s keyboard settings. The ◎ key opens Android’s keyboard picker.")
        Text("AI is unavailable in password, email and other private or specialized fields. Use a normal message field or the Playground.")
        Text("For a rewrite, select the text. On Android 14 or later, you can also place the cursor at the end of a short draft in a compatible editor.")
        Text("If AI cannot connect, you can keep typing and retry later.")
    }

    confirmation?.let { action ->
        val clearing = action == "preferences"
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (clearing) "Clear local preferences?" else "Revoke this AI session?") },
            text = { Text(if (clearing) "This resets your keyboard and writing preferences." else "This revokes this installation’s current AI credential. It requires a connection to the service.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (clearing) {
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
                }) { Text(if (clearing) "Clear" else "Revoke") }
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
    val haptics = settings.haptics
    val correction = settings.correction
    val favorites = settings.favorites
    val preferences = Communication.choices.keys.associateWith(settings::preference)
    val custom = settings.custom
}
