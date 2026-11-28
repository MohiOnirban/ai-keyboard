package com.kothakeys.keyboard

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class AiConnectionTest {
    @Test fun debugOverridesAcceptTlsOriginsOnly() {
        assertEquals("https://keyboard.example", normalizeHttpsOrigin(" HTTPS://KEYBOARD.example:443/ "))
        assertEquals("https://keyboard.example:8443", normalizeHttpsOrigin("https://keyboard.example:8443"))
        for (address in listOf(
            "http://keyboard.example", "http://127.0.0.1:4000", "keyboard.example",
            "https://user:password@keyboard.example", "https://keyboard.example/?token=private",
            "https://keyboard.example/#private", "https://keyboard.example/private", "https://keyboard.example:",
            "https://keyboard.example:65536",
        )) assertNull(normalizeHttpsOrigin(address))
    }

    @Test fun unconfiguredAndEmulatorAddressesAreDistinguishedFromReachableConfigurations() {
        assertEquals(AiFailure.UNCONFIGURED, backendUnavailable("", false))
        assertEquals(AiFailure.EMULATOR_ENDPOINT, backendUnavailable("http://10.0.2.2:4000", false))
        assertNull(backendUnavailable("http://10.0.2.2:4000", true))
        assertNull(backendUnavailable("http://127.0.0.1:4000", false))
        assertNull(backendUnavailable("https://keyboard.example", false))
    }

    @Test fun errorDetailsDoNotCopyNetworkOrResponseContent() {
        for ((error, expected) in listOf(
            UnknownHostException("private") to AiFailure.DNS,
            SSLHandshakeException("private") to AiFailure.TLS,
            ConnectException("private") to AiFailure.CONNECTION,
            SocketTimeoutException("private") to AiFailure.TIMEOUT,
            JSONException("private") to AiFailure.PARSING,
        )) {
            val failure = connectionFailure(error)
            assertEquals(expected, failure.failure)
            assertFalse(failure.message.orEmpty().contains("private", ignoreCase = true))
        }
        assertEquals(AiFailure.HTTP, httpFailure(302, "provider_auth").failure)
        assertEquals(AiFailure.AUTH, httpFailure(401, null).failure)
        assertEquals(AiFailure.QUOTA, httpFailure(429, "quota").failure)
        assertEquals(AiFailure.RATE, httpFailure(429, "private").failure)
        assertEquals(AiFailure.BUDGET, httpFailure(503, "budget").failure)
        assertEquals(AiFailure.PROVIDER_RESTRICTED, httpFailure(503, "provider_restricted").failure)
        assertEquals(AiFailure.PROVIDER_RESPONSE, httpFailure(503, "provider_response").failure)
        assertEquals(AiFailure.UNAVAILABLE, httpFailure(503, "private").failure)
    }
}
