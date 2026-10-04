package io.homeassistant.companion.android.kiosk.camera

import io.homeassistant.companion.android.common.data.kiosk.KioskStreamCredentials
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The only path this server answers on. */
private const val STREAM_PATH = "/camera"

/** Separates the parts of the multipart response. Arbitrary, but it must not occur in JPEG data. */
private const val BOUNDARY = "hakioskframe"

/** Caps how much of a request is read, so a client cannot make the server buffer without end. */
private const val MAX_REQUEST_LINES = 64
private const val MAX_REQUEST_LINE_LENGTH = 4096

private const val CRLF = "\r\n"

/**
 * Serves the kiosk camera as an MJPEG stream over the local network.
 *
 * Every request must carry HTTP Basic credentials. There is no unauthenticated mode and no
 * "allow when no password is set" path: this endpoint shows a camera inside somebody's home to
 * anything that can reach the device, and a stream that answers an unauthenticated GET is a
 * mistake that cannot be taken back once it is on a wall.
 *
 * Only `GET /camera` is answered. Anything else gets 404, and an unauthenticated request gets 401
 * with a `WWW-Authenticate` challenge, which is what makes a browser and Home Assistant's generic
 * camera both able to connect.
 */
internal class KioskCameraStreamServer(
    private val capture: KioskCameraCapture,
    private val encodeJpeg: (KioskCameraFrame, Int) -> ByteArray? = ::encodeFrameToJpeg,
    private val serverSocketFactory: (Int) -> ServerSocket = { ServerSocket(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Serves the stream until the caller stops collecting.
     *
     * [onPortBound] reports the port actually bound, which differs from [port] only when the
     * caller asks for 0 and lets the system choose, as the tests do.
     */
    suspend fun serve(
        port: Int,
        credentials: KioskStreamCredentials,
        quality: Int = DEFAULT_JPEG_QUALITY,
        onPortBound: (Int) -> Unit = {},
    ): Unit = withContext(ioDispatcher) {
        val serverSocket = runCatching { serverSocketFactory(port) }
            .onFailure { Timber.w(it, "Could not open the kiosk camera stream on port $port") }
            .getOrNull() ?: return@withContext

        onPortBound(serverSocket.localPort)
        Timber.d("Kiosk camera stream listening on port ${serverSocket.localPort}")

        // accept() blocks in a way cancellation cannot interrupt, so closing the socket is what
        // ends the loop. Without this the thread waits for a connection that will never come, and
        // the port stays bound after the stream is switched off.
        val closer = coroutineContext.job.invokeOnCompletion { runCatching { serverSocket.close() } }

        try {
            coroutineScope {
                while (true) {
                    ensureActive()
                    val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                    launch { socket.use { handle(it, credentials, quality) } }
                }
            }
        } finally {
            closer.dispose()
            withContext(NonCancellable) {
                runCatching { serverSocket.close() }
                Timber.d("Kiosk camera stream stopped")
            }
        }
    }

    private suspend fun handle(socket: Socket, credentials: KioskStreamCredentials, quality: Int) {
        val request = runCatching { readRequest(socket) }.getOrNull() ?: return
        val output = BufferedOutputStream(socket.getOutputStream())

        when (decide(request, credentials)) {
            StreamResponse.NOT_FOUND -> output.writeStatus("404 Not Found")
            // The challenge is what makes a browser prompt and what Home Assistant's generic
            // camera expects; without it an unauthenticated client has nothing to retry with.
            StreamResponse.UNAUTHORIZED ->
                output.writeStatus("401 Unauthorized", "WWW-Authenticate: Basic realm=\"Home Assistant kiosk\"")

            StreamResponse.STREAM -> streamTo(output, quality)
        }
    }

    private suspend fun streamTo(output: BufferedOutputStream, quality: Int) {
        output.write(
            (
                "HTTP/1.1 200 OK$CRLF" +
                    "Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY$CRLF" +
                    "Cache-Control: no-store$CRLF" +
                    "Connection: close$CRLF$CRLF"
                ).toByteArray(),
        )
        output.flush()

        try {
            capture.frames().collectJpeg(quality) { jpeg ->
                output.write(
                    (
                        "--$BOUNDARY$CRLF" + "Content-Type: image/jpeg$CRLF" +
                            "Content-Length: ${jpeg.size}$CRLF$CRLF"
                        ).toByteArray(),
                )
                output.write(jpeg)
                output.write(CRLF.toByteArray())
                output.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The reader went away, which is the normal way a stream ends.
            Timber.d(e, "Kiosk camera stream reader disconnected")
        }
    }

    private suspend fun Flow<KioskCameraFrame>.collectJpeg(quality: Int, write: (ByteArray) -> Unit) {
        collect { frame -> encodeJpeg(frame, quality)?.let(write) }
    }

    companion object {
        /** Enough for a dashboard camera card without making every frame expensive to send. */
        const val DEFAULT_JPEG_QUALITY = 70
    }
}

/** What a client asked for: the parts of the request this server acts on. */
internal class StreamRequest(val path: String, val authorization: String?)

/** What the server does about a request. */
internal enum class StreamResponse { STREAM, UNAUTHORIZED, NOT_FOUND }

/**
 * Decides what a request gets, with no I/O involved.
 *
 * The whole security property of this server is here: a request reaches [StreamResponse.STREAM]
 * only by presenting the exact credentials, on the one path that serves anything. There is no
 * branch that answers an unauthenticated request with a picture.
 */
internal fun decide(request: StreamRequest, credentials: KioskStreamCredentials): StreamResponse = when {
    request.path != STREAM_PATH -> StreamResponse.NOT_FOUND
    !credentials.matches(request.authorization) -> StreamResponse.UNAUTHORIZED
    else -> StreamResponse.STREAM
}

/** Reads the request line and headers, stopping at the blank line that ends them. */
private fun readRequest(socket: Socket): StreamRequest? =
    parseRequest(BufferedReader(InputStreamReader(socket.getInputStream())))

/** Parses a request from [reader], which the tests feed from a string. */
internal fun parseRequest(reader: BufferedReader): StreamRequest? {
    val requestLine = reader.readLine()?.take(MAX_REQUEST_LINE_LENGTH) ?: return null
    val parts = requestLine.split(' ')

    // Anything but a GET is given a path nothing serves, so one decision handles every request.
    val path = if (parts.size < 2 || parts[0] != "GET") "" else parts[1].substringBefore('?')

    var authorization: String? = null
    if (path.isNotEmpty()) {
        var line = reader.readLine()?.take(MAX_REQUEST_LINE_LENGTH)
        var read = 0
        while (!line.isNullOrEmpty() && read < MAX_REQUEST_LINES) {
            if (line.startsWith("Authorization:", ignoreCase = true)) {
                authorization = line.substringAfter(':').trim()
            }
            line = reader.readLine()?.take(MAX_REQUEST_LINE_LENGTH)
            read++
        }
    }

    return StreamRequest(path, authorization)
}

private fun BufferedOutputStream.writeStatus(status: String, vararg headers: String) {
    val head = buildString {
        append("HTTP/1.1 $status$CRLF")
        headers.forEach { append(it).append(CRLF) }
        append("Content-Length: 0$CRLF")
        append("Connection: close$CRLF$CRLF")
    }
    write(head.toByteArray())
    flush()
}

/** Compares the offered credentials without leaking how much of them matched. */
internal fun KioskStreamCredentials.matches(authorization: String?): Boolean {
    if (authorization == null) return false
    val expected = "Basic $encoded"
    return MessageDigest.isEqual(expected.toByteArray(), authorization.toByteArray())
}
