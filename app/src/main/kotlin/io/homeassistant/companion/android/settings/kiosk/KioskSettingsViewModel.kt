package io.homeassistant.companion.android.settings.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
/** Whether the kiosk settings are readable, or still waiting behind the device's lock credential. */
internal enum class KioskSettingsLock {
    /**
     * The stored settings have not arrived yet, so it is not known whether they are protected.
     *
     * The screen shows neither the settings nor the lock until this resolves. Guessing either way
     * would flash the wrong thing: assuming unprotected would put protected settings on screen,
     * and assuming protected would make everyone else watch a lock appear and vanish.
     */
    UNKNOWN,

    /** Nothing is hidden; the user did not ask for the settings to be protected. */
    NOT_REQUIRED,

    /** Protected and not yet unlocked, so the settings must stay off screen. */
    LOCKED,

    /** Protected, and unlocked for as long as this screen lives. */
    UNLOCKED,
}

internal data class KioskSettingsViewState(
    val lock: KioskSettingsLock = KioskSettingsLock.UNKNOWN,
    val enabled: Boolean = false,
    val requireAuthentication: Boolean = false,
    val acceptRemoteCommands: Boolean = true,
    val showRemoteCommandConfirmations: Boolean = true,
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val brightness: KioskBrightnessOption = KioskBrightnessOption.SystemAdjusted,
)

@HiltViewModel
internal class KioskSettingsViewModel @Inject constructor(
    private val kioskSettingsRepository: KioskSettingsRepository,
) : ViewModel() {

    private val unlocked = MutableStateFlow(false)

    val viewState: StateFlow<KioskSettingsViewState> =
        combine(kioskSettingsRepository.settingsFlow(), unlocked, KioskSettings::toViewState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), KioskSettingsViewState())

    /** Turns kiosk mode on or off. */
    fun onEnabledChanged(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** Chooses whether reading these settings asks for the device's lock credential first. */
    fun onRequireAuthenticationChanged(require: Boolean) = update { it.copy(requireAuthentication = require) }

    /**
     * Records whether the user has cleared the lock.
     *
     * Cleared again when the screen leaves the foreground, so returning to protected settings asks
     * for the credential rather than showing what was already unlocked.
     */
    fun onUnlockedChanged(unlocked: Boolean) {
        this.unlocked.value = unlocked
    }

    /** Chooses whether kiosk commands from the server are obeyed. */
    fun onAcceptRemoteCommandsChanged(accept: Boolean) = update { it.copy(acceptRemoteCommands = accept) }

    /** Chooses whether obeying a kiosk command from the server tells the user it happened. */
    fun onShowRemoteCommandConfirmationsChanged(show: Boolean) = update {
        it.copy(showRemoteCommandConfirmations = show)
    }

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
            kioskSettingsRepository.updateSettings(change)
        }
    }
}

private fun KioskSettings.toViewState(unlocked: Boolean): KioskSettingsViewState = KioskSettingsViewState(
    lock = when {
        !requireAuthentication -> KioskSettingsLock.NOT_REQUIRED
        unlocked -> KioskSettingsLock.UNLOCKED
        else -> KioskSettingsLock.LOCKED
    },
    enabled = enabled,
    requireAuthentication = requireAuthentication,
    acceptRemoteCommands = acceptRemoteCommands,
    showRemoteCommandConfirmations = showRemoteCommandConfirmations,
    hideStatusBar = hideStatusBar,
    hideNavigationBar = hideNavigationBar,
    brightness = brightness
        ?.let { KioskBrightnessOption.Fixed(it.toPercent()) }
        ?: KioskBrightnessOption.SystemAdjusted,
)
