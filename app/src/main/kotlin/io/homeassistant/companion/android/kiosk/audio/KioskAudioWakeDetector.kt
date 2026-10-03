package io.homeassistant.companion.android.kiosk.audio

import io.homeassistant.companion.android.common.data.kiosk.KioskSoundThreshold
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Full scale for 16-bit PCM, used to turn a sample into a 0..1 level. */
private const val PCM_16_BIT_FULL_SCALE = 32768f

/**
 * How long the level has to stay above the threshold before it counts.
 *
 * A door closing or a single clap is louder than speech but far shorter, so requiring the sound to
 * persist keeps the screensaver from waking on every knock. It is a rising-edge debounce rather
 * than a clear delay: re-triggering while somebody keeps talking is harmless, because all it does
 * is reset an idle countdown that is already reset.
 */
private val SUSTAINED_ABOVE_THRESHOLD = 300.milliseconds

/**
 * Turns the microphone into a "somebody is here" signal for the kiosk screensaver.
 *
 * Reads from the shared [VoiceAudioRecorder] rather than opening a recorder of its own, so it costs
 * nothing extra when wake word detection or an Assist pipeline is already listening, and starts and
 * stops the hardware by itself when they are not.
 *
 * Capture lasts only as long as a collector, and the only collector runs while the screen that can
 * show a screensaver is on. That keeps the microphone tied to the foreground, which is also what
 * lets this work without a foreground service on Android 11 and up.
 */
@Singleton
class KioskAudioWakeDetector @Inject constructor(private val voiceAudioRecorder: VoiceAudioRecorder) {

    /**
     * Emits the current input level, between 0 and 1, roughly every 10 milliseconds.
     *
     * Each value is the root mean square of one chunk of samples, which tracks how loud a sound is
     * perceived to be far better than the loudest single sample would.
     */
    fun levels(): Flow<Float> = flow {
        emitAll(voiceAudioRecorder.audioData().map { it.rootMeanSquare() })
    }

    /**
     * Emits once each time the level stays above [threshold] for [SUSTAINED_ABOVE_THRESHOLD].
     *
     * A single emission per crossing, not one per chunk: the caller treats each as somebody being
     * there, and the quiet that has to follow before the next one is what makes that meaningful.
     */
    fun soundDetections(threshold: KioskSoundThreshold): Flow<Unit> = levels()
        .aboveFor(threshold.value, SUSTAINED_ABOVE_THRESHOLD)
        .filter { it }
        .map { }
}

/** Root mean square of these PCM 16-bit samples, as a fraction of full scale. */
private fun ShortArray.rootMeanSquare(): Float {
    if (isEmpty()) return 0f
    var sumOfSquares = 0.0
    for (sample in this) {
        val normalized = sample / PCM_16_BIT_FULL_SCALE
        sumOfSquares += normalized.toDouble() * normalized
    }
    return sqrt(sumOfSquares / size).toFloat()
}

/**
 * Reports whether this flow of levels has stayed above [threshold] for at least [duration],
 * emitting true once per crossing and false when it drops back.
 *
 * Counting chunks rather than reading a clock keeps this testable without one: the recorder emits
 * at a fixed rate, so a count of chunks is a duration.
 */
private fun Flow<Float>.aboveFor(threshold: Float, duration: Duration): Flow<Boolean> = flow {
    val chunksRequired = (duration / CHUNK_DURATION).toInt().coerceAtLeast(1)
    var consecutiveAbove = 0
    var reported = false

    collect { level ->
        if (level > threshold) {
            consecutiveAbove++
            if (consecutiveAbove >= chunksRequired && !reported) {
                reported = true
                emit(true)
            }
        } else {
            consecutiveAbove = 0
            if (reported) {
                reported = false
                emit(false)
            }
        }
    }
}

/** How much audio each emission from [VoiceAudioRecorder] covers. */
private val CHUNK_DURATION = 10.milliseconds
