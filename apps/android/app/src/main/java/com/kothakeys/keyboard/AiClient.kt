package com.kothakeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class Usage(val used: Int, val limit: Int) {
    val label: String get() = "${(limit - used).coerceAtLeast(0)} of $limit free today"
}
internal data class Generation(val suggestion: String, val usage: Usage)

internal data class GenerationRequest(
    val mode: String,
    val text: String,
    val style: String,
    val intensity: String,
    val relationship: String,
    val language: String,
    val preferences: JSONObject
) {
    fun toJson() = JSONObject()
        .put("mode", mode.lowercase(Locale.ROOT))
        .put("text", text.trim())
        .put("intensity", intensity)
        .put("language", language)
        .put("preferences", preferences)
        .apply {
            style.takeIf(String::isNotEmpty)?.let { put("style", it.lowercase(Locale.ROOT)) }
            relationship.takeIf(String::isNotEmpty)?.let { put("relationship", it) }
        }
}

internal class AiError(message: String, val status: Int = 0) : IOException(message)

internal class AiTask {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var future: Future<*>? = null
    @Volatile var cancelled = false
        private set

    fun attach(connection: HttpURLConnection) {
        this.connection = connection
        if (cancelled) {
            connection.disconnect()
            throw InterruptedException()
        }
    }

    fun detach() { connection = null }
    fun setFuture(value: Future<*>) {
        future = value
        if (cancelled) value.cancel(true)
    }

    fun cancel() {
        cancelled = true
        future?.cancel(true)
        connection?.let { active -> disconnects.execute(active::disconnect) }
    }

    companion object {
        private val disconnects = Executors.newCachedThreadPool()
    }

    fun checkActive() {
        if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedException()
    }
}

internal class AiClient private constructor(context: Context) {
    private val credential = CredentialStore(context, BuildConfig.BACKEND_URL)
    private val workers = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun usage(result: (Result<Usage>) -> Unit) = submit(result) { job ->
        withSession(job, retryUnauthorized = true) { token -> request(job, "GET", "/v1/usage", token).toUsage() }
    }

    fun generate(input: GenerationRequest, result: (Result<Generation>) -> Unit) = submit(result) { job ->
        if (input.text.isBlank() || input.text.length > 1600) throw AiError("Enter between 1 and 1,600 characters.")
        val key = UUID.randomUUID().toString()
        withSession(job, retryUnauthorized = false) { token ->
            val data = request(job, "POST", "/v1/generate", token, input.toJson(), key)
            val suggestion = data.getString("suggestion")
            if (suggestion.isBlank() || suggestion.length > 4800) throw AiError("AI returned an unusable suggestion.")
            Generation(suggestion, data.toUsage())
        }
    }

    fun reset(result: (Result<Unit>) -> Unit) = submit(result) { job ->
        synchronized(credential) {
            credential.read()?.let { token ->
                try {
                    request(job, "DELETE", "/v1/session", token, JSONObject())
                } catch (error: AiError) {
                    if (error.status != 401) throw error
                }
            }
            credential.clear()
        }
    }

    private fun <T> submit(callback: (Result<T>) -> Unit, work: (AiTask) -> T): AiTask {
        val task = AiTask()
        task.setFuture(workers.submit {
            val result = runCatching {
                task.checkActive()
                work(task)
            }.recoverCatching {
                if (it is AiError) throw it
                throw AiError("Couldn’t reach AI. Check your connection and try again.")
            }
            main.post { if (!task.cancelled) callback(result) }
        })
        return task
    }

    private fun <T> withSession(job: AiTask, retryUnauthorized: Boolean, action: (String) -> T): T {
        val token = ensureSession(job)
        return try {
            action(token)
        } catch (error: AiError) {
            if (error.status != 401) throw error
            synchronized(credential) { if (credential.read() == token) credential.clear() }
            if (!retryUnauthorized) throw error
            action(ensureSession(job))
        }
    }

    private fun ensureSession(job: AiTask): String = synchronized(credential) {
        job.checkActive()
        credential.read() ?: request(job, "POST", "/v1/sessions", payload = JSONObject())
            .getString("token")
            .also { token ->
                if (token.length !in 20..1024 || token.any(Char::isWhitespace)) throw AiError("Couldn’t set up AI.")
                credential.write(token)
                job.checkActive()
            }
    }

    private fun request(
        job: AiTask,
        method: String,
        path: String,
        token: String? = null,
        payload: JSONObject? = null,
        idempotencyKey: String? = null
    ): JSONObject {
        job.checkActive()
        val base = BuildConfig.BACKEND_URL
        if (base.isEmpty()) throw AiError("AI is unavailable in this build.")
        val connection = URI.create(base + path).toURL().openConnection() as HttpURLConnection
        job.attach(connection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 6000
            connection.readTimeout = 24000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }
            payload?.let {
                val bytes = it.toString().toByteArray(StandardCharsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                job.checkActive()
                connection.outputStream.use { stream -> stream.write(bytes) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw AiError(errorMessage(status), status)
            if (status == 204) return JSONObject()
            val response = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                buildString {
                    while (true) {
                        job.checkActive()
                        val length = reader.read(buffer)
                        if (length < 0) break
                        if (this.length + length > 16384) throw AiError("AI returned an unusable response.")
                        append(buffer, 0, length)
                    }
                }
            }
            return JSONObject(response)
        } finally {
            job.detach()
            connection.disconnect()
        }
    }

    private fun JSONObject.toUsage() = Usage(getInt("used"), getInt("limit")).also {
        if (it.used < 0 || it.limit < 0) throw AiError("AI returned an unusable response.")
    }

    private fun errorMessage(status: Int) = when (status) {
        400 -> "Check the message and try again."
        401 -> "Your AI session expired. Try again."
        409 -> "This request already reached AI. Try again if you still need a suggestion."
        422 -> "AI couldn’t help with this request. You can try different wording."
        429 -> "AI usage is limited right now. Check your allowance in Settings."
        503 -> "AI is temporarily unavailable. You can keep typing."
        else -> "AI couldn’t complete the request. Try again shortly."
    }

    companion object {
        @Volatile private var instance: AiClient? = null

        fun get(context: Context): AiClient = instance ?: synchronized(this) {
            instance ?: AiClient(context.applicationContext).also { instance = it }
        }
    }
}

internal fun credentialPreferenceKey(backendUrl: String): String {
    if (backendUrl.isEmpty()) return "credential_disabled"
    val uri = URI.create(backendUrl)
    val scheme = requireNotNull(uri.scheme).lowercase(Locale.ROOT)
    val host = requireNotNull(uri.host).lowercase(Locale.ROOT)
    val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80
    val origin = "$scheme://$host:$port"
    val digest = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray(StandardCharsets.UTF_8))
    return "credential_" + digest.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
}

private class CredentialStore(context: Context, backendUrl: String) {
    private val prefs = context.getSharedPreferences("kotha_session", Context.MODE_PRIVATE)
    private val preferenceKey = credentialPreferenceKey(backendUrl)
    private val originBinding = preferenceKey.toByteArray(StandardCharsets.UTF_8)

    init {
        // Earlier builds did not record the issuing origin, so their credential cannot be reused safely.
        if (prefs.contains("credential")) prefs.edit().remove("credential").apply()
    }

    fun read(): String? {
        val stored = prefs.getString(preferenceKey, null) ?: return null
        return try {
            val (iv, encrypted) = stored.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.updateAAD(originBinding)
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun write(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(originBinding)
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        val stored = listOf(cipher.iv, encrypted).joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
        check(prefs.edit().putString(preferenceKey, stored).commit())
    }

    fun clear() { check(prefs.edit().remove(preferenceKey).commit()) }

    private fun key(): SecretKey {
        val alias = "kotha_ai_credential_v1"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }
            .generateKey()
    }
}
