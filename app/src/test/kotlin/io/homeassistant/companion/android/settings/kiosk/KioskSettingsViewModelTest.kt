package io.homeassistant.companion.android.settings.kiosk

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.kiosk.KioskAutoReloadInterval
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.kiosk.FakeKioskSettingsRepository
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class KioskSettingsViewModelTest {

    private val repository = FakeKioskSettingsRepository()
    private val serverManager: ServerManager = mockk {
        coEvery { servers() } returns emptyList()
    }
    private lateinit var viewModel: KioskSettingsViewModel

    /**
     * Builds the ViewModel inside the test, after the extension has swapped the Main dispatcher, so
     * `viewModelScope` runs on the scheduler the test advances.
     */
    private fun createViewModel() {
        viewModel = KioskSettingsViewModel(repository, serverManager)
    }

    @Test
    fun `Given a clean install when the screen opens then kiosk mode is off and the system keeps brightness`() = runTest {
        createViewModel()
        viewModel.viewState.test {
            val state = awaitItem()

            assertFalse(state.enabled)
            assertFalse(state.hideStatusBar)
            assertFalse(state.hideNavigationBar)
            assertEquals(KioskBrightnessOption.SystemAdjusted, state.brightness)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the user turns kiosk mode on then it is stored and shown as on`() = runTest {
        createViewModel()
        viewModel.viewState.test {
            assertFalse(awaitItem().enabled)

            viewModel.onEnabledChanged(true)
            advanceUntilIdle()

            assertTrue(awaitItem().enabled)
            assertTrue(repository.getSettings().enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a clean install then remote commands and their confirmations are on`() = runTest {
        createViewModel()

        viewModel.viewState.test {
            val state = awaitItem()

            assertTrue(state.acceptRemoteCommands)
            assertTrue(state.showRemoteCommandConfirmations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the user refuses remote commands then it is stored`() = runTest {
        createViewModel()

        viewModel.onAcceptRemoteCommandsChanged(false)
        advanceUntilIdle()

        assertFalse(repository.getSettings().acceptRemoteCommands)
    }

    @Test
    fun `Given the user silences confirmations then commands are still accepted`() = runTest {
        createViewModel()

        viewModel.onShowRemoteCommandConfirmationsChanged(false)
        advanceUntilIdle()

        val stored = repository.getSettings()
        assertFalse(stored.showRemoteCommandConfirmations)
        assertTrue(stored.acceptRemoteCommands)
    }

    @Test
    fun `Given the settings are unprotected then nothing is locked`() = runTest {
        createViewModel()

        viewModel.viewState.test {
            assertEquals(KioskSettingsLock.UNKNOWN, awaitItem().lock)
            assertEquals(KioskSettingsLock.NOT_REQUIRED, awaitItem().lock)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the settings are protected then they start locked`() = runTest {
        repository.setSettings(KioskSettings(requireAuthentication = true))
        createViewModel()

        viewModel.viewState.test {
            assertEquals(KioskSettingsLock.UNKNOWN, awaitItem().lock)
            assertEquals(KioskSettingsLock.LOCKED, awaitItem().lock)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given protected settings when the user authenticates then they unlock`() = runTest {
        repository.setSettings(KioskSettings(requireAuthentication = true))
        createViewModel()

        viewModel.viewState.test {
            assertEquals(KioskSettingsLock.UNKNOWN, awaitItem().lock)
            assertEquals(KioskSettingsLock.LOCKED, awaitItem().lock)

            viewModel.onAuthenticated()

            assertEquals(KioskSettingsLock.UNLOCKED, awaitItem().lock)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the user asks for protection then it is stored`() = runTest {
        createViewModel()

        viewModel.onRequireAuthenticationChanged(true)
        advanceUntilIdle()

        assertTrue(repository.getSettings().requireAuthentication)
    }

    @Test
    fun `Given the user picks a reload interval then it is stored`() = runTest {
        createViewModel()

        viewModel.onDisplaySettingChanged(KioskDisplaySetting.AutoReload(KioskAutoReloadInterval.MINUTES_15))
        advanceUntilIdle()

        assertEquals(KioskAutoReloadInterval.MINUTES_15, repository.getSettings().autoReload)
    }

    @Test
    fun `Given the user keeps the screen on then it is stored`() = runTest {
        createViewModel()

        viewModel.onDisplaySettingChanged(KioskDisplaySetting.KeepScreenOn(true))
        advanceUntilIdle()

        assertTrue(repository.getSettings().keepScreenOn)
    }

    @Test
    fun `Given the user hides both system bars then both are stored`() = runTest {
        createViewModel()
        viewModel.onDisplaySettingChanged(KioskDisplaySetting.HideStatusBar(true))
        advanceUntilIdle()
        viewModel.onDisplaySettingChanged(KioskDisplaySetting.HideNavigationBar(true))
        advanceUntilIdle()

        val stored = repository.getSettings()
        assertTrue(stored.hideStatusBar)
        assertTrue(stored.hideNavigationBar)
    }

    @Test
    fun `Given the user picks a fixed brightness then it is stored as that percentage`() = runTest {
        createViewModel()
        viewModel.onDisplaySettingChanged(KioskDisplaySetting.Brightness(KioskBrightnessOption.Fixed(40)))
        advanceUntilIdle()

        assertEquals(KioskBrightness.fromPercent(40), repository.getSettings().brightness)
    }

    @Test
    fun `Given a fixed brightness when the user chooses system adjusted then no brightness is stored`() = runTest {
        repository.setSettings(KioskSettings(brightness = KioskBrightness.fromPercent(40)))
        createViewModel()

        viewModel.onDisplaySettingChanged(KioskDisplaySetting.Brightness(KioskBrightnessOption.SystemAdjusted))
        advanceUntilIdle()

        assertNull(repository.getSettings().brightness)
    }

    @Test
    fun `Given a setting changes elsewhere then the screen follows it`() = runTest {
        createViewModel()
        viewModel.viewState.test {
            assertFalse(awaitItem().enabled)

            repository.setSettings(KioskSettings(enabled = true, hideStatusBar = true))

            val state = awaitItem()
            assertTrue(state.enabled)
            assertTrue(state.hideStatusBar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a toggle is flipped then the other stored settings are left alone`() = runTest {
        repository.setSettings(
            KioskSettings(enabled = true, hideStatusBar = true, brightness = KioskBrightness.fromPercent(30)),
        )
        createViewModel()

        viewModel.onDisplaySettingChanged(KioskDisplaySetting.HideNavigationBar(true))
        advanceUntilIdle()

        val stored = repository.getSettings()
        assertTrue(stored.enabled)
        assertTrue(stored.hideStatusBar)
        assertTrue(stored.hideNavigationBar)
        assertEquals(KioskBrightness.fromPercent(30), stored.brightness)
    }
}
