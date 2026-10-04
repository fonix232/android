package io.homeassistant.companion.android.kiosk.camera

/**
 * One frame from the kiosk camera, as NV21 bytes.
 *
 * NV21 because it serves both readers without a second copy: its first `width * height` bytes are
 * the luminance plane, which is all motion detection looks at, and the whole buffer is what
 * `android.graphics.YuvImage` compresses to the JPEG the stream sends.
 *
 * Not a data class: [nv21] is an array, and the generated `equals` would compare it by identity,
 * which reads as a bug the first time someone relies on it.
 */
internal class KioskCameraFrame(val nv21: ByteArray, val width: Int, val height: Int, val rotationDegrees: Int) {
    /** Number of bytes at the start of [nv21] that make up the luminance plane. */
    val luminanceSize: Int get() = width * height
}
