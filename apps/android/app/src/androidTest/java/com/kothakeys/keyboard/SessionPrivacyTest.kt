package com.kothakeys.keyboard

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionPrivacyTest {
    @Test fun revokedSessionDoesNotResubmitDraft() {
        assumeTrue(Build.HARDWARE.contains("ranchu") && BuildConfig.BACKEND_URL == "http://10.0.2.2:4000")
        assumeTrue("set kothaMock=true for a local mock server", InstrumentationRegistry.getArguments().getString("kothaMock") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = AiClient.get(context)
        val stored = context.getSharedPreferences("kotha_session", Context.MODE_PRIVATE)
        await<Usage>({ client.usage(it) }).getOrThrow()

        val preferenceKey = credentialPreferenceKey(BuildConfig.BACKEND_URL)
        val sealed = stored.getString(preferenceKey, null)
        assertNotNull("session stores an encrypted credential", sealed)
        val (nonce, bytes) = checkNotNull(sealed).split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keys.getKey("kotha_ai_credential_v1", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            updateAAD(preferenceKey.encodeToByteArray())
        }
        val token = cipher.doFinal(bytes).decodeToString()
        val revoke = URI.create("${BuildConfig.BACKEND_URL}/v1/session").toURL().openConnection() as HttpURLConnection
        try {
            revoke.requestMethod = "DELETE"
            revoke.connectTimeout = 5000
            revoke.readTimeout = 5000
            revoke.setRequestProperty("Authorization", "Bearer $token")
            revoke.setRequestProperty("Content-Type", "application/json")
            revoke.doOutput = true
            revoke.setFixedLengthStreamingMode(2)
            revoke.outputStream.use { it.write("{}".encodeToByteArray()) }
            assertEquals(204, revoke.responseCode)
        } finally {
            revoke.disconnect()
        }

        val result = await<Generation> { callback -> client.generate(
            GenerationRequest("Rewrite", "Synthetic test draft.", "", "balanced", "", "en", JSONObject()), callback
        ) }
        assertEquals(401, (result.exceptionOrNull() as? AiError)?.status)
        assertNull("retry requires a new explicit generation action", stored.getString(preferenceKey, null))
    }

    private fun <T> await(start: ((Result<T>) -> Unit) -> AiTask): Result<T> {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        val task = start {
            result = it
            done.countDown()
        }
        val completed = done.await(12, TimeUnit.SECONDS)
        if (!completed) task.cancel()
        assertTrue("AI request finished", completed)
        return checkNotNull(result)
    }
}
