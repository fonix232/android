package io.homeassistant.companion.android.kiosk.notifications

import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import java.util.Locale

/**
 * Prefix shared by every kiosk command arriving in a notification's message.
 *
 * TODO The wire names are unresolved: every other notification command in this app is `command_*`,
 *  and the companion app on iOS uses `command_*` everywhere too except for its kiosk commands. We
 *  copy that one anomaly so a single automation can drive both platforms, which is a deliberate
 *  break from this app's convention rather than a match for it. Reviewers should challenge it. The
 *  names come from
 *  https://github.com/home-assistant/iOS/blob/main/Sources/App/Notifications/KioskPushCommand.swift
 */
internal const val KIOSK_COMMAND_PREFIX = "kiosk_"

/** A kiosk command sent from Home Assistant as a notification message. */
sealed interface KioskPushCommand {

    /** Sets the brightness kiosk mode forces on the display. */
    data class SetBrightness(val brightness: KioskBrightness) : KioskPushCommand

    /** Sets what the screensaver shows, including turning it off. */
    data class SetScreensaverMode(val mode: KioskScreensaverMode) : KioskPushCommand

    /** Covers the dashboard with the screensaver now, without waiting for the idle timeout. */
    data object ShowScreensaver : KioskPushCommand

    /** Uncovers the dashboard now, and restarts the idle countdown. */
    data object HideScreensaver : KioskPushCommand

    companion object {
        private const val SET_BRIGHTNESS = "${KIOSK_COMMAND_PREFIX}set_brightness"
        private const val SET_SCREENSAVER_MODE = "${KIOSK_COMMAND_PREFIX}set_screensaver_mode"
        private const val SHOW_SCREENSAVER = "${KIOSK_COMMAND_PREFIX}show_screensaver"
        private const val HIDE_SCREENSAVER = "${KIOSK_COMMAND_PREFIX}hide_screensaver"

        /** Payload key holding the level for the commands that take one. */
        private const val KEY_LEVEL = "level"

        /** Payload key holding the screensaver mode. */
        private const val KEY_MODE = "mode"

        /** Above this a level is read as a percentage rather than a fraction. */
        private const val FRACTION_MAX = 1.0

        private const val PERCENT_SCALE = 100.0

        /** Whether [message] is addressed to kiosk mode at all, regardless of whether we serve it. */
        fun isKioskCommand(message: String?): Boolean = message?.normalize()?.startsWith(KIOSK_COMMAND_PREFIX) == true

        /**
         * Returns the command [message] asks for, or null when it names one this app does not serve
         * or its payload is unusable.
         *
         * An unusable payload yields null rather than a command with a default, so a typo in an
         * automation leaves the kiosk alone instead of quietly setting the screen to something
         * nobody asked for.
         */
        fun from(message: String?, data: Map<String, String>): KioskPushCommand? = when (message?.normalize()) {
            SET_BRIGHTNESS -> data.level()?.let { SetBrightness(KioskBrightness.of(it.toFloat())) }
            SET_SCREENSAVER_MODE -> data.screensaverMode()?.let(::SetScreensaverMode)
            SHOW_SCREENSAVER -> ShowScreensaver
            HIDE_SCREENSAVER -> HideScreensaver
            else -> null
        }

        /**
         * Reads the level as a fraction of full scale.
         *
         * Accepts either a fraction (`0`..`1`) or a percentage (above `1`), because an automation
         * author writing `level: 40` means 40% and writing `level: 0.4` means the same thing.
         */
        private fun Map<String, String>.level(): Double? = this[KEY_LEVEL]
            ?.toDoubleOrNull()
            // NaN and the infinities parse, and coerceIn passes NaN straight through, so a payload
            // of `level: NaN` would otherwise become a command that sets the screen to zero.
            ?.takeIf { it.isFinite() }
            ?.let { if (it > FRACTION_MAX) it / PERCENT_SCALE else it }
            ?.coerceIn(0.0, FRACTION_MAX)

        /** Reads the screensaver mode, or null when the payload names one that does not exist. */
        private fun Map<String, String>.screensaverMode(): KioskScreensaverMode? = this[KEY_MODE]
            ?.normalize()
            ?.let { value -> KioskScreensaverMode.entries.firstOrNull { it.storageValue == value } }

        /** Server payloads are free text, so compare them trimmed and case-insensitively. */
        private fun String.normalize(): String = trim().lowercase(Locale.ROOT)
    }
}
