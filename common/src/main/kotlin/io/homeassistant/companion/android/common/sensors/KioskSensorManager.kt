package io.homeassistant.companion.android.common.sensors

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverController
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import javax.inject.Singleton

/** Attribute names on the kiosk mode sensor, so an automation can see how the kiosk is configured. */
private const val ATTRIBUTE_HIDE_STATUS_BAR = "hide_status_bar"
private const val ATTRIBUTE_HIDE_NAVIGATION_BAR = "hide_navigation_bar"
private const val ATTRIBUTE_BRIGHTNESS = "brightness"
private const val ATTRIBUTE_SCREENSAVER_MODE = "screensaver_mode"
private const val ATTRIBUTE_SCREENSAVER_IDLE_TIMEOUT_SECONDS = "screensaver_idle_timeout_seconds"
private const val ATTRIBUTE_ACCEPT_REMOTE_COMMANDS = "accept_remote_commands"

/** Reported for the brightness attribute when kiosk mode leaves the display to the system. */
private const val BRIGHTNESS_SYSTEM = "system"

/**
 * Reports whether kiosk mode is on, and how it is configured.
 *
 * The configuration rides along as attributes rather than as sensors of its own: an automation that
 * cares about a kiosk's brightness or screensaver is almost always already looking at whether kiosk
 * mode is on, and a sensor per setting would be a lot of entities for a feature most users never
 * enable.
 */
@Singleton
class KioskSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
    private val kioskSettingsRepository: KioskSettingsRepository,
    private val screensaverController: KioskScreensaverController,
) : SensorManager {

    companion object {
        @ProvidesSensor
        internal val kioskMode = SensorManager.BasicSensor(
            "kiosk_mode",
            "binary_sensor",
            commonR.string.basic_sensor_name_kiosk_mode,
            commonR.string.sensor_description_kiosk_mode,
            "mdi:tablet-dashboard",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        internal val kioskScreensaver = SensorManager.BasicSensor(
            "kiosk_screensaver",
            "binary_sensor",
            commonR.string.basic_sensor_name_kiosk_screensaver,
            commonR.string.sensor_description_kiosk_screensaver,
            "mdi:weather-night",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String = "https://companion.home-assistant.io/docs/core/sensors"

    override val name: Int
        get() = commonR.string.sensor_name_kiosk

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> = listOf(kioskMode, kioskScreensaver)

    override fun requiredPermissions(sensorId: String): Array<String> = emptyArray()

    override suspend fun requestSensorUpdate() {
        if (isEnabled(kioskMode)) {
            val settings = kioskSettingsRepository.getSettings()
            onSensorUpdated(kioskMode, settings.enabled, kioskMode.statelessIcon, settings.toAttributes())
        }

        if (isEnabled(kioskScreensaver)) {
            // False whenever no screen is showing the dashboard, which is correct: a screensaver
            // that is not drawn is not covering anything.
            onSensorUpdated(
                kioskScreensaver,
                screensaverController.isVisible.value,
                kioskScreensaver.statelessIcon,
                emptyMap(),
            )
        }
    }
}

private fun KioskSettings.toAttributes(): Map<String, Any> = mapOf(
    ATTRIBUTE_HIDE_STATUS_BAR to hideStatusBar,
    ATTRIBUTE_HIDE_NAVIGATION_BAR to hideNavigationBar,
    // A percentage keeps the attribute in the same units the kiosk_set_brightness command takes.
    ATTRIBUTE_BRIGHTNESS to (brightness?.toPercent() ?: BRIGHTNESS_SYSTEM),
    ATTRIBUTE_SCREENSAVER_MODE to screensaverMode.storageValue,
    ATTRIBUTE_SCREENSAVER_IDLE_TIMEOUT_SECONDS to screensaverIdleTimeout.inWholeSeconds,
    ATTRIBUTE_ACCEPT_REMOTE_COMMANDS to acceptRemoteCommands,
)
