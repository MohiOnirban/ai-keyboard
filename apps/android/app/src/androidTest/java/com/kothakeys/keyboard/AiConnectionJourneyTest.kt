package com.kothakeys.keyboard

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class AiConnectionJourneyTest {
    @Test fun debugDeviceReachesMockThroughUsbAndRejectsUntrustedTls() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("kothaConnectionFixtures") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = AiClient.get(context)
        val prior = client.backendOverride()
        try {
            client.useUsbBackend()
            assertEquals(AiClient.USB_BACKEND, client.configuredBackend())
            assertNull(checkConnection(client))

            assertTrue(client.setBackendOverride("https://127.0.0.1:4443"))
            assertEquals(AiFailure.TLS, (checkConnection(client) as AiError).failure)
        } finally {
            if (prior == AiClient.USB_BACKEND) client.useUsbBackend()
            else client.setBackendOverride(prior)
        }
    }

    private fun checkConnection(client: AiClient): Throwable? {
        val result = ArrayBlockingQueue<Result<Unit>>(1)
        val task = client.checkConnection { result.add(it) }
        return try {
            requireNotNull(result.poll(12, TimeUnit.SECONDS)).exceptionOrNull()
        } finally {
            task.cancel()
        }
    }
}
