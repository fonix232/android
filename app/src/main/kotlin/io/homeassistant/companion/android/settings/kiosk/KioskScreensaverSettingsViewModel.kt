package io.homeassistant.companion.android.settings.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundLevel
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundThreshold
import io.homeassistant.companion.android.kiosk.audio.KioskAudioWakeDetector
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val wakeOnSound: Boolean = false,
    val soundWakeThreshold: KioskSoundThreshold = KioskSoundThreshold.DEFAULT,
) {
    /** The idle timeout only matters once a screensaver is chosen. */
    val isTimeoutRelevant: Boolean
        get() = mode != KioskScreensaverMode.DISABLED
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class KioskScreensaverSettingsViewModel @Inject constructor(
    private val kioskSettingsRepository: KioskSettingsRepository,
    audioWakeDetector: KioskAudioWakeDetector,
) : ViewModel() {

    val viewState: StateFlow<KioskScreensaverSettingsViewState> = kioskSettingsRepository.settingsFlow()
        .map {
            KioskScreensaverSettingsViewState(
                mode = it.screensaverMode,
                idleTimeout = it.screensaverIdleTimeout,
                wakeOnSound = it.wakeOnSound,
                soundWakeThreshold = it.soundWakeThreshold,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), KioskScreensaverSettingsViewState())

    /**
     * The microphone level while the user is calibrating, or `null` when nothing is listening.
     *
     * Collected only while this screen shows it and only once sound waking is on, so opening the
     * settings does not open the microphone.
     */
    val soundLevel: StateFlow<KioskSoundLevel?> = kioskSettingsRepository.settingsFlow()
        .map { it.wakeOnSound }
        .distinctUntilChanged()
        .flatMapLatest { enabled -> if (enabled) audioWakeDetector.levels() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Chooses whether a sound loud enough counts as somebody being there. */
    fun onWakeOnSoundChanged(wake: Boolean) = update { it.copy(wakeOnSound = wake) }

    /**
     * Chooses how loud a sound has to be before it counts, from a point on the meter.
     *
     * The slider works in the meter's own 0..1 travel; the conversion to dBFS lives in
     * [KioskSoundThreshold] so the stored unit is never in question.
     */
    fun onSoundWakeThresholdChanged(meterPosition: Float) = update {
        it.copy(soundWakeThreshold = KioskSoundThreshold.ofMeterPosition(meterPosition))
    }

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
            kioskSettingsRepository.setSettings(change(kioskSettingsRepository.getSettings()))
        }
    }
}
