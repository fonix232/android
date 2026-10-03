package io.homeassistant.companion.android.common.data.kiosk

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Screen brightness applied while kiosk mode is active, expressed as a fraction of the
 * display's maximum brightness.
 *
 * Values are always within [MIN_VALUE]..[MAX_VALUE]; the constructor clamps anything outside
 * that range instead of throwing, so a malformed stored value or an out-of-range server command
 * degrades to the nearest valid brightness rather than failing the whole kiosk session.
 */
@JvmInline
value class KioskBrightness private constructor(val value: Float) {

    companion object {
        const val MIN_VALUE: Float = 0f
        const val MAX_VALUE: Float = 1f

        /**
         * Returns a brightness clamped into [MIN_VALUE]..[MAX_VALUE].
         *
         * `NaN` becomes [MIN_VALUE]: `coerceIn` passes it through untouched because neither
         * comparison holds, which would put a value outside the range this type promises into
         * storage and onto a window.
         */
        fun of(value: Float): KioskBrightness =
            KioskBrightness(if (value.isNaN()) MIN_VALUE else value.coerceIn(MIN_VALUE, MAX_VALUE))

        /**
         * Returns the brightness stored as a percentage in 0..100, or null when [percent] is null.
         *
         * Null means brightness is not kiosk mode's to set; see [KioskSettings.brightness].
         */
        fun fromPercent(percent: Int?): KioskBrightness? = percent?.let { of(it / PERCENT_SCALE) }

        private const val PERCENT_SCALE: Float = 100f
    }

    /**
     * Returns this brightness as a whole percentage in 0..100, for storage and sensor reporting.
     *
     * Rounds rather than truncates, so every whole percentage survives a round trip: 53% is held
     * as a float that multiplies back to 52.999996, which truncation would store as 52%.
     *
     * Precision finer than a percent is still lost, so `of(0.405f)` comes back from [fromPercent]
     * as `0.4f` and the two are not equal. Compare through this function when either side may have
     * come from storage.
     */
    fun toPercent(): Int = (value * PERCENT_SCALE).roundToInt()
}

/**
 * How often kiosk mode reloads the dashboard on its own.
 *
 * A wall display is left running for weeks, long enough for a frontend to end up wedged on a stale
 * page or a dead websocket. Reloading on a schedule gets it back without anyone walking over to it.
 */
enum class KioskAutoReloadInterval(val storageValue: String, val interval: Duration?) {
    /** No scheduled reload; the dashboard is only reloaded when something asks for it. */
    NEVER("never", null),

    MINUTES_10("minutes10", 10.minutes),
    MINUTES_15("minutes15", 15.minutes),
    MINUTES_30("minutes30", 30.minutes),
    HOURS_1("hours1", 1.hours),
    ;

    companion object {
        /** Returns the matching entry, or [NEVER] when [value] is null or unknown. */
        fun fromStorageValue(value: String?): KioskAutoReloadInterval =
            entries.firstOrNull { it.storageValue == value } ?: NEVER
    }
}

/**
 * What the screensaver shows once the device has been idle for
 * [KioskSettings.screensaverIdleTimeout].
 */
enum class KioskScreensaverMode(val storageValue: String) {
    /** No screensaver; the dashboard stays visible. */
    DISABLED("disabled"),

    /** A clock and date over a black background. */
    CLOCK("clock"),

    /** A fully black screen, dimmed as far as the device allows. */
    BLANK("blank"),
    ;

    companion object {
        /** Returns the matching entry, or [DISABLED] when [value] is null or unknown. */
        fun fromStorageValue(value: String?): KioskScreensaverMode =
            entries.firstOrNull { it.storageValue == value } ?: DISABLED
    }
}

/**
 * How loud a sound has to be to count as somebody being there, in dBFS.
 *
 * Values are always within [KIOSK_SOUND_FLOOR_DBFS]..[KIOSK_SOUND_CEILING_DBFS]; the constructor
 * clamps anything outside that range, so a malformed stored value degrades to the nearest usable
 * threshold rather than leaving the screensaver permanently awake or permanently asleep.
 */
@JvmInline
value class KioskSoundThreshold private constructor(val dbfs: Float) {

    /** Where this threshold sits on the meter, 0..1. */
    val meterPosition: Float get() = dbfsToMeterPosition(dbfs)

    companion object {
        /**
         * Threshold used until the user calibrates one.
         *
         * Chosen to sit between a quiet room and someone speaking at the device, so the feature
         * does something sensible before anyone opens the meter. The meter is what settles it for
         * a given room.
         */
        val DEFAULT: KioskSoundThreshold = KioskSoundThreshold(-40f)

        /** Returns a threshold clamped into the meter's window. */
        fun ofDbfs(dbfs: Float): KioskSoundThreshold =
            KioskSoundThreshold(dbfs.coerceIn(KIOSK_SOUND_FLOOR_DBFS, KIOSK_SOUND_CEILING_DBFS))

        /** Returns the threshold a point on the meter stands for. */
        fun ofMeterPosition(position: Float): KioskSoundThreshold = ofDbfs(meterPositionToDbfs(position))
    }
}

/**
 * The complete kiosk mode configuration.
 *
 * Immutable: produce changes with [copy] and persist them through
 * [KioskSettingsRepository.setSettings].
 */
data class KioskSettings(
    /** Whether kiosk mode is active. All other values are inert while this is false. */
    val enabled: Boolean = false,

    /**
     * The server whose dashboard this kiosk shows, or null to follow whichever server is active.
     *
     * A wall display is usually pinned to one server even on a phone that switches between
     * several, which is why it is stored here rather than read from the active server.
     */
    val serverId: Int? = null,

    /**
     * The dashboard path this kiosk returns to, such as `lovelace/wall`, or null for the server's
     * default dashboard.
     */
    val dashboardPath: String? = null,

    /**
     * Whether the kiosk settings ask for the device's lock credential before they can be read.
     *
     * A kiosk usually hangs on a wall where anyone can reach it, so this is what stops a passer-by
     * turning kiosk mode off. It guards the settings, not the dashboard: whoever can see the
     * dashboard could see it anyway.
     */
    val requireAuthentication: Boolean = false,

    /**
     * Whether kiosk commands arriving from the server are obeyed.
     *
     * On by default: a kiosk is usually somewhere nobody stands, so being able to drive it from an
     * automation is the point. Turning it off makes the device configurable only in its own
     * settings.
     */
    val acceptRemoteCommands: Boolean = true,

    /**
     * Whether obeying a kiosk command from the server tells the user it happened.
     *
     * Off still runs the command, silently. Ignored while [acceptRemoteCommands] is false, because
     * then nothing arrives to confirm.
     */
    val showRemoteCommandConfirmations: Boolean = true,

    /**
     * Whether the display is kept awake while kiosk mode is active.
     *
     * Independent of, and additive to, the app-wide "keep screen on" preference: a wall display
     * wants to stay lit whether or not the user turned that on for their phone.
     */
    val keepScreenOn: Boolean = false,

    val hideStatusBar: Boolean = false,

    val hideNavigationBar: Boolean = false,

    /**
     * Brightness forced while kiosk mode is active, or null to leave the display to the system,
     * including its automatic adjustment.
     */
    val brightness: KioskBrightness? = null,

    /** How often the dashboard reloads on its own. */
    val autoReload: KioskAutoReloadInterval = KioskAutoReloadInterval.NEVER,

    /**
     * Whether a sound louder than [soundWakeThreshold] counts as somebody being there.
     *
     * Off by default, and nothing captures audio until the user turns it on: the microphone is not
     * something to start using on a device's behalf.
     */
    val wakeOnSound: Boolean = false,

    /** How loud a sound has to be before [wakeOnSound] treats it as somebody being there. */
    val soundWakeThreshold: KioskSoundThreshold = KioskSoundThreshold.DEFAULT,

    val screensaverMode: KioskScreensaverMode = KioskScreensaverMode.DISABLED,

    /**
     * How long the device must be idle before the screensaver appears.
     *
     * Ignored when [screensaverMode] is [KioskScreensaverMode.DISABLED].
     */
    val screensaverIdleTimeout: Duration = DEFAULT_SCREENSAVER_IDLE_TIMEOUT,
) {
    val isScreensaverEnabled: Boolean
        get() = enabled && screensaverMode != KioskScreensaverMode.DISABLED

    companion object {
        /** Idle timeout used when the user has not chosen one. */
        val DEFAULT_SCREENSAVER_IDLE_TIMEOUT: Duration = 5.minutes

        /** Shortest idle timeout that still leaves the dashboard usable. */
        val MIN_SCREENSAVER_IDLE_TIMEOUT: Duration = 1.minutes
    }
}
