package io.homeassistant.companion.android.common.data.kiosk

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.di.qualifiers.NamedKioskStorage
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PREF_KIOSK_ENABLED = "kiosk_enabled"
private const val PREF_KIOSK_REQUIRE_AUTHENTICATION = "kiosk_require_authentication"
private const val PREF_KIOSK_ACCEPT_REMOTE_COMMANDS = "kiosk_accept_remote_commands"
private const val PREF_KIOSK_SHOW_REMOTE_COMMAND_CONFIRMATIONS = "kiosk_show_remote_command_confirmations"
private const val PREF_KIOSK_KEEP_SCREEN_ON = "kiosk_keep_screen_on"
private const val PREF_KIOSK_HIDE_STATUS_BAR = "kiosk_hide_status_bar"
private const val PREF_KIOSK_HIDE_NAVIGATION_BAR = "kiosk_hide_navigation_bar"
private const val PREF_KIOSK_BRIGHTNESS_PERCENT = "kiosk_brightness_percent"
private const val PREF_KIOSK_AUTO_RELOAD = "kiosk_auto_reload"
private const val PREF_KIOSK_SCREENSAVER_MODE = "kiosk_screensaver_mode"
private const val PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS = "kiosk_screensaver_idle_timeout_seconds"

internal class KioskSettingsRepositoryImpl @Inject constructor(
    @NamedKioskStorage private val localStorage: LocalStorage,
) : KioskSettingsRepository {

    private val writeMutex = Mutex()

    override suspend fun getSettings(): KioskSettings = readSettings()

    override suspend fun setSettings(settings: KioskSettings) = writeMutex.withLock {
        writeSettings(settings)
    }

    override suspend fun updateSettings(change: (KioskSettings) -> KioskSettings) = writeMutex.withLock {
        writeSettings(change(readSettings()))
    }

    private suspend fun writeSettings(settings: KioskSettings) {
        val idleTimeout = settings.screensaverIdleTimeout.coerceAtLeast(KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT)

        // Turning kiosk mode off first, and on last, keeps a collector from applying the new
        // configuration while kiosk mode is off or the old one after it is on.
        if (!settings.enabled) localStorage.putBoolean(PREF_KIOSK_ENABLED, false)

        localStorage.putBoolean(PREF_KIOSK_REQUIRE_AUTHENTICATION, settings.requireAuthentication)
        localStorage.putBoolean(PREF_KIOSK_ACCEPT_REMOTE_COMMANDS, settings.acceptRemoteCommands)
        localStorage.putBoolean(
            PREF_KIOSK_SHOW_REMOTE_COMMAND_CONFIRMATIONS,
            settings.showRemoteCommandConfirmations,
        )
        localStorage.putBoolean(PREF_KIOSK_KEEP_SCREEN_ON, settings.keepScreenOn)
        localStorage.putBoolean(PREF_KIOSK_HIDE_STATUS_BAR, settings.hideStatusBar)
        localStorage.putBoolean(PREF_KIOSK_HIDE_NAVIGATION_BAR, settings.hideNavigationBar)
        localStorage.putInt(PREF_KIOSK_BRIGHTNESS_PERCENT, settings.brightness?.toPercent())
        localStorage.putString(PREF_KIOSK_AUTO_RELOAD, settings.autoReload.storageValue)
        localStorage.putString(PREF_KIOSK_SCREENSAVER_MODE, settings.screensaverMode.storageValue)
        localStorage.putLong(PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS, idleTimeout.inWholeSeconds)

        if (settings.enabled) localStorage.putBoolean(PREF_KIOSK_ENABLED, true)
    }

    override fun settingsFlow(): Flow<KioskSettings> = flow {
        emitAll(
            localStorage.observeChanges(
                PREF_KIOSK_ENABLED,
                PREF_KIOSK_REQUIRE_AUTHENTICATION,
                PREF_KIOSK_ACCEPT_REMOTE_COMMANDS,
                PREF_KIOSK_SHOW_REMOTE_COMMAND_CONFIRMATIONS,
                PREF_KIOSK_KEEP_SCREEN_ON,
                PREF_KIOSK_HIDE_STATUS_BAR,
                PREF_KIOSK_HIDE_NAVIGATION_BAR,
                PREF_KIOSK_BRIGHTNESS_PERCENT,
                PREF_KIOSK_AUTO_RELOAD,
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
            requireAuthentication = localStorage.getBoolean(PREF_KIOSK_REQUIRE_AUTHENTICATION),
            // These two default to true, so an absent value cannot go through getBoolean, which
            // reports false for anything it has not stored.
            acceptRemoteCommands = localStorage.getBooleanOrNull(PREF_KIOSK_ACCEPT_REMOTE_COMMANDS) ?: true,
            showRemoteCommandConfirmations =
            localStorage.getBooleanOrNull(PREF_KIOSK_SHOW_REMOTE_COMMAND_CONFIRMATIONS) ?: true,
            keepScreenOn = localStorage.getBoolean(PREF_KIOSK_KEEP_SCREEN_ON),
            hideStatusBar = localStorage.getBoolean(PREF_KIOSK_HIDE_STATUS_BAR),
            hideNavigationBar = localStorage.getBoolean(PREF_KIOSK_HIDE_NAVIGATION_BAR),
            brightness = KioskBrightness.fromPercent(localStorage.getInt(PREF_KIOSK_BRIGHTNESS_PERCENT)),
            autoReload = KioskAutoReloadInterval.fromStorageValue(localStorage.getString(PREF_KIOSK_AUTO_RELOAD)),
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
