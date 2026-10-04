package io.homeassistant.companion.android.settings.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Idle timeouts the screensaver settings offer.
 *
 * Fixed choices rather than a free duration: a kiosk only ever wants a round number here, and a
 * picker spares the screen a duration input it would otherwise have to validate against
 * [KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT].
 */
internal val SCREENSAVER_TIMEOUT_CHOICES: List<Duration> =
    listOf(1.minutes, 5.minutes, 10.minutes, 15.minutes, 30.minutes, 1.hours)

/** What the kiosk screensaver settings screen renders. */
internal data class KioskScreensaverSettingsViewState(
    val mode: KioskScreensaverMode = KioskScreensaverMode.DISABLED,
    val idleTimeout: Duration = KioskSettings.DEFAULT_SCREENSAVER_IDLE_TIMEOUT,
) {
    /** The idle timeout only matters once a screensaver is chosen. */
    val isTimeoutRelevant: Boolean
        get() = mode != KioskScreensaverMode.DISABLED
}

@HiltViewModel
internal class KioskScreensaverSettingsViewModel @Inject constructor(
    private val kioskSettingsRepository: KioskSettingsRepository,
) : ViewModel() {

    val viewState: StateFlow<KioskScreensaverSettingsViewState> = kioskSettingsRepository.settingsFlow()
        .map { KioskScreensaverSettingsViewState(mode = it.screensaverMode, idleTimeout = it.screensaverIdleTimeout) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), KioskScreensaverSettingsViewState())

    /** Chooses what the screensaver shows, or turns it off. */
    fun onModeChanged(mode: KioskScreensaverMode) = update { it.copy(screensaverMode = mode) }

    /** Chooses how long the device must be idle before the screensaver appears. */
    fun onIdleTimeoutChanged(idleTimeout: Duration) = update { it.copy(screensaverIdleTimeout = idleTimeout) }

    /**
     * Applies [change] to the stored settings.
     *
     * Reads the settings back rather than editing the last rendered state, so a change made
     * elsewhere between render and tap is not silently reverted.
     */
    private fun update(change: (KioskSettings) -> KioskSettings) {
        viewModelScope.launch {
            kioskSettingsRepository.updateSettings(change)
        }
    }
}
