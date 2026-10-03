package io.homeassistant.companion.android.common.data.kiosk

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.di.qualifiers.NamedKioskStorage
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private const val PREF_KIOSK_ENABLED = "kiosk_enabled"
private const val PREF_KIOSK_SETTINGS_ENTRY_POSITION = "kiosk_settings_entry_position"
private const val PREF_KIOSK_SETTINGS_ENTRY_HIDDEN = "kiosk_settings_entry_hidden"
private const val PREF_KIOSK_SERVER_ID = "kiosk_server_id"
private const val PREF_KIOSK_DASHBOARD_PATH = "kiosk_dashboard_path"
private const val PREF_KIOSK_REQUIRE_AUTHENTICATION = "kiosk_require_authentication"
private const val PREF_KIOSK_ACCEPT_REMOTE_COMMANDS = "kiosk_accept_remote_commands"
private const val PREF_KIOSK_SHOW_REMOTE_COMMAND_CONFIRMATIONS = "kiosk_show_remote_command_confirmations"
private const val PREF_KIOSK_KEEP_SCREEN_ON = "kiosk_keep_screen_on"
private const val PREF_KIOSK_HIDE_STATUS_BAR = "kiosk_hide_status_bar"
private const val PREF_KIOSK_HIDE_NAVIGATION_BAR = "kiosk_hide_navigation_bar"
private const val PREF_KIOSK_BRIGHTNESS_PERCENT = "kiosk_brightness_percent"
private const val PREF_KIOSK_AUTO_RELOAD = "kiosk_auto_reload"
private const val PREF_KIOSK_WAKE_ON_SOUND = "kiosk_wake_on_sound"
private const val PREF_KIOSK_SOUND_WAKE_THRESHOLD_DBFS = "kiosk_sound_wake_threshold_dbfs"
private const val PREF_KIOSK_WAKE_ON_CAMERA_MOTION = "kiosk_wake_on_camera_motion"
private const val PREF_KIOSK_SCREENSAVER_MODE = "kiosk_screensaver_mode"
private const val PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS = "kiosk_screensaver_idle_timeout_seconds"

internal class KioskSettingsRepositoryImpl @Inject constructor(
    @NamedKioskStorage private val localStorage: LocalStorage,
) : KioskSettingsRepository {

    override suspend fun getSettings(): KioskSettings = readSettings()

    override suspend fun setSettings(settings: KioskSettings) {
        val idleTimeout = settings.screensaverIdleTimeout.coerceAtLeast(KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT)

        localStorage.putBoolean(PREF_KIOSK_ENABLED, settings.enabled)
        localStorage.putString(PREF_KIOSK_SETTINGS_ENTRY_POSITION, settings.settingsEntryPosition.storageValue)
        localStorage.putBoolean(PREF_KIOSK_SETTINGS_ENTRY_HIDDEN, settings.settingsEntryHidden)
        localStorage.putInt(PREF_KIOSK_SERVER_ID, settings.serverId)
        localStorage.putString(PREF_KIOSK_DASHBOARD_PATH, settings.dashboardPath)
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
        localStorage.putBoolean(PREF_KIOSK_WAKE_ON_SOUND, settings.wakeOnSound)
        localStorage.putInt(PREF_KIOSK_SOUND_WAKE_THRESHOLD_DBFS, settings.soundWakeThreshold.dbfs.toInt())
        localStorage.putBoolean(PREF_KIOSK_WAKE_ON_CAMERA_MOTION, settings.wakeOnCameraMotion)
        localStorage.putString(PREF_KIOSK_SCREENSAVER_MODE, settings.screensaverMode.storageValue)
        localStorage.putLong(PREF_KIOSK_SCREENSAVER_IDLE_TIMEOUT_SECONDS, idleTimeout.inWholeSeconds)
    }

    override fun settingsFlow(): Flow<KioskSettings> = flow {
        emitAll(
            localStorage.observeChanges(
                PREF_KIOSK_ENABLED,
                PREF_KIOSK_SETTINGS_ENTRY_POSITION,
                PREF_KIOSK_SETTINGS_ENTRY_HIDDEN,
                PREF_KIOSK_SERVER_ID,
                PREF_KIOSK_DASHBOARD_PATH,
                PREF_KIOSK_REQUIRE_AUTHENTICATION,
                PREF_KIOSK_ACCEPT_REMOTE_COMMANDS,
                PREF_KIOSK_SHOW_REMOTE_COMMAND_CONFIRMATIONS,
                PREF_KIOSK_KEEP_SCREEN_ON,
                PREF_KIOSK_HIDE_STATUS_BAR,
                PREF_KIOSK_HIDE_NAVIGATION_BAR,
                PREF_KIOSK_BRIGHTNESS_PERCENT,
                PREF_KIOSK_AUTO_RELOAD,
                PREF_KIOSK_WAKE_ON_SOUND,
                PREF_KIOSK_SOUND_WAKE_THRESHOLD_DBFS,
                PREF_KIOSK_WAKE_ON_CAMERA_MOTION,
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
            settingsEntryPosition = KioskCornerPosition.fromStorageValue(
                localStorage.getString(PREF_KIOSK_SETTINGS_ENTRY_POSITION),
            ),
            settingsEntryHidden = localStorage.getBoolean(PREF_KIOSK_SETTINGS_ENTRY_HIDDEN),
            serverId = localStorage.getInt(PREF_KIOSK_SERVER_ID),
            // Blank is stored as absent, so a cleared field reads back as the server's default.
            dashboardPath = localStorage.getString(PREF_KIOSK_DASHBOARD_PATH)?.takeIf { it.isNotBlank() },
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
            wakeOnSound = localStorage.getBoolean(PREF_KIOSK_WAKE_ON_SOUND),
            soundWakeThreshold = localStorage.getInt(PREF_KIOSK_SOUND_WAKE_THRESHOLD_DBFS)
                ?.let { KioskSoundThreshold.ofDbfs(it.toFloat()) }
                ?: KioskSoundThreshold.DEFAULT,
            wakeOnCameraMotion = localStorage.getBoolean(PREF_KIOSK_WAKE_ON_CAMERA_MOTION),
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
