package io.homeassistant.companion.android.kiosk.notifications

import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreenController
import io.homeassistant.companion.android.common.data.kiosk.KioskScreenRequest
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
    private val controller = KioskScreenController()
    private val handler = KioskPushCommandHandler(repository, controller)

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
    fun `Given a show screensaver command then the request reaches the screen and nothing is stored`() = runTest {
        turbineScope {
            val requests = controller.requests.testIn(backgroundScope)

            val obeyed = handler.handle(KioskPushCommand.ShowScreensaver)

            assertTrue(obeyed)
            assertEquals(KioskScreenRequest.Show, requests.awaitItem())
            assertEquals(KioskScreensaverMode.DISABLED, repository.getSettings().screensaverMode)
        }
    }

    @Test
    fun `Given a hide screensaver command then the request reaches the screen`() = runTest {
        turbineScope {
            val requests = controller.requests.testIn(backgroundScope)

            handler.handle(KioskPushCommand.HideScreensaver)

            assertEquals(KioskScreenRequest.Hide, requests.awaitItem())
        }
    }

    @Test
    fun `Given remote commands are refused then a show request never reaches the screen`() = runTest {
        repository.setSettings(KioskSettings(acceptRemoteCommands = false))
        turbineScope {
            val requests = controller.requests.testIn(backgroundScope)

            assertFalse(handler.handle(KioskPushCommand.ShowScreensaver))

            requests.expectNoEvents()
            requests.cancel()
        }
    }

    @Test
    fun `Given confirmations are silenced then the handler reports that`() = runTest {
        repository.setSettings(KioskSettings(showRemoteCommandConfirmations = false))

        assertFalse(handler.shouldConfirm())
    }
}
