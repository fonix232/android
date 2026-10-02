package io.homeassistant.companion.android.common.data.kiosk

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the kiosk mode configuration.
 *
 * Settings are read and written as a whole [KioskSettings] because kiosk mode interprets several
 * values together — the screensaver mode and its idle timeout, for example — so a caller changing
 * one almost always needs the rest in hand. That shape is a convenience, not a transaction: see
 * [setSettings] for what [settingsFlow] can emit while a write is in progress.
 */
interface KioskSettingsRepository {

    /** Returns the stored settings, falling back to [KioskSettings] defaults for absent values. */
    suspend fun getSettings(): KioskSettings

    /**
     * Persists [settings].
     *
     * Every value is stored on its own, so while the write progresses [settingsFlow] can emit
     * combinations of the previous and the new settings, and a process death part-way through
     * leaves a mixture of the two stored. A caller that needs one coherent configuration should
     * work from the instance it passed in.
     *
     * Out-of-range values are coerced rather than rejected: [KioskSettings.screensaverIdleTimeout]
     * is raised to [KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT] when shorter, here and again when
     * read back, because a very short timeout makes the dashboard unusable. Brightness is already
     * clamped by [KioskBrightness]. A caller that needs the coerced result should read it back from
     * [getSettings] or collect [settingsFlow].
     */
    suspend fun setSettings(settings: KioskSettings)

    /**
     * Emits the current settings immediately on collection, then on every change.
     *
     * Intended for the kiosk manager and sensors, which must react to changes made from the
     * settings screen or by a server command without polling.
     */
    fun settingsFlow(): Flow<KioskSettings>
}
