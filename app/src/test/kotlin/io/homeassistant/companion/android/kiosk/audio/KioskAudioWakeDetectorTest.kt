package io.homeassistant.companion.android.kiosk.audio

import app.cash.turbine.test
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

/** A chunk of samples whose root mean square is [level] of full scale. */
private fun chunkAt(level: Float): ShortArray {
    val amplitude = (level * 32768f).roundToInt().toShort()
    return ShortArray(160) { amplitude }
}

class KioskAudioWakeDetectorTest {

    private val threshold = KioskSoundThreshold.of(0.2f)

    /**
     * A detector reading a canned recording, given as (level, number of 10 ms chunks) pairs.
     *
     * [VoiceAudioRecorder] is final and owns an [android.media.AudioRecord], so it is mocked rather
     * than faked; the detector only ever calls [VoiceAudioRecorder.audioData].
     */
    private fun detectorFor(vararg levels: Pair<Float, Int>): KioskAudioWakeDetector {
        val chunks = levels.flatMap { (level, count) -> List(count) { chunkAt(level) } }
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
        detectorFor(0.1f to SUSTAINED_CHUNKS * 4).soundDetections(threshold).test {
            awaitComplete()
        }
    }

    @Test
    fun `Given a loud chunk then the reported level reflects it`() = runTest {
        detectorFor(0.5f to 1).levels().test {
            val level = awaitItem()
            assertTrue(level > 0.45f && level < 0.55f, "expected about 0.5 but was $level")
            awaitComplete()
        }
    }

    @Test
    fun `Given silence then the reported level is zero`() = runTest {
        detectorFor(0f to 1).levels().test {
            assertEquals(0f, awaitItem())
            awaitComplete()
        }
    }
}
