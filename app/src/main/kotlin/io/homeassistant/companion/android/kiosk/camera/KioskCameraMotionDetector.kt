package io.homeassistant.companion.android.kiosk.camera

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * How different one pixel has to be from the last frame to count as changed, out of 255.
 *
 * Camera sensors are noisy, and a still scene in dim light still jitters by a few levels per pixel
 * between frames. Anything below this is that noise rather than movement.
 */
private const val PIXEL_CHANGE_THRESHOLD = 20

/**
 * Compares every Nth pixel rather than all of them.
 *
 * A tablet's camera produces on the order of a million luminance samples per frame, several times a
 * second, and movement large enough to matter is visible in a sixteenth of them. This keeps the
 * comparison cheap enough to run continuously on a device that is also rendering a dashboard.
 */
private const val PIXEL_STRIDE = 4

/** Fraction of the compared pixels that must change before a frame counts as showing movement. */
const val DEFAULT_MOTION_AREA_FRACTION = 0.02f

/**
 * Turns the camera into a "somebody is here" signal for the kiosk screensaver.
 *
 * Reads the shared [KioskCameraCapture] rather than opening a camera of its own, so it costs nothing
 * extra when the stream or the camera popup is already running, and starts and stops the hardware by
 * itself when they are not.
 *
 * Only the luminance plane is compared. Colour adds nothing to the question being asked -- did
 * anything in the frame move -- and tripling the work to answer it would be a poor trade on a device
 * doing this all day.
 *
 * Nothing here checks the camera permission or whether the user asked for this; callers do.
 */
@Singleton
internal class KioskCameraMotionDetector @Inject constructor(private val capture: KioskCameraCapture) {

    /**
     * Emits once each time the scene starts moving.
     *
     * One emission per episode rather than one per moving frame: the caller treats each as somebody
     * being there, and a frame without movement is what re-arms it. [changedAreaFraction] is how
     * much of the frame has to change, from 0 to 1.
     */
    fun motionDetections(changedAreaFraction: Float = DEFAULT_MOTION_AREA_FRACTION): Flow<Unit> =
        movement(changedAreaFraction)
            .risingEdges()
            .filter { it }
            .map { }

    /** Emits whether each frame differs from the one before it by more than [changedAreaFraction]. */
    private fun movement(changedAreaFraction: Float): Flow<Boolean> = flow {
        var previous: ByteArray? = null

        capture.frames().collect { frame ->
            val current = frame.nv21
            val last = previous

            // The first frame has nothing to compare against, and a frame whose dimensions changed
            // cannot be compared to the last one pixel for pixel.
            if (last != null && last.size == current.size) {
                emit(changedFraction(last, current, frame.luminanceSize) > changedAreaFraction)
            }
            previous = current
        }
    }
}

/**
 * Returns the fraction of the compared pixels that differ between two frames' luminance planes.
 *
 * Both arrays hold a whole NV21 frame; only the first [luminanceSize] bytes are luminance, and the
 * chroma that follows is deliberately ignored.
 */
private fun changedFraction(previous: ByteArray, current: ByteArray, luminanceSize: Int): Float {
    var compared = 0
    var changed = 0
    var index = 0

    while (index < luminanceSize) {
        // Luminance is unsigned, and a signed byte comparison would read 200 as -56.
        val difference = abs((current[index].toInt() and BYTE_MASK) - (previous[index].toInt() and BYTE_MASK))
        if (difference > PIXEL_CHANGE_THRESHOLD) changed++
        compared++
        index += PIXEL_STRIDE
    }

    return if (compared == 0) 0f else changed.toFloat() / compared
}

/** Emits true when this flow first becomes true, and false when it drops back. */
private fun Flow<Boolean>.risingEdges(): Flow<Boolean> = flow {
    var reported = false

    collect { value ->
        if (value != reported) {
            reported = value
            emit(value)
        }
    }
}

private const val BYTE_MASK = 0xFF
