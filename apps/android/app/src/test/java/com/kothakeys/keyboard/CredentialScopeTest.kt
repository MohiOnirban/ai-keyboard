package com.kothakeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CredentialScopeTest {
    @Test fun equivalentOriginsShareAnInstallationSession() {
        assertEquals(
            credentialPreferenceKey("https://KEYBOARD.example"),
            credentialPreferenceKey("https://keyboard.example:443/api")
        )
        assertEquals(
            credentialPreferenceKey("http://localhost"),
            credentialPreferenceKey("http://localhost:80")
        )
    }

    @Test fun changingSchemeHostOrPortUsesSeparateCredentials() {
        val original = credentialPreferenceKey("https://keyboard.example")
        assertNotEquals(original, credentialPreferenceKey("http://keyboard.example"))
        assertNotEquals(original, credentialPreferenceKey("https://new.keyboard.example"))
        assertNotEquals(original, credentialPreferenceKey("https://keyboard.example:4012"))
        assertNotEquals(credentialPreferenceKey("http://10.0.2.2:4000"), credentialPreferenceKey("http://10.0.2.2:4012"))
    }

    @Test fun legacyAndDisabledCredentialSlotsAreInaccessibleFromConfiguredOrigin() {
        val scoped = credentialPreferenceKey("https://keyboard.example")
        assertNotEquals("credential", scoped)
        assertNotEquals(credentialPreferenceKey(""), scoped)
    }
}
