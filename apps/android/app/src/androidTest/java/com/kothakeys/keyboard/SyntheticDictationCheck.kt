package com.kothakeys.keyboard

import android.Manifest
import android.app.Instrumentation
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in check with generated 16 kHz mono PCM. Never prints a provider transcript. */
internal fun syntheticDictationCheck(instrumentation: Instrumentation) {
    val context = instrumentation.targetContext
    val dir = context.getExternalFilesDir("advanced")
    val audio = File(dir, "synthetic-voice.pcm")
    require(audio.length() in 8_000..320_000)
    val report = JSONObject()
    val available = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    report.put("local_service", available)
    if (available) {
        val ready = CountDownLatch(1)
        val descriptor = ParcelFileDescriptor.open(audio, ParcelFileDescriptor.MODE_READ_ONLY)
        val previous = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        var recognizer: SpeechRecognizer? = null
        try {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
            instrumentation.runOnMainSync {
                val local = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer = local
                local.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { report.put("ready", true) }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        report.put("matched_synthetic_phrase", text?.trim()?.trimEnd('.')?.lowercase() == "the parcel arrives tomorrow")
                        ready.countDown()
                    }
                    override fun onError(error: Int) { report.put("android_error_code", error); ready.countDown() }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                local.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT))
            }
            report.put("completed", ready.await(20, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { recognizer?.destroy() }
            descriptor.close()
            // Revoking permission kills the instrumented process; the invoking harness restores the original grant afterward.
            report.put("permission_previously_granted", previous == android.content.pm.PackageManager.PERMISSION_GRANTED)
        }
    }
    File(dir, "synthetic-voice.json").writeText(report.toString(2))
}
