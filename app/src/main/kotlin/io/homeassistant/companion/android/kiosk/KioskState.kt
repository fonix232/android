package io.homeassistant.companion.android.kiosk

import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskCornerPosition
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundThreshold
import kotlin.time.Duration

/**
 * The kiosk configuration in effect right now, as the rest of the app should act on it.
 *
 * Distinct from [KioskSettings], which is what the user configured: while kiosk mode is off its
 * other values are inert, and this type collapses them into [Inactive].
 *
 * Read it through [ObserveKioskStateUseCase].
 */
internal sealed interface KioskState {

    /** Where the settings button sits, or `null` when kiosk mode is off and there is none. */
    val settingsEntry: KioskSettingsEntry?

    /** How often the dashboard should reload on its own, or `null` for not at all. */
    val autoReloadInterval: Duration?

    /** Whether the display must be kept awake right now. */
    val keepsScreenOn: Boolean

    val hidesStatusBar: Boolean

    val hidesNavigationBar: Boolean

    /** Brightness to force on the display, or `null` when the app must leave brightness alone. */
    val forcedBrightness: KioskBrightness?

    /** The screensaver to show after an idle period, or `null` when no screensaver should appear. */
    val screensaver: KioskScreensaver?

    /**
     * How loud a sound must be to count as somebody being there, or `null` when sound is ignored.
     *
     * Null whenever kiosk mode is off, no screensaver is configured, or the user has not turned
     * sound waking on, so a consumer that holds a threshold is always one that should be listening.
     */
    val soundWakeThreshold: KioskSoundThreshold?

    /**
     * Whether movement seen by the camera counts as somebody being there.
     *
     * False whenever kiosk mode is off, no screensaver is configured, or the user has not turned
     * camera waking on, so a consumer that sees true is always one that should be watching.
     */
    val wakesOnCameraMotion: Boolean

    /** Kiosk mode is off: nothing is hidden, and the display is left to the system and the user. */
    data object Inactive : KioskState {
        override val settingsEntry: KioskSettingsEntry? = null
        override val autoReloadInterval: Duration? = null
        override val keepsScreenOn: Boolean = false
        override val hidesStatusBar: Boolean = false
        override val hidesNavigationBar: Boolean = false
        override val forcedBrightness: KioskBrightness? = null
        override val screensaver: KioskScreensaver? = null
        override val soundWakeThreshold: KioskSoundThreshold? = null
        override val wakesOnCameraMotion: Boolean = false
    }

    /** Kiosk mode is on, and every value here applies to the display right now. */
    data class Active(
        override val settingsEntry: KioskSettingsEntry,
        override val autoReloadInterval: Duration?,
        override val keepsScreenOn: Boolean,
        override val hidesStatusBar: Boolean,
        override val hidesNavigationBar: Boolean,
        override val forcedBrightness: KioskBrightness?,
        override val screensaver: KioskScreensaver?,
        override val soundWakeThreshold: KioskSoundThreshold?,
        override val wakesOnCameraMotion: Boolean,
    ) : KioskState
}

/**
 * A screensaver that should appear after [idleTimeout] without user interaction.
 *
 * [mode] is never [KioskScreensaverMode.DISABLED]: "no screensaver" is a `null`
 * [KioskState.screensaver] instead.
 */
internal data class KioskScreensaver(val mode: KioskScreensaverMode, val idleTimeout: Duration)

/** Where the button that opens the kiosk settings sits, and whether it can be seen. */
internal data class KioskSettingsEntry(val position: KioskCornerPosition, val hidden: Boolean)
