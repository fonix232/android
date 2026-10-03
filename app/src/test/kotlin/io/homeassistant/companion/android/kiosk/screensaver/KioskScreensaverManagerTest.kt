package io.homeassistant.companion.android.kiosk.screensaver

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverController
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverRequest
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.kiosk.FakeKioskSettingsRepository
import io.homeassistant.companion.android.kiosk.ObserveKioskStateUseCase
import io.homeassistant.companion.android.testing.unit.FakeClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class KioskScreensaverManagerTest {

    private val repository = FakeKioskSettingsRepository()
    private val clock = FakeClock().apply { currentInstant = START }
    private val controller = KioskScreensaverController()
    private val manager = KioskScreensaverManager(ObserveKioskStateUseCase(repository), controller, clock)

    /**
     * Advances the test scheduler and the [FakeClock] together, so `delay` and `Clock.now()` agree
     * on how much time has passed.
     */
    private fun TestScope.advanceBoth(duration: Duration) {
        clock.currentInstant += duration
        advanceTimeBy(duration)
    }

    private suspend fun enableScreensaver(
        mode: KioskScreensaverMode = KioskScreensaverMode.CLOCK,
        idleTimeout: Duration = IDLE_TIMEOUT,
    ) {
        repository.setSettings(
            KioskSettings(enabled = true, screensaverMode = mode, screensaverIdleTimeout = idleTimeout),
        )
    }

    @Test
    fun `Given no screensaver configured when idle then nothing is shown`() = runTest {
        repository.setSettings(KioskSettings(enabled = true, screensaverMode = KioskScreensaverMode.DISABLED))

        manager.screensaverFlow().test {
            assertNull(awaitItem())

            advanceBoth(1.minutes * 10)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given kiosk mode disabled when idle then nothing is shown`() = runTest {
        repository.setSettings(
            KioskSettings(enabled = false, screensaverMode = KioskScreensaverMode.CLOCK, screensaverIdleTimeout = IDLE_TIMEOUT),
        )

        manager.screensaverFlow().test {
            assertNull(awaitItem())

            advanceBoth(IDLE_TIMEOUT * 2)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a screensaver when the idle timeout passes then it is shown with the current time`() = runTest {
        enableScreensaver()

        manager.screensaverFlow().test {
            assertNull(awaitItem())

            advanceBoth(IDLE_TIMEOUT)

            assertEquals(
                KioskScreensaverUiState(mode = KioskScreensaverMode.CLOCK, now = START + IDLE_TIMEOUT),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a screensaver when the user interacts before the timeout then it is not shown`() = runTest {
        enableScreensaver()

        manager.screensaverFlow().test {
            assertNull(awaitItem())

            advanceBoth(IDLE_TIMEOUT - 1.seconds)
            manager.onUserInteraction()
            advanceBoth(IDLE_TIMEOUT - 1.seconds)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the screensaver is showing when the user interacts then the dashboard comes back`() = runTest {
        enableScreensaver()

        manager.screensaverFlow().test {
            assertNull(awaitItem())
            advanceBoth(IDLE_TIMEOUT)
            awaitItem()

            manager.onUserInteraction()

            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the screensaver is showing when the minute changes then the time is refreshed`() = runTest {
        enableScreensaver()

        manager.screensaverFlow().test {
            assertNull(awaitItem())
            advanceBoth(IDLE_TIMEOUT)
            assertEquals(START + IDLE_TIMEOUT, awaitItem()?.now)

            advanceBoth(1.minutes)

            assertEquals(START + IDLE_TIMEOUT + 1.minutes, awaitItem()?.now)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the screensaver is showing when its mode changes then the new mode replaces it`() = runTest {
        enableScreensaver(mode = KioskScreensaverMode.CLOCK)

        manager.screensaverFlow().test {
            assertNull(awaitItem())
            advanceBoth(IDLE_TIMEOUT)
            assertEquals(KioskScreensaverMode.CLOCK, awaitItem()?.mode)

            enableScreensaver(mode = KioskScreensaverMode.BLANK)

            // The countdown restarts against the last interaction, not against the change, so a
            // device that is already idle swaps screensaver instead of showing the dashboard for
            // another timeout.
            assertNull(awaitItem())
            assertEquals(KioskScreensaverMode.BLANK, awaitItem()?.mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a show request when the device is not idle then the screensaver appears at once`() = runTest {
        enableScreensaver()
        backgroundScope.launch { manager.observeRequests() }

        manager.screensaverFlow().test {
            assertNull(awaitItem())

            controller.request(KioskScreensaverRequest.Show)
            runCurrent()

            assertEquals(KioskScreensaverMode.CLOCK, awaitItem()?.mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the screensaver is showing when a hide request arrives then the dashboard comes back`() = runTest {
        enableScreensaver()
        backgroundScope.launch { manager.observeRequests() }

        manager.screensaverFlow().test {
            assertNull(awaitItem())
            advanceBoth(IDLE_TIMEOUT)
            assertEquals(KioskScreensaverMode.CLOCK, awaitItem()?.mode)

            controller.request(KioskScreensaverRequest.Hide)
            runCurrent()

            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given no screensaver configured when a show request arrives then nothing is shown`() = runTest {
        repository.setSettings(KioskSettings(enabled = true, screensaverMode = KioskScreensaverMode.DISABLED))
        backgroundScope.launch { manager.observeRequests() }

        manager.screensaverFlow().test {
            assertNull(awaitItem())

            controller.request(KioskScreensaverRequest.Show)
            runCurrent()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the screensaver appears then the controller reports it visible`() = runTest {
        enableScreensaver()

        manager.screensaverFlow().test {
            assertNull(awaitItem())
            assertFalse(controller.isVisible.value)

            advanceBoth(IDLE_TIMEOUT)
            awaitItem()

            assertTrue(controller.isVisible.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        /** A whole minute, so the clock's minute-boundary refresh lands on predictable instants. */
        val START: Instant = Instant.fromEpochSeconds(1_700_000_040)

        val IDLE_TIMEOUT = 5.minutes
    }
}
