package io.homeassistant.companion.android.sensors

import android.content.Context
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In-memory [KioskSettingsRepository]; the manager only ever reads from it. */
private class StubKioskSettingsRepository(private var settings: KioskSettings) : KioskSettingsRepository {
    override suspend fun getSettings(): KioskSettings = settings
    override suspend fun setSettings(settings: KioskSettings) {
        this.settings = settings
    }
    override suspend fun updateSettings(change: (KioskSettings) -> KioskSettings) {
        settings = change(settings)
    }

    override fun settingsFlow(): Flow<KioskSettings> = flowOf(settings)
}

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class KioskSensorManagerTest {

    private lateinit var context: Context
    private lateinit var sensorRepository: SensorRepository
    private lateinit var serverManager: ServerManager

    private val enabledSensor = Sensor(
        id = KioskSensorManager.kioskMode.id,
        serverId = 1,
        enabled = true,
        state = "false",
        lastSentState = "false",
        lastSentIcon = KioskSensorManager.kioskMode.statelessIcon,
        icon = KioskSensorManager.kioskMode.statelessIcon,
        stateType = "boolean",
    )

    @Before
    fun setUp() {
        context = mockk()
        sensorRepository = mockk()
        serverManager = mockk()

        coEvery { serverManager.servers() } returns listOf(mockk(relaxed = true))
        coEvery { sensorRepository.get(any()) } returns listOf(enabledSensor)
        coEvery { sensorRepository.get(any(), any()) } returns enabledSensor
        coEvery { sensorRepository.getFull(any()) } returns mapOf(enabledSensor to emptyList())
        coJustRun { sensorRepository.update(any()) }
        coJustRun { sensorRepository.replaceAllAttributes(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun managerFor(settings: KioskSettings) = KioskSensorManager(context, sensorRepository, serverManager, StubKioskSettingsRepository(settings))

    private suspend fun attributesAfterUpdate(settings: KioskSettings): Map<String, String?> {
        val captured = slot<List<Attribute>>()
        coJustRun { sensorRepository.replaceAllAttributes(any(), capture(captured)) }

        managerFor(settings).requestSensorUpdate()

        return captured.captured.associate { it.name to it.value }
    }

    @Test
    fun `Given kiosk mode is on when requesting an update then the sensor reports it on`() = runTest {
        val updated = slot<Sensor>()
        coJustRun { sensorRepository.update(capture(updated)) }

        managerFor(KioskSettings(enabled = true)).requestSensorUpdate()

        assertEquals("true", updated.captured.state)
    }

    @Test
    fun `Given kiosk mode is off when requesting an update then the sensor reports it off`() = runTest {
        val updated = slot<Sensor>()
        coJustRun { sensorRepository.update(capture(updated)) }

        managerFor(KioskSettings(enabled = false)).requestSensorUpdate()

        assertEquals("false", updated.captured.state)
    }

    @Test
    fun `Given a configured kiosk when requesting an update then the configuration rides along`() = runTest {
        val attributes = attributesAfterUpdate(
            KioskSettings(
                enabled = true,
                hideStatusBar = true,
                hideNavigationBar = false,
                brightness = KioskBrightness.fromPercent(40),
                screensaverMode = KioskScreensaverMode.CLOCK,
                screensaverIdleTimeout = 10.minutes,
                acceptRemoteCommands = false,
            ),
        )

        assertEquals("true", attributes["hide_status_bar"])
        assertEquals("false", attributes["hide_navigation_bar"])
        assertEquals("40", attributes["brightness"])
        assertEquals("clock", attributes["screensaver_mode"])
        assertEquals("600", attributes["screensaver_idle_timeout_seconds"])
        assertEquals("false", attributes["accept_remote_commands"])
    }

    @Test
    fun `Given the system controls brightness then the attribute says so rather than reporting a number`() = runTest {
        val attributes = attributesAfterUpdate(KioskSettings(enabled = true, brightness = null))

        assertEquals("system", attributes["brightness"])
    }

    @Test
    fun `Given the sensor is disabled when requesting an update then nothing is reported`() = runTest {
        coEvery { sensorRepository.get(any()) } returns listOf(enabledSensor.copy(enabled = false))

        managerFor(KioskSettings(enabled = true)).requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(any()) }
    }
}
