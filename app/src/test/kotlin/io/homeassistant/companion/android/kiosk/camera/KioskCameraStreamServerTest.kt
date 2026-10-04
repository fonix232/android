package io.homeassistant.companion.android.kiosk.camera

import io.homeassistant.companion.android.common.data.kiosk.KioskStreamCredentials
import java.io.BufferedReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Covers what a request gets and why.
 *
 * The decision is a pure function on purpose: an MJPEG endpoint showing a camera inside somebody's
 * home must not answer a request that carries no credentials, and that property should be provable
 * without a socket.
 */
class KioskCameraStreamServerTest {

    private val credentials = KioskStreamCredentials("s3cret")

    private fun request(raw: String): StreamRequest? = parseRequest(BufferedReader(raw.reader()))

    private fun get(path: String = "/camera", authorization: String? = null): String = buildString {
        append("GET $path HTTP/1.1\r\n")
        append("Host: 10.0.0.5:8080\r\n")
        authorization?.let { append("Authorization: $it\r\n") }
        append("\r\n")
    }

    // Written out rather than encoded here: `java.util.Base64` is one of the android.jar classes
    // stubbed to return null in a unit test, so encoding in the test would prove nothing. The
    // encoder itself is checked against the JDK's in KioskStreamCredentialsTest.
    private val rightCredentials = "Basic a2lvc2s6czNjcmV0" // kiosk:s3cret
    private val wrongUser = "Basic YWRtaW46czNjcmV0" // admin:s3cret
    private val wrongPassword = "Basic a2lvc2s6d3Jvbmc" // kiosk:wrong
    private val truncatedPassword = "Basic a2lvc2s6czNjcmU" // kiosk:s3cre

    private fun decideFor(raw: String): StreamResponse? = request(raw)?.let { decide(it, credentials) }

    @Test
    fun `Given no credentials then the stream is refused`() {
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(get()))
    }

    @Test
    fun `Given a wrong password then the stream is refused`() {
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(get(authorization = wrongPassword)))
    }

    @Test
    fun `Given a password that is a prefix of the right one then the stream is refused`() {
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(get(authorization = truncatedPassword)))
    }

    @Test
    fun `Given a wrong user name then the stream is refused`() {
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(get(authorization = wrongUser)))
    }

    @Test
    fun `Given a bearer token instead of basic credentials then the stream is refused`() {
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(get(authorization = "Bearer s3cret")))
    }

    @Test
    fun `Given an empty authorization header then the stream is refused`() {
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(get(authorization = "")))
    }

    @Test
    fun `Given the right credentials then the stream is served`() {
        assertEquals(StreamResponse.STREAM, decideFor(get(authorization = rightCredentials)))
    }

    @Test
    fun `Given the right credentials on another path then nothing is served`() {
        // One route only: a path that happens to be reachable is a path somebody will find.
        val raw = get(path = "/", authorization = rightCredentials)

        assertEquals(StreamResponse.NOT_FOUND, decideFor(raw))
    }

    @Test
    fun `Given a query string on the stream path then the credentials still decide`() {
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(get(path = "/camera?fps=5")))
        assertEquals(
            StreamResponse.STREAM,
            decideFor(get(path = "/camera?fps=5", authorization = rightCredentials)),
        )
    }

    @Test
    fun `Given a lower case authorization header then it is still read`() {
        val raw = "GET /camera HTTP/1.1\r\nauthorization: $rightCredentials\r\n\r\n"

        assertEquals(StreamResponse.STREAM, decideFor(raw))
    }

    @Test
    fun `Given a method other than GET then nothing is served`() {
        val raw = "POST /camera HTTP/1.1\r\nAuthorization: $rightCredentials\r\n\r\n"

        assertEquals(StreamResponse.NOT_FOUND, decideFor(raw))
    }

    @Test
    fun `Given an empty request then there is nothing to answer`() {
        assertNull(request(""))
    }

    @Test
    fun `Given more headers than the server will read then it still answers the request line`() {
        // The cap is what stops a client making the server buffer without end.
        val raw = buildString {
            append("GET /camera HTTP/1.1\r\n")
            repeat(500) { append("X-Filler-$it: padding\r\n") }
            append("Authorization: $rightCredentials\r\n\r\n")
        }

        // The credentials arrive after the cap, so they are never seen: refused, not served.
        assertEquals(StreamResponse.UNAUTHORIZED, decideFor(raw))
    }
}
