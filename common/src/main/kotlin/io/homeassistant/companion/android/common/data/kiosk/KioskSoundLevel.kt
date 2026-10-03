package io.homeassistant.companion.android.common.data.kiosk

import kotlin.math.log10

/**
 * Quietest level the kiosk's sound meter shows, in dBFS.
 *
 * A tablet microphone in a quiet room sits a little above this, so silence reads as an empty meter
 * rather than as a bar that never quite reaches the bottom.
 */
const val KIOSK_SOUND_FLOOR_DBFS: Float = -60f

/** Loudest level the meter shows: digital full scale, which nothing in a room reaches. */
const val KIOSK_SOUND_CEILING_DBFS: Float = 0f

/**
 * Where [dbfs] sits on the meter, from 0 at [KIOSK_SOUND_FLOOR_DBFS] to 1 at
 * [KIOSK_SOUND_CEILING_DBFS].
 *
 * Decibels are what make the meter usable: amplitude is linear in pressure, so ordinary speech
 * occupies only the bottom tenth of a linear scale and everything above it is a loudness no room
 * produces. On this scale a quiet room sits near the bottom, conversation around the middle and
 * someone speaking up close near the top, which is the spread a level meter is for.
 */
fun dbfsToMeterPosition(dbfs: Float): Float =
    ((dbfs - KIOSK_SOUND_FLOOR_DBFS) / (KIOSK_SOUND_CEILING_DBFS - KIOSK_SOUND_FLOOR_DBFS)).coerceIn(0f, 1f)

/** The inverse of [dbfsToMeterPosition]: the level a point on the meter stands for. */
fun meterPositionToDbfs(position: Float): Float =
    KIOSK_SOUND_FLOOR_DBFS + position.coerceIn(0f, 1f) * (KIOSK_SOUND_CEILING_DBFS - KIOSK_SOUND_FLOOR_DBFS)

/**
 * A microphone input level, in decibels relative to full scale.
 *
 * Carried as a type rather than a bare `Float` because the same number means entirely different
 * things on a linear and a logarithmic scale, and the two are easy to confuse: -40 is a quiet
 * room here, and would be nonsense as an amplitude.
 */
@JvmInline
value class KioskSoundLevel private constructor(val dbfs: Float) {

    /** Where this level sits on the meter, 0..1. */
    val meterPosition: Float get() = dbfsToMeterPosition(dbfs)

    companion object {
        /** Digital silence, and the level anything below the meter's floor is reported as. */
        val SILENCE: KioskSoundLevel = KioskSoundLevel(KIOSK_SOUND_FLOOR_DBFS)

        /** Returns a level clamped into the meter's window. */
        fun ofDbfs(dbfs: Float): KioskSoundLevel =
            KioskSoundLevel(dbfs.coerceIn(KIOSK_SOUND_FLOOR_DBFS, KIOSK_SOUND_CEILING_DBFS))

        /**
         * Returns the level of an amplitude expressed as a fraction of full scale.
         *
         * Digital silence has no logarithm, so it is reported as [SILENCE] rather than as negative
         * infinity.
         */
        fun ofAmplitude(amplitude: Float): KioskSoundLevel =
            if (amplitude <= 0f) SILENCE else ofDbfs(DECIBELS_PER_DECADE * log10(amplitude))
    }
}

/** Amplitude, not power, so a tenfold change is 20 dB. */
private const val DECIBELS_PER_DECADE = 20f
