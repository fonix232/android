package io.homeassistant.companion.android.kiosk.camera

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** An 8x8 frame: 64 luminance bytes followed by 32 of chroma. */
private const val FRAME_WIDTH = 8
private const val FRAME_HEIGHT = 8
private const val LUMINANCE_SIZE = FRAME_WIDTH * FRAME_HEIGHT

/** Every fourth pixel is compared, so a quarter of the luminance plane. */
private const val COMPARED_PIXELS = LUMINANCE_SIZE / 4

/**
 * A frame whose first [changedPixels] compared pixels sit at [brightness] and the rest at zero.
 *
 * Working in compared pixels rather than raw indices keeps the tests about what they are testing:
 * how much of the frame moved, not where the sampling happens to land.
 */
private fun frame(changedPixels: Int, brightness: Int = 255): KioskCameraFrame {
    val nv21 = ByteArray(LUMINANCE_SIZE + LUMINANCE_SIZE / 2)
    for (pixel in 0 until changedPixels) {
        nv21[pixel * 4] = brightness.toByte()
    }
    return KioskCameraFrame(
        nv21 = nv21,
        width = FRAME_WIDTH,
        height = FRAME_HEIGHT,
        rotationDegrees = 0,
    )
}

class KioskCameraMotionDetectorTest {

    private fun detectorFor(vararg frames: KioskCameraFrame): KioskCameraMotionDetector {
        val capture = mockk<KioskCameraCapture> {
            every { frames() } returns flowOf(*frames)
        }
        return KioskCameraMotionDetector(capture)
    }

    @Test
    fun `Given a still scene then nothing is detected`() = runTest {
        detectorFor(frame(0), frame(0), frame(0)).motionDetections().test {
            awaitComplete()
        }
    }

    @Test
    fun `Given a single frame then nothing is detected`() = runTest {
        // Nothing to compare it against, and reporting movement on the first frame would mean the
        // camera waking the screen every time it starts.
        detectorFor(frame(COMPARED_PIXELS)).motionDetections().test {
            awaitComplete()
        }
    }

    @Test
    fun `Given the scene changes then it is detected once`() = runTest {
        detectorFor(frame(0), frame(COMPARED_PIXELS)).motionDetections().test {
            awaitItem()
            awaitComplete()
        }
    }

    @Test
    fun `Given movement that continues then it is still detected only once`() = runTest {
        // Alternating halves: every frame differs from the one before it.
        detectorFor(
            frame(0),
            frame(COMPARED_PIXELS),
            frame(0),
            frame(COMPARED_PIXELS),
        ).motionDetections().test {
            awaitItem()
            awaitComplete()
        }
    }

    @Test
    fun `Given movement that stops and starts again then it is detected each time`() = runTest {
        detectorFor(
            frame(0),
            frame(COMPARED_PIXELS),
            frame(COMPARED_PIXELS),
            frame(0),
            frame(0),
        ).motionDetections().test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }
    }

    @Test
    fun `Given a change smaller than the required area then nothing is detected`() = runTest {
        // A quarter of the compared pixels, against a detector asking for half.
        detectorFor(frame(0), frame(COMPARED_PIXELS / 4))
            .motionDetections(changedAreaFraction = 0.5f)
            .test {
                awaitComplete()
            }
    }

    @Test
    fun `Given sensor noise rather than movement then nothing is detected`() = runTest {
        // Every compared pixel changes, but by less than the per-pixel threshold.
        detectorFor(frame(0), frame(COMPARED_PIXELS, brightness = 10)).motionDetections().test {
            awaitComplete()
        }
    }

    @Test
    fun `Given a bright scene going dark then it is detected`() = runTest {
        // The comparison is on absolute difference, so losing light counts as much as gaining it.
        detectorFor(frame(COMPARED_PIXELS), frame(0)).motionDetections().test {
            awaitItem()
            awaitComplete()
        }
    }
}
