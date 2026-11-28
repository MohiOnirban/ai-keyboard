package com.kothakeys.keyboard

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
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

internal class AiTask {
    var stage = AiStage.CONFIGURATION
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

internal class AiClient private constructor(private val context: Context) {
    private val connectionSettings = context.getSharedPreferences("kotha_ai_connection", Context.MODE_PRIVATE)
    private val credentialLock = Any()
    private val workers = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val emulator = Build.HARDWARE in setOf("goldfish", "ranchu")

    @Volatile
    private var endpoint = backendOverride() ?: BuildConfig.BACKEND_URL

    fun backendOverride(): String? = if (BuildConfig.DEBUG) {
        connectionSettings.getString("debug_endpoint", null)?.let {
            if (it == USB_BACKEND) it else normalizeHttpsOrigin(it)
        }
    } else null

    fun configuredBackend(): String = endpoint

    fun backendAvailability(): AiFailure? = backendUnavailable(endpoint, emulator)

    fun setBackendOverride(value: String?): Boolean {
        if (!BuildConfig.DEBUG) return false
        val origin = if (value == null) null else normalizeHttpsOrigin(value) ?: return false
        applyBackendOverride(origin)
        return true
    }

    fun useUsbBackend() {
        if (BuildConfig.DEBUG) applyBackendOverride(USB_BACKEND)
    }

    private fun applyBackendOverride(base: String?) {
        connectionSettings.edit().putString("debug_endpoint", base).apply()
        endpoint = base ?: BuildConfig.BACKEND_URL
    }

    fun checkConnection(result: (Result<Unit>) -> Unit) = submit(result) { job, base ->
        val response = request(job, base, "GET", "/health", readTimeout = 6000)
        if (response.opt("status") != "ok") throw AiError(AiFailure.PARSING)
    }

    fun usage(result: (Result<Usage>) -> Unit) = submit(result) { job, base ->
        withSession(job, base, retryUnauthorized = true) { token ->
            request(job, base, "GET", "/v1/usage", token, readTimeout = 6000).toUsage()
        }
    }

    fun generate(input: GenerationRequest, result: (Result<Generation>) -> Unit) = submit(result) { job, base ->
        if (input.text.isBlank() || input.text.length > 1600) {
            throw AiError(AiFailure.INVALID, message = "Enter between 1 and 1,600 characters.")
        }
        val key = UUID.randomUUID().toString()
        withSession(job, base, retryUnauthorized = false) { token ->
            val data = request(job, base, "POST", "/v1/generate", token, input.toJson(), key)
            val suggestion = data.opt("suggestion") as? String ?: throw AiError(AiFailure.PARSING)
            if (suggestion.isBlank() || suggestion.length > 4800) throw AiError(AiFailure.PARSING)
            Generation(suggestion, data.toUsage())
        }
    }

    fun reset(result: (Result<Unit>) -> Unit) = submit(result) { job, base ->
        synchronized(credentialLock) {
            val credential = CredentialStore(context, base)
            credential.read()?.let { token ->
                try {
                    request(job, base, "DELETE", "/v1/session", token, JSONObject(), readTimeout = 6000)
                } catch (error: AiError) {
                    if (error.status != 401) throw error
                }
            }
            credential.clear()
        }
    }

    private fun <T> submit(callback: (Result<T>) -> Unit, work: (AiTask, String) -> T): AiTask {
        val task = AiTask()
        val base = endpoint
        task.setFuture(workers.submit {
            val started = SystemClock.elapsedRealtime()
            val result = runCatching {
                task.checkActive()
                checkEndpoint(base)
                work(task, base)
            }.recoverCatching {
                val failure = connectionFailure(it)
                if (!task.cancelled) {
                    val elapsed = SystemClock.elapsedRealtime() - started
                    Log.w("KothaKeyAI", "stage=${task.stage.name} code=${failure.failure.name} status=${failure.status} duration_ms=$elapsed")
                }
                throw failure
            }
            main.post {
                if (!task.cancelled) {
                    callback(if (endpoint == base) result else Result.failure(AiError(AiFailure.ENDPOINT_CHANGED)))
                }
            }
        })
        return task
    }

    private fun checkEndpoint(base: String) {
        if (base != endpoint) throw AiError(AiFailure.ENDPOINT_CHANGED)
        backendUnavailable(base, emulator)?.let { throw AiError(it) }
    }

    private fun <T> withSession(job: AiTask, base: String, retryUnauthorized: Boolean, action: (String) -> T): T {
        val credential = CredentialStore(context, base)
        val token = ensureSession(job, base, credential)
        return try {
            action(token)
        } catch (error: AiError) {
            if (error.status != 401) throw error
            synchronized(credentialLock) { if (credential.read() == token) credential.clear() }
            if (!retryUnauthorized) throw error
            action(ensureSession(job, base, credential))
        }
    }

    private fun ensureSession(job: AiTask, base: String, credential: CredentialStore): String = synchronized(credentialLock) {
        job.checkActive()
        checkEndpoint(base)
        credential.read() ?: request(job, base, "POST", "/v1/sessions", payload = JSONObject(), readTimeout = 6000)
            .getString("token")
            .also { token ->
                if (!sessionPattern.matches(token)) throw AiError(AiFailure.PARSING)
                credential.write(token)
                job.checkActive()
            }
    }

    private fun request(
        job: AiTask,
        base: String,
        method: String,
        path: String,
        token: String? = null,
        payload: JSONObject? = null,
        idempotencyKey: String? = null,
        readTimeout: Int = 55_000
    ): JSONObject {
        job.checkActive()
        checkEndpoint(base)
        job.stage = AiStage.forPath(path)
        val connection = URI.create(base + path).toURL().openConnection() as HttpURLConnection
        job.attach(connection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 6000
            connection.readTimeout = readTimeout
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
            if (status !in 200..299) throw httpFailure(status, connection.getHeaderField("kotha-error-code"))
            if (status == 204) return JSONObject()
            val response = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                buildString {
                    while (true) {
                        job.checkActive()
                        val length = reader.read(buffer)
                        if (length < 0) break
                        if (this.length + length > 16384) throw AiError(AiFailure.PARSING)
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

    private fun JSONObject.toUsage() = Usage(
        opt("used") as? Int ?: throw AiError(AiFailure.PARSING),
        opt("limit") as? Int ?: throw AiError(AiFailure.PARSING)
    ).also {
        if (it.used < 0 || it.limit < 0) throw AiError(AiFailure.PARSING)
    }

    companion object {
        const val USB_BACKEND = "http://127.0.0.1:4000"
        private val sessionPattern = Regex("[A-Za-z0-9_-]{43}")
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
