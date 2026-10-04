package io.homeassistant.companion.android.kiosk.audio

import io.homeassistant.companion.android.common.data.kiosk.KioskSoundLevel
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundThreshold
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Full scale for 16-bit PCM, used to turn a sample into a fraction of full scale. */
private const val PCM_16_BIT_FULL_SCALE = 32768f

/**
 * How long the level has to stay above the threshold before it counts.
 *
 * A door closing or a single clap is louder than speech but far shorter, so requiring the sound to
 * persist keeps the screensaver from waking on every knock.
 */
private val SUSTAINED_ABOVE_THRESHOLD = 300.milliseconds

/**
 * How often a sound that stays loud keeps counting as somebody being there.
 *
 * Reporting only the first crossing would let the screensaver appear in the middle of a
 * conversation: the idle countdown is reset once and then runs out while the room is still noisy.
 */
private val SUSTAINED_REPEAT_INTERVAL = 10.seconds

/**
 * Turns the microphone into a "somebody is here" signal for the kiosk screensaver.
 *
 * Reads from the shared [VoiceAudioRecorder] rather than opening a recorder of its own, so it costs
 * nothing extra when wake word detection or an Assist pipeline is already listening, and starts and
 * stops the hardware by itself when they are not. Capture lasts exactly as long as a collector.
 */
@Singleton
class KioskAudioWakeDetector @Inject constructor(private val voiceAudioRecorder: VoiceAudioRecorder) {

    /**
     * Emits the current input level, roughly every 10 milliseconds.
     *
     * Each value is the root mean square of one chunk of samples, which tracks how loud a sound is
     * perceived to be far better than the loudest single sample would, expressed in dBFS because
     * hearing is logarithmic: on a linear amplitude scale every sound a room produces is crowded
     * into the bottom tenth.
     */
    fun levels(): Flow<KioskSoundLevel> = flow {
        emitAll(voiceAudioRecorder.audioData().map { KioskSoundLevel.ofAmplitude(it.rootMeanSquare()) })
    }

    /**
     * Emits once the level has stayed above [threshold] for [SUSTAINED_ABOVE_THRESHOLD], and every
     * [SUSTAINED_REPEAT_INTERVAL] for as long as it stays there.
     *
     * Not one emission per chunk, which would be a hundred a second, and not one per crossing
     * either: a conversation holds the level up for minutes, and a single emission would reset the
     * idle countdown once and let the screensaver appear while people were still talking.
     */
    fun soundDetections(threshold: KioskSoundThreshold): Flow<Unit> = levels()
        .aboveFor(threshold, SUSTAINED_ABOVE_THRESHOLD)
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
private fun Flow<KioskSoundLevel>.aboveFor(threshold: KioskSoundThreshold, duration: Duration): Flow<Boolean> = flow {
    val chunksRequired = (duration / CHUNK_DURATION).toInt().coerceAtLeast(1)
    val chunksPerRepeat = (SUSTAINED_REPEAT_INTERVAL / CHUNK_DURATION).toInt().coerceAtLeast(1)
    var consecutiveAbove = 0
    var reported = false

    collect { level ->
        if (level.dbfs > threshold.dbfs) {
            consecutiveAbove++
            val crossed = consecutiveAbove == chunksRequired
            val stillThere = reported && (consecutiveAbove - chunksRequired) % chunksPerRepeat == 0
            if (crossed || stillThere) {
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
