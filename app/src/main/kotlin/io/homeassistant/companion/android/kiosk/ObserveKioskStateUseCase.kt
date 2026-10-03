package io.homeassistant.companion.android.kiosk

import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Maps the stored [KioskSettings] onto the state that applies right now, emitting only when the
 * result changes.
 */
internal class ObserveKioskStateUseCase @Inject constructor(
    private val kioskSettingsRepository: KioskSettingsRepository,
) {

    /**
     * Emits the current state immediately on collection, then on every change that consumers can
     * observe. Changes that leave the resulting state identical — editing the idle timeout while
     * the screensaver is disabled, for example — do not produce an emission.
     */
    operator fun invoke(): Flow<KioskState> = kioskSettingsRepository.settingsFlow()
        .map { it.toKioskState() }
        .distinctUntilChanged()
}

/** Collapses the configured [KioskSettings] into the state that applies right now. */
private fun KioskSettings.toKioskState(): KioskState = if (enabled) {
    KioskState.Active(
        settingsEntry = KioskSettingsEntry(position = settingsEntryPosition, hidden = settingsEntryHidden),
        autoReloadInterval = autoReload.interval,
        keepsScreenOn = keepScreenOn,
        hidesStatusBar = hideStatusBar,
        hidesNavigationBar = hideNavigationBar,
        forcedBrightness = brightness,
        screensaver = screensaverMode
            .takeIf { it != KioskScreensaverMode.DISABLED }
            ?.let { KioskScreensaver(mode = it, idleTimeout = screensaverIdleTimeout) },
        // Listening is pointless without a screensaver to wake from, so the threshold goes away
        // with it rather than leaving the microphone running for nothing.
        soundWakeThreshold = soundWakeThreshold
            .takeIf { wakeOnSound && screensaverMode != KioskScreensaverMode.DISABLED },
    )
} else {
    KioskState.Inactive
}
