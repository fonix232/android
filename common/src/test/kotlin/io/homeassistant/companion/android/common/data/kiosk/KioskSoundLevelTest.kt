package io.homeassistant.companion.android.common.data.kiosk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KioskSoundLevelTest {

    @Test
    fun `Given full scale amplitude then the level is the top of the meter`() {
        val level = KioskSoundLevel.ofAmplitude(1f)

        assertEquals(KIOSK_SOUND_CEILING_DBFS, level.dbfs)
        assertEquals(1f, level.meterPosition)
    }

    @Test
    fun `Given digital silence then the level is the floor rather than negative infinity`() {
        val level = KioskSoundLevel.ofAmplitude(0f)

        assertEquals(KIOSK_SOUND_FLOOR_DBFS, level.dbfs)
        assertEquals(0f, level.meterPosition)
    }

    @Test
    fun `Given a negative amplitude then the level is the floor`() {
        assertEquals(KIOSK_SOUND_FLOOR_DBFS, KioskSoundLevel.ofAmplitude(-0.5f).dbfs)
    }

    @Test
    fun `Given an amplitude quieter than the floor then the level is clamped to it`() {
        // -80 dBFS, two thirds of a meter below the floor.
        assertEquals(KIOSK_SOUND_FLOOR_DBFS, KioskSoundLevel.ofAmplitude(0.0001f).dbfs)
    }

    @Test
    fun `Given a tenfold drop in amplitude then the level falls by twenty decibels`() {
        val loud = KioskSoundLevel.ofAmplitude(0.1f).dbfs
        val quiet = KioskSoundLevel.ofAmplitude(0.01f).dbfs

        assertEquals(20f, loud - quiet, 0.01f)
    }

    @Test
    fun `Given the levels a room produces then they spread across the meter`() {
        // The bug this guards: on a linear scale all three of these sat inside the bottom third.
        val quietRoom = KioskSoundLevel.ofAmplitude(0.003f).meterPosition
        val conversation = KioskSoundLevel.ofAmplitude(0.03f).meterPosition
        val loudSpeech = KioskSoundLevel.ofAmplitude(0.2f).meterPosition

        assertTrue(quietRoom < 0.3f, "quiet room should sit low but was $quietRoom")
        assertTrue(conversation in 0.3f..0.7f, "conversation should sit mid-meter but was $conversation")
        assertTrue(loudSpeech > 0.7f, "loud speech should sit high but was $loudSpeech")
    }

    @Test
    fun `Given a meter position then converting it to decibels and back returns it`() {
        for (position in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals(position, dbfsToMeterPosition(meterPositionToDbfs(position)), 0.0001f)
        }
    }

    @Test
    fun `Given a position outside the meter then it is clamped`() {
        assertEquals(KIOSK_SOUND_FLOOR_DBFS, meterPositionToDbfs(-1f))
        assertEquals(KIOSK_SOUND_CEILING_DBFS, meterPositionToDbfs(2f))
    }

    @Test
    fun `Given the default threshold then it sits above a quiet room and below speech at the device`() {
        val quietRoom = KioskSoundLevel.ofAmplitude(0.003f).dbfs
        val speechAtTheDevice = KioskSoundLevel.ofAmplitude(0.03f).dbfs

        assertTrue(
            KioskSoundThreshold.DEFAULT.dbfs > quietRoom,
            "default should not trip on a quiet room at $quietRoom dBFS",
        )
        assertTrue(
            KioskSoundThreshold.DEFAULT.dbfs < speechAtTheDevice,
            "default should trip on speech at $speechAtTheDevice dBFS",
        )
    }
}
