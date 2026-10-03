package io.homeassistant.companion.android.kiosk

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObserveKioskStateUseCaseTest {

    private val repository = FakeKioskSettingsRepository()
    private val observeKioskState = ObserveKioskStateUseCase(repository)

    @Test
    fun `Given kiosk disabled when observing then the state is inactive and nothing is hidden`() = runTest {
        repository.setSettings(
            KioskSettings(
                enabled = false,
                hideStatusBar = true,
                hideNavigationBar = true,
                brightness = KioskBrightness.fromPercent(10),
                screensaverMode = KioskScreensaverMode.CLOCK,
            ),
        )

        observeKioskState().test {
            val state = awaitItem()

            assertEquals(KioskState.Inactive, state)
            assertNull(state.forcedBrightness)
            assertNull(state.screensaver)
            assertFalse(state.hidesStatusBar)
            assertFalse(state.hidesNavigationBar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given kiosk enabled when observing then the configured values are in effect`() = runTest {
        repository.setSettings(
            KioskSettings(
                enabled = true,
                hideStatusBar = true,
                hideNavigationBar = false,
                brightness = KioskBrightness.fromPercent(30),
                screensaverMode = KioskScreensaverMode.CLOCK,
                screensaverIdleTimeout = 7.minutes,
            ),
        )

        observeKioskState().test {
            val state = awaitItem()

            assertEquals(KioskBrightness.fromPercent(30), state.forcedBrightness)
            assertEquals(KioskScreensaver(KioskScreensaverMode.CLOCK, 7.minutes), state.screensaver)
            assertTrue(state.hidesStatusBar)
            assertFalse(state.hidesNavigationBar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given no configured brightness when kiosk is enabled then the system keeps control of it`() = runTest {
        repository.setSettings(KioskSettings(enabled = true, brightness = null))

        observeKioskState().test {
            assertNull(awaitItem().forcedBrightness)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the screensaver mode is disabled when kiosk is enabled then there is no screensaver`() = runTest {
        repository.setSettings(
            KioskSettings(
                enabled = true,
                screensaverMode = KioskScreensaverMode.DISABLED,
                screensaverIdleTimeout = 7.minutes,
            ),
        )

        observeKioskState().test {
            assertNull(awaitItem().screensaver)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a collector when kiosk mode is enabled then the new state is emitted`() = runTest {
        observeKioskState().test {
            assertEquals(KioskState.Inactive, awaitItem())

            repository.setSettings(KioskSettings(enabled = true))

            assertEquals(
                KioskState.Active(
                    hidesStatusBar = false,
                    hidesNavigationBar = false,
                    forcedBrightness = null,
                    screensaver = null,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given kiosk disabled when an inert value changes then no new state is emitted`() = runTest {
        observeKioskState().test {
            assertEquals(KioskState.Inactive, awaitItem())

            repository.setSettings(KioskSettings(enabled = false, brightness = KioskBrightness.fromPercent(20)))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the screensaver is disabled when the idle timeout changes then no new state is emitted`() = runTest {
        repository.setSettings(KioskSettings(enabled = true, screensaverMode = KioskScreensaverMode.DISABLED))

        observeKioskState().test {
            awaitItem()

            repository.setSettings(
                KioskSettings(
                    enabled = true,
                    screensaverMode = KioskScreensaverMode.DISABLED,
                    screensaverIdleTimeout = 42.minutes,
                ),
            )

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
