package io.homeassistant.companion.android.kiosk.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import timber.log.Timber

/**
 * Compresses a captured frame to JPEG.
 *
 * `YuvImage` takes NV21 directly, which is why frames carry that layout: compressing from the
 * camera's own buffer avoids building a `Bitmap` for every frame of a stream.
 *
 * The image is encoded as the sensor produced it. [KioskCameraFrame.rotationDegrees] is carried
 * alongside but not applied, so a device mounted sideways streams a sideways picture; rotating
 * every frame would mean a bitmap round trip per frame.
 *
 * Returns null when the frame cannot be compressed, which is treated as a frame to skip rather
 * than as a reason to drop the connection.
 */
internal fun encodeFrameToJpeg(frame: KioskCameraFrame, quality: Int): ByteArray? = runCatching {
    val image = YuvImage(frame.nv21, ImageFormat.NV21, frame.width, frame.height, null)
    ByteArrayOutputStream().use { out ->
        image.compressToJpeg(Rect(0, 0, frame.width, frame.height), quality, out)
        out.toByteArray()
    }
}.onFailure { Timber.w(it, "Could not compress a kiosk camera frame") }.getOrNull()
