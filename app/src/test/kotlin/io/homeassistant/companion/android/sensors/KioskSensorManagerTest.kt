package io.homeassistant.companion.android.sensors

import android.content.Context
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverController
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
    private val screensaverController = KioskScreensaverController()

    private fun enabledSensor(id: String, enabled: Boolean = true) = Sensor(
        id = id,
        serverId = 1,
        enabled = enabled,
        state = "false",
        lastSentState = "false",
        lastSentIcon = "",
        icon = "",
        stateType = "boolean",
    )

    private val modeSensor = enabledSensor(KioskSensorManager.kioskMode.id)
    private val screensaverSensor = enabledSensor(KioskSensorManager.kioskScreensaver.id)

    @Before
    fun setUp() {
        context = mockk()
        sensorRepository = mockk()
        serverManager = mockk()

        coEvery { serverManager.servers() } returns listOf(mockk(relaxed = true))
        coEvery { sensorRepository.get(KioskSensorManager.kioskMode.id) } returns listOf(modeSensor)
        coEvery { sensorRepository.get(KioskSensorManager.kioskScreensaver.id) } returns listOf(screensaverSensor)
        coEvery { sensorRepository.get(KioskSensorManager.kioskMode.id, any()) } returns modeSensor
        coEvery { sensorRepository.get(KioskSensorManager.kioskScreensaver.id, any()) } returns screensaverSensor
        coEvery { sensorRepository.getFull(any()) } returns mapOf(modeSensor to emptyList())
        coJustRun { sensorRepository.update(any()) }
        coJustRun { sensorRepository.replaceAllAttributes(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun managerFor(settings: KioskSettings) = KioskSensorManager(context, sensorRepository, serverManager, StubKioskSettingsRepository(settings), screensaverController)

    /**
     * Runs an update and returns the state each sensor reported, keyed by sensor id.
     *
     * Both sensors update in one pass, so a single captured slot would only ever hold whichever
     * reported last.
     */
    private suspend fun statesAfterUpdate(settings: KioskSettings): Map<String, String?> {
        val captured = mutableListOf<Sensor>()
        coJustRun { sensorRepository.update(capture(captured)) }

        managerFor(settings).requestSensorUpdate()

        return captured.associate { it.id to it.state }
    }

    private suspend fun attributesAfterUpdate(settings: KioskSettings): Map<String, String?> {
        val captured = mutableListOf<List<Attribute>>()
        coJustRun { sensorRepository.replaceAllAttributes(KioskSensorManager.kioskMode.id, capture(captured)) }

        managerFor(settings).requestSensorUpdate()

        return captured.flatten().associate { it.name to it.value }
    }

    @Test
    fun `Given kiosk mode is on when requesting an update then the sensor reports it on`() = runTest {
        val states = statesAfterUpdate(KioskSettings(enabled = true))

        assertEquals("true", states[KioskSensorManager.kioskMode.id])
    }

    @Test
    fun `Given kiosk mode is off when requesting an update then the sensor reports it off`() = runTest {
        val states = statesAfterUpdate(KioskSettings(enabled = false))

        assertEquals("false", states[KioskSensorManager.kioskMode.id])
    }

    @Test
    fun `Given the screensaver is not covering the dashboard then its sensor reports off`() = runTest {
        val states = statesAfterUpdate(KioskSettings(enabled = true))

        assertEquals("false", states[KioskSensorManager.kioskScreensaver.id])
    }

    @Test
    fun `Given the screensaver is covering the dashboard then its sensor reports on`() = runTest {
        screensaverController.setVisible(true)

        val states = statesAfterUpdate(KioskSettings(enabled = true))

        assertEquals("true", states[KioskSensorManager.kioskScreensaver.id])
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
    fun `Given both sensors are disabled when requesting an update then nothing is reported`() = runTest {
        coEvery { sensorRepository.get(any()) } returns listOf(modeSensor.copy(enabled = false))

        managerFor(KioskSettings(enabled = true)).requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(any()) }
    }
}
