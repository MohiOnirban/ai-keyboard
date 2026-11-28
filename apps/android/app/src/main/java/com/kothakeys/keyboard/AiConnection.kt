package com.kothakeys.keyboard

import org.json.JSONException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

internal enum class AiStage {
    CONFIGURATION, HEALTH, SESSION, USAGE, GENERATE, REVOKE;

    companion object {
        fun forPath(path: String) = when (path) {
            "/health" -> HEALTH
            "/v1/sessions" -> SESSION
            "/v1/usage" -> USAGE
            "/v1/generate" -> GENERATE
            "/v1/session" -> REVOKE
            else -> CONFIGURATION
        }
    }
}

internal enum class AiFailure(val message: String) {
    UNCONFIGURED("AI has no service address configured for this build."),
    EMULATOR_ENDPOINT("The AI service address is for an Android emulator. Set a trusted HTTPS service address in Settings."),
    ENDPOINT_CHANGED("The AI service address changed. Try again."),
    DNS("The AI service hostname could not be found. Check the address and your network."),
    TLS("The AI service certificate could not be verified. Check the service address and certificate."),
    CONNECTION("The AI service could not be reached. Check your network and the service address."),
    TIMEOUT("The AI service took too long to respond. Try again."),
    HTTP("The AI service returned an unexpected HTTP response. Check the service address."),
    AUTH("Your AI session expired or was denied. Refresh your allowance in Settings."),
    QUOTA("You’ve reached today’s AI allowance. You can keep typing."),
    RATE("Too many AI requests. Wait a moment and try again."),
    BUSY("AI is busy. Try again in a moment."),
    BUDGET("The AI service reached its spending limit. You can keep typing."),
    PROVIDER_AUTH("The AI service could not authenticate with its provider."),
    PROVIDER_QUOTA("The AI provider’s usage allowance has been reached."),
    PROVIDER_RESTRICTED("No AI provider route could accept the request under the service’s restrictions."),
    PROVIDER_TIMEOUT("The AI provider took too long to respond. Try again."),
    PROVIDER_DNS("The AI service could not resolve its provider’s hostname."),
    PROVIDER_TLS("The AI service could not verify its provider’s secure connection."),
    PROVIDER_HTTP("The AI provider could not complete the request. Try again shortly."),
    PROVIDER_RESPONSE("The AI provider returned an unusable response."),
    PARSING("The AI service returned an unusable response."),
    UNAVAILABLE("AI is temporarily unavailable. You can keep typing."),
    INVALID("Check the message and try again."),
    DUPLICATE("This request already reached AI. Try again if you still need a suggestion."),
    REFUSAL("AI couldn’t help with this request. You can try different wording."),
}

internal class AiError(
    val failure: AiFailure,
    val status: Int = 0,
    message: String = failure.message
) : IOException(message)

internal fun normalizeHttpsOrigin(value: String): String? {
    val uri = try {
        URI(value.trim())
    } catch (_: java.net.URISyntaxException) {
        return null
    }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.userInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/") ||
        uri.port == 0 || uri.port > 65535 || uri.rawAuthority.endsWith(':')
    ) return null
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    return "https://${uri.host.lowercase(Locale.ROOT)}$port"
}

internal fun backendUnavailable(base: String, emulator: Boolean): AiFailure? = when {
    base.isEmpty() -> AiFailure.UNCONFIGURED
    !emulator && URI.create(base).host == "10.0.2.2" -> AiFailure.EMULATOR_ENDPOINT
    else -> null
}

internal fun connectionFailure(error: Throwable): AiError = when (error) {
    is AiError -> error
    is UnknownHostException -> AiError(AiFailure.DNS)
    is SSLException -> AiError(AiFailure.TLS)
    is SocketTimeoutException -> AiError(AiFailure.TIMEOUT)
    is ConnectException, is NoRouteToHostException, is IOException -> AiError(AiFailure.CONNECTION)
    is JSONException -> AiError(AiFailure.PARSING)
    else -> AiError(AiFailure.UNAVAILABLE)
}

internal fun httpFailure(status: Int, code: String?): AiError {
    val failure = when (status) {
        400, 413, 415 -> AiFailure.INVALID
        401, 403 -> AiFailure.AUTH
        408, 504 -> AiFailure.TIMEOUT
        409 -> AiFailure.DUPLICATE
        422 -> AiFailure.REFUSAL
        429 -> when (code) {
            "quota" -> AiFailure.QUOTA
            "busy" -> AiFailure.BUSY
            else -> AiFailure.RATE
        }
        503 -> when (code) {
            "budget" -> AiFailure.BUDGET
            "provider_auth" -> AiFailure.PROVIDER_AUTH
            "provider_quota" -> AiFailure.PROVIDER_QUOTA
            "provider_rate" -> AiFailure.RATE
            "provider_restricted" -> AiFailure.PROVIDER_RESTRICTED
            "provider_timeout" -> AiFailure.PROVIDER_TIMEOUT
            "provider_dns" -> AiFailure.PROVIDER_DNS
            "provider_tls" -> AiFailure.PROVIDER_TLS
            "provider_http" -> AiFailure.PROVIDER_HTTP
            "provider_response" -> AiFailure.PROVIDER_RESPONSE
            else -> AiFailure.UNAVAILABLE
        }
        else -> AiFailure.HTTP
    }
    return AiError(failure, status)
}
