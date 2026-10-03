package io.homeassistant.companion.android.kiosk

import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
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

    /** Whether the display must be kept awake right now. */
    val keepsScreenOn: Boolean

    val hidesStatusBar: Boolean

    val hidesNavigationBar: Boolean

    /** Brightness to force on the display, or `null` when the app must leave brightness alone. */
    val forcedBrightness: KioskBrightness?

    /** The screensaver to show after an idle period, or `null` when no screensaver should appear. */
    val screensaver: KioskScreensaver?

    /** Kiosk mode is off: nothing is hidden, and the display is left to the system and the user. */
    data object Inactive : KioskState {
        override val keepsScreenOn: Boolean = false
        override val hidesStatusBar: Boolean = false
        override val hidesNavigationBar: Boolean = false
        override val forcedBrightness: KioskBrightness? = null
        override val screensaver: KioskScreensaver? = null
    }

    /** Kiosk mode is on, and every value here applies to the display right now. */
    data class Active(
        override val keepsScreenOn: Boolean,
        override val hidesStatusBar: Boolean,
        override val hidesNavigationBar: Boolean,
        override val forcedBrightness: KioskBrightness?,
        override val screensaver: KioskScreensaver?,
    ) : KioskState
}

/**
 * A screensaver that should appear after [idleTimeout] without user interaction.
 *
 * [mode] is never [KioskScreensaverMode.DISABLED]: "no screensaver" is a `null`
 * [KioskState.screensaver] instead.
 */
internal data class KioskScreensaver(val mode: KioskScreensaverMode, val idleTimeout: Duration)
