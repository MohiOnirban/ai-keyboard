package com.kothakeys.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun DictionarySettings(settings: LocalSettings) {
    var open by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var words by remember(open) { mutableStateOf(settings.personalDictionary.sorted()) }

    KothaSection("Personal dictionary") {
        Text("Add names or words you want the keyboard to suggest and keep. Entries are stored on this device.")
        OutlinedButton(onClick = { open = true }) { Text("Manage words") }
    }
    if (!open) return

    val edit = entry
    if (edit != null) {
        AlertDialog(
            onDismissRequest = { entry = null },
            title = { Text(if (original == null) "Add a word" else "Edit word") },
            text = {
                OutlinedTextField(
                    value = edit,
                    onValueChange = { entry = it.take(48); error = false },
                    label = { Text("Word") },
                    supportingText = { Text(if (error) "Enter a new word or remove an entry if the dictionary is full."
                        else "Use one English, Banglish or Bangla word.") },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = edit.isNotBlank(), onClick = {
                    if (settings.saveDictionaryWord(edit, original)) {
                        words = settings.personalDictionary.sorted()
                        entry = null
                    } else error = true
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { entry = null }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Personal dictionary") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                if (words.isEmpty()) Text("Add a word to see it here.")
                words.forEach { word ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(word, Modifier.weight(1f))
                        TextButton(
                            modifier = Modifier.semantics { contentDescription = "Edit $word" },
                            onClick = {
                                original = word
                                entry = word
                                error = false
                            },
                        ) { Text("Edit") }
                        TextButton(
                            modifier = Modifier.semantics { contentDescription = "Remove $word" },
                            onClick = {
                                settings.removeDictionaryWord(word)
                                words = settings.personalDictionary.sorted()
                            },
                        ) { Text("Remove") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = words.size < 250, onClick = { original = null; entry = ""; error = false }) {
                Text("Add word")
            }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Done") } }
    )
}
