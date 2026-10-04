package io.homeassistant.companion.android.kiosk.camera

import androidx.camera.core.ImageProxy
import io.mockk.every
import io.mockk.mockk
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests the YUV_420_888 to NV21 conversion.
 *
 * The conversion is the one piece of the camera pipeline that can be tested off a device, and it is
 * also the piece most likely to be wrong: plane strides differ per device, and a stride mistake
 * produces a picture that is skewed rather than absent.
 */
class KioskCameraFrameTest {

    @Test
    fun `Given tightly packed planes When converted Then luminance is copied and chroma is interleaved V first`() {
        val image = fakeImage(
            width = 4,
            height = 4,
            luminance = ByteArray(16) { it.toByte() },
            uPlane = byteArrayOf(10, 11, 12, 13),
            vPlane = byteArrayOf(20, 21, 22, 23),
            chromaRowStride = 2,
        )

        val frame = image.toFrame()

        assertArrayEquals(ByteArray(16) { it.toByte() }, frame.nv21.copyOfRange(0, 16))
        assertArrayEquals(
            byteArrayOf(20, 10, 21, 11, 22, 12, 23, 13),
            frame.nv21.copyOfRange(16, 24),
        )
    }

    @Test
    fun `Given padded chroma rows When converted Then the padding is skipped`() {
        // Two chroma samples per row, but each row is four bytes wide; bytes 2 and 3 are padding.
        val image = fakeImage(
            width = 4,
            height = 4,
            luminance = ByteArray(16),
            uPlane = byteArrayOf(10, 11, 0, 0, 12, 13, 0, 0),
            vPlane = byteArrayOf(20, 21, 0, 0, 22, 23, 0, 0),
            chromaRowStride = 4,
        )

        val frame = image.toFrame()

        assertArrayEquals(
            byteArrayOf(20, 10, 21, 11, 22, 12, 23, 13),
            frame.nv21.copyOfRange(16, 24),
        )
    }

    @Test
    fun `Given a padded luminance plane When converted Then rows are copied without their padding`() {
        // Two rows of two pixels, each row padded to four bytes.
        val image = fakeImage(
            width = 2,
            height = 2,
            luminance = byteArrayOf(1, 2, 0, 0, 3, 4, 0, 0),
            luminanceRowStride = 4,
            uPlane = byteArrayOf(10),
            vPlane = byteArrayOf(20),
            chromaRowStride = 1,
        )

        val frame = image.toFrame()

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), frame.nv21.copyOfRange(0, 4))
    }

    @Test
    fun `Given an image When converted Then dimensions rotation and luminance size are carried over`() {
        val image = fakeImage(
            width = 4,
            height = 4,
            luminance = ByteArray(16),
            uPlane = byteArrayOf(10, 11, 12, 13),
            vPlane = byteArrayOf(20, 21, 22, 23),
            chromaRowStride = 2,
            rotationDegrees = 270,
        )

        val frame = image.toFrame()

        assertEquals(4, frame.width)
        assertEquals(4, frame.height)
        assertEquals(270, frame.rotationDegrees)
        assertEquals(16, frame.luminanceSize)
        assertEquals(24, frame.nv21.size)
    }

    private fun fakeImage(
        width: Int,
        height: Int,
        luminance: ByteArray,
        uPlane: ByteArray,
        vPlane: ByteArray,
        chromaRowStride: Int,
        luminanceRowStride: Int = width,
        chromaPixelStride: Int = 1,
        rotationDegrees: Int = 0,
    ): ImageProxy = mockk<ImageProxy>(relaxed = true).also { image ->
        every { image.width } returns width
        every { image.height } returns height
        every { image.imageInfo.rotationDegrees } returns rotationDegrees
        every { image.planes } returns arrayOf(
            fakePlane(luminance, rowStride = luminanceRowStride, pixelStride = 1),
            fakePlane(uPlane, rowStride = chromaRowStride, pixelStride = chromaPixelStride),
            fakePlane(vPlane, rowStride = chromaRowStride, pixelStride = chromaPixelStride),
        )
    }

    private fun fakePlane(bytes: ByteArray, rowStride: Int, pixelStride: Int): ImageProxy.PlaneProxy = mockk<ImageProxy.PlaneProxy>(relaxed = true).also { plane ->
        every { plane.buffer } returns ByteBuffer.wrap(bytes)
        every { plane.rowStride } returns rowStride
        every { plane.pixelStride } returns pixelStride
    }
}
