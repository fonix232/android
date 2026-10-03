package io.homeassistant.companion.android.kiosk.audio

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.kiosk.KIOSK_SOUND_FLOOR_DBFS
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundThreshold
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Number of 10 ms chunks that make up the sustained window the detector requires. */
private const val SUSTAINED_CHUNKS = 30

/** A chunk of samples whose root mean square is [amplitudeFraction] of full scale. */
private fun chunkAt(amplitudeFraction: Float): ShortArray {
    val amplitude = (amplitudeFraction * 32768f).roundToInt().toShort()
    return ShortArray(160) { amplitude }
}

class KioskAudioWakeDetectorTest {

    // -20 dBFS, which is a tenth of full scale in amplitude.
    private val threshold = KioskSoundThreshold.ofDbfs(-20f)

    /**
     * A detector reading a canned recording, given as (level, number of 10 ms chunks) pairs.
     *
     * [VoiceAudioRecorder] is final and owns an [android.media.AudioRecord], so it is mocked rather
     * than faked; the detector only ever calls [VoiceAudioRecorder.audioData].
     */
    private fun detectorFor(vararg levels: Pair<Float, Int>): KioskAudioWakeDetector {
        val chunks = levels.flatMap { (amplitude, count) -> List(count) { chunkAt(amplitude) } }
        val recorder = mockk<VoiceAudioRecorder> {
            coEvery { audioData() } returns flowOf(*chunks.toTypedArray())
        }
        return KioskAudioWakeDetector(recorder)
    }

    @Test
    fun `Given silence then nothing is detected`() = runTest {
        detectorFor(0f to SUSTAINED_CHUNKS * 2).soundDetections(threshold).test {
            awaitComplete()
        }
    }

    @Test
    fun `Given a brief loud sound then nothing is detected`() = runTest {
        // Louder than the threshold, but far shorter than the sustained window a knock would be.
        detectorFor(0.9f to SUSTAINED_CHUNKS / 3, 0f to SUSTAINED_CHUNKS).soundDetections(threshold).test {
            awaitComplete()
        }
    }

    @Test
    fun `Given a sustained loud sound then it is detected once`() = runTest {
        detectorFor(0.9f to SUSTAINED_CHUNKS * 2).soundDetections(threshold).test {
            awaitItem()
            awaitComplete()
        }
    }

    @Test
    fun `Given sound that never stops then it keeps being detected`() = runTest {
        // The bug this guards: a conversation holds the level up for minutes, and reporting only
        // the first crossing let the idle countdown run out while people were still talking.
        detectorFor(0.9f to SUSTAINED_CHUNKS * 120).soundDetections(threshold).test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given sound that stops and starts again then it is detected each time`() = runTest {
        detectorFor(
            0.9f to SUSTAINED_CHUNKS,
            0f to SUSTAINED_CHUNKS,
            0.9f to SUSTAINED_CHUNKS,
        ).soundDetections(threshold).test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }
    }

    @Test
    fun `Given sound below the threshold then nothing is detected however long it lasts`() = runTest {
        // -40 dBFS, half the meter below the threshold.
        detectorFor(0.01f to SUSTAINED_CHUNKS * 4).soundDetections(threshold).test {
            awaitComplete()
        }
    }

    @Test
    fun `Given a chunk at half of full scale then the reported level is about minus 6 dBFS`() = runTest {
        // Half the amplitude is -6.02 dBFS, which is what makes this a logarithmic scale: half as
        // loud is a small step down from the top, not half way to the bottom.
        detectorFor(0.5f to 1).levels().test {
            val dbfs = awaitItem().dbfs
            assertTrue(dbfs > -7f && dbfs < -5f, "expected about -6 dBFS but was $dbfs")
            awaitComplete()
        }
    }

    @Test
    fun `Given a chunk at a tenth of full scale then the reported level is about minus 20 dBFS`() = runTest {
        detectorFor(0.1f to 1).levels().test {
            val dbfs = awaitItem().dbfs
            assertTrue(dbfs > -21f && dbfs < -19f, "expected about -20 dBFS but was $dbfs")
            awaitComplete()
        }
    }

    @Test
    fun `Given speech level audio then it lands in the middle of the meter rather than the bottom`() = runTest {
        // The bug this guards: an amplitude of 0.03 is what ordinary speech produces at a tablet,
        // and on a linear scale it sat in the bottom few percent of the meter.
        detectorFor(0.03f to 1).levels().test {
            val position = awaitItem().meterPosition
            assertTrue(position > 0.3f && position < 0.7f, "expected mid-meter but was $position")
            awaitComplete()
        }
    }

    @Test
    fun `Given digital silence then the reported level is the meter floor`() = runTest {
        detectorFor(0f to 1).levels().test {
            assertEquals(KIOSK_SOUND_FLOOR_DBFS, awaitItem().dbfs)
            awaitComplete()
        }
    }
}
