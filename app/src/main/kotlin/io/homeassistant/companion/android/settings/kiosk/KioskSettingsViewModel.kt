package io.homeassistant.companion.android.settings.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A brightness the settings screen offers.
 *
 * [SystemAdjusted] is not "zero brightness": it means kiosk mode does not set a brightness at all
 * and leaves the display to the system, including its automatic adjustment.
 */
internal sealed interface KioskBrightnessOption {
    data object SystemAdjusted : KioskBrightnessOption

    data class Fixed(val percent: Int) : KioskBrightnessOption
}

/** What the kiosk settings screen renders. */
internal data class KioskSettingsViewState(
    val enabled: Boolean = false,
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val brightness: KioskBrightnessOption = KioskBrightnessOption.SystemAdjusted,
)

@HiltViewModel
internal class KioskSettingsViewModel @Inject constructor(
    private val kioskSettingsRepository: KioskSettingsRepository,
) : ViewModel() {

    val viewState: StateFlow<KioskSettingsViewState> = kioskSettingsRepository.settingsFlow()
        .map { it.toViewState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), KioskSettingsViewState())

    /** Turns kiosk mode on or off. */
    fun onEnabledChanged(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** Chooses whether the status bar is hidden while kiosk mode is on. */
    fun onHideStatusBarChanged(hide: Boolean) = update { it.copy(hideStatusBar = hide) }

    /** Chooses whether the navigation bar is hidden while kiosk mode is on. */
    fun onHideNavigationBarChanged(hide: Boolean) = update { it.copy(hideNavigationBar = hide) }

    /** Chooses the brightness kiosk mode forces, or leaves it to the system. */
    fun onBrightnessChanged(option: KioskBrightnessOption) = update {
        it.copy(
            brightness = when (option) {
                KioskBrightnessOption.SystemAdjusted -> null
                is KioskBrightnessOption.Fixed -> KioskBrightness.fromPercent(option.percent)
            },
        )
    }

    /**
     * Applies [change] to the stored settings.
     *
     * Reads the settings back rather than editing the last rendered state, so a change made
     * elsewhere between render and tap is not silently reverted.
     */
    private fun update(change: (KioskSettings) -> KioskSettings) {
        viewModelScope.launch {
            kioskSettingsRepository.setSettings(change(kioskSettingsRepository.getSettings()))
        }
    }
}

private fun KioskSettings.toViewState(): KioskSettingsViewState = KioskSettingsViewState(
    enabled = enabled,
    hideStatusBar = hideStatusBar,
    hideNavigationBar = hideNavigationBar,
    brightness = brightness
        ?.let { KioskBrightnessOption.Fixed(it.toPercent()) }
        ?: KioskBrightnessOption.SystemAdjusted,
)
