package com.kothakeys.keyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Only the system permission request runs in this activity. Recording always needs a separate keyboard tap. */
class VoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) finish()
        else if (savedInstanceState == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}

internal class AndroidDictation(
    private val context: Context,
    private val changed: () -> Unit,
    private val insert: (String) -> Boolean,
) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeout = Runnable { if (active) fail("Android’s speech service timed out. Try dictating again.") }
    private var recognizer: SpeechRecognizer? = null
    private var revision = 0
    private var waiting = false
    val active get() = recognizer != null
    val mayStop get() = active && !waiting
    var status = ""
        private set
    var partial = ""
        private set

    val permitted get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val onDevice get() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    val available get() = onDevice || SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: String) {
        cancel()
        if (!permitted) return fail("Allow microphone access to dictate.")
        if (!available) return fail("No Android speech service is available.")
        val local = onDevice
        val service = runCatching {
            if (local && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrElse { return fail("Android could not open its speech service.") }
        recognizer = service
        val token = revision
        handler.postDelayed(timeout, 60_000)
        status = "Starting microphone…"
        changed()
        fun current() = revision == token && recognizer === service
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (current()) { status = "Listening…"; changed() }
            }
            override fun onBeginningOfSpeech() {
                if (current()) { status = "Listening…"; changed() }
            }
            override fun onEndOfSpeech() {
                if (current()) { status = "Finishing transcription…"; waiting = true; changed() }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (current()) {
                    partial = best(partialResults).orEmpty()
                    changed()
                }
            }
            override fun onResults(results: Bundle?) {
                if (!current()) return
                val final = best(results)
                cancel()
                status = when {
                    final == null -> "No words were recognized."
                    insert(final) -> "Transcription inserted."
                    else -> "The editor changed. Dictate again at the new cursor."
                }
                changed()
            }
            override fun onError(error: Int) {
                if (current()) fail(when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Android could not access the microphone. Check its permission and privacy switch."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech service could not connect."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This language is unavailable in the selected Android speech service."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Another app is using speech recognition."
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Android’s speech service is busy. Try again later."
                    else -> "Android’s speech service stopped. Try again or switch to system voice input."
                })
            }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "bn") "bn-BD" else "en-US")
        if (Build.VERSION.SDK_INT >= 33) intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
        if (Build.VERSION.SDK_INT >= 34 && language == "mixed") {
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("en-US", "bn-BD"))
        }
        runCatching { service.startListening(intent) }.onFailure { fail("Android could not start listening.") }
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.length in 1..4000 }

    fun stop() {
        val service = recognizer ?: return
        waiting = true
        status = "Finishing transcription…"
        changed()
        runCatching { service.stopListening() }.onFailure { fail("Android could not finish this transcription.") }
    }

    fun cancel() {
        handler.removeCallbacks(timeout)
        revision++
        val previous = recognizer
        recognizer = null
        if (previous != null) {
            runCatching { previous.cancel() }
            previous.destroy()
        }
        waiting = false
        partial = ""
        status = ""
    }

    private fun fail(message: String) {
        cancel()
        status = message
        changed()
    }
}
