package com.kothakeys.keyboard

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val settings by lazy { LocalSettings(this) }
    private var activation by mutableStateOf(Activation(false, false))
    private var keyboardSettingsRequests by mutableStateOf(0)
    private var settingsTheme by mutableStateOf("System")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(OPEN_SETTINGS, false)) keyboardSettingsRequests++
        settingsTheme = settings.theme
        enableEdgeToEdge()
        setContent {
            CompanionRoot(
                settings,
                AiClient.get(this),
                activation,
                keyboardSettingsRequests,
                settingsTheme,
                { settingsTheme = it; settings.theme = it },
                { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        settingsTheme = settings.theme
        updateActivation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(OPEN_SETTINGS, false)) keyboardSettingsRequests++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            settingsTheme = settings.theme
            updateActivation()
        }
    }

    private fun updateActivation() {
        val keyboard = ComponentName(this, KothaIme::class.java)
        val manager = getSystemService(InputMethodManager::class.java)
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        activation = Activation(
            manager.enabledInputMethodList.any { ComponentName.unflattenFromString(it.id) == keyboard },
            selected?.let(ComponentName::unflattenFromString) == keyboard
        )
    }

    internal companion object {
        const val OPEN_SETTINGS = "com.kothakeys.keyboard.OPEN_SETTINGS"
    }
}

private data class Activation(val enabled: Boolean, val selected: Boolean)
private enum class Page(val label: String) { Start("Start"), Playground("Playground"), Settings("Settings") }

@Composable
private fun CompanionRoot(
    settings: LocalSettings,
    client: AiClient,
    activation: Activation,
    keyboardSettingsRequests: Int,
    theme: String,
    changeTheme: (String) -> Unit,
    enable: () -> Unit,
    pick: () -> Unit
) {
    var page by remember { mutableStateOf(Page.Start) }
    LaunchedEffect(keyboardSettingsRequests) {
        if (keyboardSettingsRequests > 0) page = Page.Settings
    }
    var usage by remember { mutableStateOf<Usage?>(null) }
    val dark = theme == "Dark" || theme == "System" && androidx.compose.foundation.isSystemInDarkTheme()
    val colors = KeyboardColors.from(dark)
    val scheme = if (dark) darkColorScheme(
        primary = Color(colors.accent), onPrimary = Color(colors.onAccent), background = Color(colors.background),
        onBackground = Color(colors.ink), surface = Color(colors.key), onSurface = Color(colors.ink),
        surfaceVariant = Color(0xff33473d), onSurfaceVariant = Color(0xffd4e5db), outline = Color(0xff8aa79a),
        secondaryContainer = Color(0xff33473d), onSecondaryContainer = Color(0xffedf7f1)
    ) else lightColorScheme(
        primary = Color(colors.accent), onPrimary = Color(colors.onAccent), background = Color(colors.background),
        onBackground = Color(colors.ink), surface = Color(colors.key), onSurface = Color(colors.ink),
        surfaceVariant = Color(0xffe5eee8), onSurfaceVariant = Color(0xff375248), outline = Color(0xff657d70),
        secondaryContainer = Color(0xffd7e9dc), onSecondaryContainer = Color(0xff172822)
    )
    val activity = LocalActivity.current
    val context = LocalContext.current
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Column(
            Modifier.fillMaxSize().background(scheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        context.getString(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground, fontWeight = FontWeight.SemiBold
                    )
                    usage?.let { Text(it.label, color = scheme.primary, style = MaterialTheme.typography.labelMedium) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Page.entries.forEach { destination ->
                        FilterChip(page == destination, { page = destination }, { Text(destination.label) })
                    }
                }
                val scroll = remember(page) { androidx.compose.foundation.ScrollState(0) }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (page) {
                        Page.Start -> StartPage(activation, enable, pick) { page = Page.Playground }
                        Page.Playground -> Playground(settings, client) { usage = it }
                        Page.Settings -> SettingsPage(settings, client, usage, { usage = it }, changeTheme)
                    }
                }
            }
        }
    }
}

@Composable
private fun StartPage(activation: Activation, enable: () -> Unit, pick: () -> Unit, playground: () -> Unit) {
    val appName = stringResource(R.string.app_name)
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("কথা হোক নিজের মতো।", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Text("A Bangladesh-first keyboard for everyday conversations.", style = MaterialTheme.typography.bodyLarge)
            Text("Type in Bangla, Banglish or English. Ask for writing help when you want it.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    KothaSection("Set up your keyboard") {
        Text(when {
            activation.selected -> "$appName is your active keyboard."
            activation.enabled -> "$appName is enabled. Choose it before you start typing."
            else -> "Enable $appName in Android settings, then choose it as your keyboard."
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!activation.enabled) Button(onClick = enable) { Text("Enable keyboard") }
            if (activation.enabled && !activation.selected) Button(onClick = pick) { Text("Choose keyboard") }
            if (activation.selected) OutlinedButton(onClick = pick) { Text("Keyboard picker") }
        }
    }
    KothaSection("Typing practice") {
        PracticeField()
        Text("Hold the spacebar or open the tools menu to choose a typing language. In phonetic mode, type ami bhalo achi.")
    }
    KothaSection("Try the message assistant") {
        Text("You can try a rewrite here using your current keyboard.")
        Button(onClick = playground) { Text("Open playground") }
    }
}

@Composable
private fun PracticeField() {
    val colors = MaterialTheme.colorScheme
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeHeight) {
        if (focused && imeHeight > 0) bringIntoView.bringIntoView()
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView),
        factory = { context ->
            EditText(context).apply {
                hint = "Try typing here"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                gravity = Gravity.TOP or Gravity.START
                setMinLines(2)
                textSize = 17f
                isSaveEnabled = false
                filters = arrayOf(InputFilter.LengthFilter(1600))
                val padding = (12 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
        },
        update = { field ->
            field.setTextColor(colors.onSurface.toArgb())
            field.setHintTextColor(colors.onSurfaceVariant.toArgb())
            fun updateOutline(focused: Boolean) {
                val density = field.resources.displayMetrics.density
                field.background = GradientDrawable().apply {
                    cornerRadius = 7 * density
                    setColor(colors.surface.toArgb())
                    setStroke((density * if (focused) 2 else 1).toInt(), (if (focused) colors.primary else colors.outline).toArgb())
                }
            }
            field.setOnFocusChangeListener { _, hasFocus ->
                focused = hasFocus
                updateOutline(hasFocus)
            }
            updateOutline(field.hasFocus())
        }
    )
}

@Composable
internal fun KothaSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun Choice(label: String, choices: List<String>, selected: String, onChoice: (String) -> Unit) {
    var showing by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { showing = true }) { Text("$label · $selected") }
        DropdownMenu(expanded = showing, onDismissRequest = { showing = false }) {
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    showing = false
                    onChoice(option)
                })
            }
        }
    }
}
