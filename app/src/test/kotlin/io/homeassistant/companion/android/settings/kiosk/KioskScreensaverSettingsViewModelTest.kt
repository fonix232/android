package io.homeassistant.companion.android.settings.kiosk

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.kiosk.KIOSK_SOUND_CEILING_DBFS
import io.homeassistant.companion.android.common.data.kiosk.KIOSK_SOUND_FLOOR_DBFS
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.kiosk.FakeKioskSettingsRepository
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.mockk
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class KioskScreensaverSettingsViewModelTest {

    private val repository = FakeKioskSettingsRepository()
    private lateinit var viewModel: KioskScreensaverSettingsViewModel

    private fun createViewModel() {
        viewModel = KioskScreensaverSettingsViewModel(repository, mockk(relaxed = true))
    }

    @Test
    fun `Given a clean install when the screen opens then the screensaver is off`() = runTest {
        createViewModel()

        viewModel.viewState.test {
            val state = awaitItem()

            assertEquals(KioskScreensaverMode.DISABLED, state.mode)
            assertFalse(state.isTimeoutRelevant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the user picks a screensaver then the timeout becomes relevant`() = runTest {
        createViewModel()

        viewModel.viewState.test {
            awaitItem()

            viewModel.onModeChanged(KioskScreensaverMode.CLOCK)
            advanceUntilIdle()

            val state = awaitItem()
            assertEquals(KioskScreensaverMode.CLOCK, state.mode)
            assertTrue(state.isTimeoutRelevant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the user picks an idle timeout then it is stored`() = runTest {
        createViewModel()

        viewModel.onIdleTimeoutChanged(15.minutes)
        advanceUntilIdle()

        assertEquals(15.minutes, repository.getSettings().screensaverIdleTimeout)
    }

    @Test
    fun `Given the mode changes then the stored idle timeout is left alone`() = runTest {
        repository.setSettings(KioskSettings(screensaverIdleTimeout = 30.minutes))
        createViewModel()

        viewModel.onModeChanged(KioskScreensaverMode.BLANK)
        advanceUntilIdle()

        val stored = repository.getSettings()
        assertEquals(KioskScreensaverMode.BLANK, stored.screensaverMode)
        assertEquals(30.minutes, stored.screensaverIdleTimeout)
    }

    @Test
    fun `Given the user turns on waking on sound then it is stored`() = runTest {
        createViewModel()

        viewModel.onWakeOnSoundChanged(true)
        advanceUntilIdle()

        assertTrue(repository.getSettings().wakeOnSound)
    }

    @Test
    fun `Given the user drags the threshold to the top then the loudest level is stored`() = runTest {
        createViewModel()

        viewModel.onSoundWakeThresholdChanged(1f)
        advanceUntilIdle()

        assertEquals(KIOSK_SOUND_CEILING_DBFS, repository.getSettings().soundWakeThreshold.dbfs)
    }

    @Test
    fun `Given the user drags the threshold past the end then it is stored clamped`() = runTest {
        createViewModel()

        viewModel.onSoundWakeThresholdChanged(2f)
        advanceUntilIdle()

        assertEquals(KIOSK_SOUND_CEILING_DBFS, repository.getSettings().soundWakeThreshold.dbfs)
    }

    @Test
    fun `Given the user drags the threshold to the bottom then the meter floor is stored`() = runTest {
        createViewModel()

        viewModel.onSoundWakeThresholdChanged(0f)
        advanceUntilIdle()

        assertEquals(KIOSK_SOUND_FLOOR_DBFS, repository.getSettings().soundWakeThreshold.dbfs)
    }

    @Test
    fun `Given the user drags the threshold to the middle then the midpoint level is stored`() = runTest {
        createViewModel()

        viewModel.onSoundWakeThresholdChanged(0.5f)
        advanceUntilIdle()

        assertEquals(-30f, repository.getSettings().soundWakeThreshold.dbfs)
    }

    @Test
    fun `Given the user turns on waking on movement then it is stored`() = runTest {
        createViewModel()

        viewModel.onWakeOnCameraMotionChanged(true)
        advanceUntilIdle()

        assertTrue(repository.getSettings().wakeOnCameraMotion)
    }

    @Test
    fun `Given every offered timeout then none is below the enforced minimum`() {
        assertTrue(SCREENSAVER_TIMEOUT_CHOICES.all { it >= KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT })
    }
}
