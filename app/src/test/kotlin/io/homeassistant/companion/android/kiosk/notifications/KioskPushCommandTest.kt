package io.homeassistant.companion.android.kiosk.notifications

import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class KioskPushCommandTest {

    @ParameterizedTest
    @ValueSource(strings = ["kiosk_set_brightness", "KIOSK_SET_BRIGHTNESS", "  kiosk_show_camera  ", "kiosk_future"])
    fun `Given a kiosk message when checking then it is recognised regardless of case or spacing`(message: String) {
        assertTrue(KioskPushCommand.isKioskCommand(message))
    }

    @ParameterizedTest
    @ValueSource(strings = ["command_screen_on", "notify", "", "my_kiosk_command"])
    fun `Given a message that is not a kiosk command then it is not recognised`(message: String) {
        assertFalse(KioskPushCommand.isKioskCommand(message))
    }

    @Test
    fun `Given no message then it is not a kiosk command`() {
        assertFalse(KioskPushCommand.isKioskCommand(null))
    }

    @ParameterizedTest
    @CsvSource("40, 40", "0.4, 40", "1, 100", "100, 100", "0, 0", "250, 100", "-5, 0")
    fun `Given a brightness level as a fraction or a percentage then both mean the same thing`(
        level: String,
        expectedPercent: Int,
    ) {
        val command = KioskPushCommand.from("kiosk_set_brightness", mapOf("level" to level))

        assertEquals(
            KioskBrightness.fromPercent(expectedPercent),
            (command as KioskPushCommand.SetBrightness).brightness,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "bright", "forty"])
    fun `Given an unreadable brightness level then no command is produced`(level: String) {
        assertNull(KioskPushCommand.from("kiosk_set_brightness", mapOf("level" to level)))
    }

    @Test
    fun `Given a brightness command with no level then no command is produced`() {
        assertNull(KioskPushCommand.from("kiosk_set_brightness", emptyMap()))
    }

    @ParameterizedTest
    @CsvSource("clock, CLOCK", "blank, BLANK", "disabled, DISABLED", "  CLOCK  , CLOCK")
    fun `Given a screensaver mode then it is parsed regardless of case or spacing`(
        mode: String,
        expected: KioskScreensaverMode,
    ) {
        val command = KioskPushCommand.from("kiosk_set_screensaver_mode", mapOf("mode" to mode))

        assertEquals(expected, (command as KioskPushCommand.SetScreensaverMode).mode)
    }

    @Test
    fun `Given a screensaver mode that does not exist then no command is produced`() {
        assertNull(KioskPushCommand.from("kiosk_set_screensaver_mode", mapOf("mode" to "lava_lamp")))
    }

    @Test
    fun `Given a show or hide screensaver command then it needs no payload`() {
        assertEquals(KioskPushCommand.ShowScreensaver, KioskPushCommand.from("kiosk_show_screensaver", emptyMap()))
        assertEquals(KioskPushCommand.HideScreensaver, KioskPushCommand.from("kiosk_hide_screensaver", emptyMap()))
    }

    @Test
    fun `Given a kiosk command this app does not serve then no command is produced`() {
        assertNull(KioskPushCommand.from("kiosk_show_camera", emptyMap()))
    }
}
