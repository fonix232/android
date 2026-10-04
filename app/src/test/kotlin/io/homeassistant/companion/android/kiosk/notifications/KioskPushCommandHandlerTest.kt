package io.homeassistant.companion.android.kiosk.notifications

import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.kiosk.FakeKioskSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KioskPushCommandHandlerTest {

    private val repository = FakeKioskSettingsRepository()
    private val handler = KioskPushCommandHandler(repository)

    @Test
    fun `Given a brightness command then it is applied to the stored settings`() = runTest {
        val obeyed = handler.handle(KioskPushCommand.SetBrightness(KioskBrightness.of(0.4f)))

        assertTrue(obeyed)
        assertEquals(KioskBrightness.fromPercent(40), repository.getSettings().brightness)
    }

    @Test
    fun `Given a screensaver mode command then it is applied to the stored settings`() = runTest {
        val obeyed = handler.handle(KioskPushCommand.SetScreensaverMode(KioskScreensaverMode.CLOCK))

        assertTrue(obeyed)
        assertEquals(KioskScreensaverMode.CLOCK, repository.getSettings().screensaverMode)
    }

    @Test
    fun `Given the user refuses remote commands then nothing is changed`() = runTest {
        repository.setSettings(KioskSettings(acceptRemoteCommands = false))

        val obeyed = handler.handle(KioskPushCommand.SetBrightness(KioskBrightness.of(0.4f)))

        assertFalse(obeyed)
        assertNull(repository.getSettings().brightness)
    }

    @Test
    fun `Given a command is obeyed then the other settings are left alone`() = runTest {
        repository.setSettings(KioskSettings(enabled = true, hideStatusBar = true))

        handler.handle(KioskPushCommand.SetScreensaverMode(KioskScreensaverMode.BLANK))

        val stored = repository.getSettings()
        assertTrue(stored.enabled)
        assertTrue(stored.hideStatusBar)
    }

    @Test
    fun `Given confirmations are silenced then the handler reports that`() = runTest {
        repository.setSettings(KioskSettings(showRemoteCommandConfirmations = false))

        assertFalse(handler.shouldConfirm())
    }
}
