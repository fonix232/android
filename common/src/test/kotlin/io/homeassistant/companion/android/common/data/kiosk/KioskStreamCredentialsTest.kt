package io.homeassistant.companion.android.common.data.kiosk

import java.security.SecureRandom
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KioskStreamCredentialsTest {

    @Test
    fun `Given a password then it encodes as HTTP Basic expects`() {
        // Decoded with the JDK's own Base64, so this checks the hand-written encoder as well as
        // the shape of the credentials.
        val decoded = String(Base64.getDecoder().decode(KioskStreamCredentials("s3cret").encoded))

        assertEquals("$KIOSK_STREAM_USERNAME:s3cret", decoded)
    }

    @Test
    fun `Given passwords of every length modulo three then the encoding still round-trips`() {
        // Base64 pads in groups of three bytes, and the last group is where an encoder goes wrong.
        for (length in 1..9) {
            val password = "p".repeat(length)
            val decoded = String(Base64.getDecoder().decode(KioskStreamCredentials(password).encoded))

            assertEquals("$KIOSK_STREAM_USERNAME:$password", decoded)
        }
    }

    @Test
    fun `Given two generated credentials then they differ`() {
        assertNotEquals(
            KioskStreamCredentials.generate(SecureRandom()).password,
            KioskStreamCredentials.generate(SecureRandom()).password,
        )
    }

    @Test
    fun `Given a generated password then it is long enough to be worth having`() {
        val password = KioskStreamCredentials.generate().password

        assertTrue(password.length >= 16, "generated password was only ${password.length} characters")
    }

    @Test
    fun `Given a generated password then it needs no escaping in a URL or a header`() {
        val password = KioskStreamCredentials.generate().password

        assertTrue(password.all { it.isLetterOrDigit() || it == '-' || it == '_' }, password)
    }
}
