package io.homeassistant.companion.android.common.data.kiosk

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.di.qualifiers.NamedKioskStorage
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private const val PREF_KIOSK_ENABLED = "kiosk_enabled"
private const val PREF_KIOSK_HIDE_STATUS_BAR = "kiosk_hide_status_bar"
private const val PREF_KIOSK_HIDE_NAVIGATION_BAR = "kiosk_hide_navigation_bar"
private const val PREF_KIOSK_BRIGHTNESS_PERCENT = "kiosk_brightness_percent"
private const val PREF_KIOSK_SCREENSAVER_MODE = "kiosk_screensaver_mode"
private const val PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS = "kiosk_screensaver_idle_timeout_seconds"

internal class KioskSettingsRepositoryImpl @Inject constructor(
    @NamedKioskStorage private val localStorage: LocalStorage,
) : KioskSettingsRepository {

    override suspend fun getSettings(): KioskSettings = readSettings()

    override suspend fun setSettings(settings: KioskSettings) {
        val idleTimeout = settings.screensaverIdleTimeout.coerceAtLeast(KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT)

        localStorage.putBoolean(PREF_KIOSK_ENABLED, settings.enabled)
        localStorage.putBoolean(PREF_KIOSK_HIDE_STATUS_BAR, settings.hideStatusBar)
        localStorage.putBoolean(PREF_KIOSK_HIDE_NAVIGATION_BAR, settings.hideNavigationBar)
        localStorage.putInt(PREF_KIOSK_BRIGHTNESS_PERCENT, settings.brightness?.toPercent())
        localStorage.putString(PREF_KIOSK_SCREENSAVER_MODE, settings.screensaverMode.storageValue)
        localStorage.putLong(PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS, idleTimeout.inWholeSeconds)
    }

    override fun settingsFlow(): Flow<KioskSettings> = flow {
        emitAll(
            localStorage.observeChanges(
                PREF_KIOSK_ENABLED,
                PREF_KIOSK_HIDE_STATUS_BAR,
                PREF_KIOSK_HIDE_NAVIGATION_BAR,
                PREF_KIOSK_BRIGHTNESS_PERCENT,
                PREF_KIOSK_SCREENSAVER_MODE,
                PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS,
            ) { readSettings() },
        )
    }

    /** Reads every stored value into a [KioskSettings], substituting defaults for absent ones. */
    private suspend fun readSettings(): KioskSettings {
        val storedTimeoutSeconds = localStorage.getLong(PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS)
        return KioskSettings(
            enabled = localStorage.getBoolean(PREF_KIOSK_ENABLED),
            hideStatusBar = localStorage.getBoolean(PREF_KIOSK_HIDE_STATUS_BAR),
            hideNavigationBar = localStorage.getBoolean(PREF_KIOSK_HIDE_NAVIGATION_BAR),
            brightness = KioskBrightness.fromPercent(localStorage.getInt(PREF_KIOSK_BRIGHTNESS_PERCENT)),
            screensaverMode = KioskScreensaverMode.fromStorageValue(
                localStorage.getString(PREF_KIOSK_SCREENSAVER_MODE),
            ),
            screensaverIdleTimeout = storedTimeoutSeconds
                ?.seconds
                ?.coerceAtLeast(KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT)
                ?: KioskSettings.DEFAULT_SCREENSAVER_IDLE_TIMEOUT,
        )
    }
}
