package io.homeassistant.companion.android.common.data.kiosk

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.LocalStorage
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * In-memory [LocalStorage] so the round-trip tests exercise the real mapping between
 * [KioskSettings] and stored keys rather than asserting against recorded mock calls.
 */
private class FakeLocalStorage : LocalStorage {
    private val values = mutableMapOf<String, Any?>()
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override suspend fun putString(key: String, value: String?) = write(key, value)
    override suspend fun getString(key: String): String? = values[key] as String?
    override suspend fun putLong(key: String, value: Long?) = write(key, value)
    override suspend fun getLong(key: String): Long? = values[key] as Long?
    override suspend fun putInt(key: String, value: Int?) = write(key, value)
    override suspend fun getInt(key: String): Int? = values[key] as Int?
    override suspend fun putBoolean(key: String, value: Boolean) = write(key, value)
    override suspend fun getBoolean(key: String): Boolean = values[key] as Boolean? ?: false
    override suspend fun getBooleanOrNull(key: String): Boolean? = values[key] as Boolean?
    override suspend fun putStringSet(key: String, value: Set<String>) = write(key, value)

    @Suppress("UNCHECKED_CAST")
    override suspend fun getStringSet(key: String): Set<String>? = values[key] as Set<String>?

    override suspend fun remove(key: String) = write(key, null)

    override fun observeChanges(vararg keys: String): Flow<String> = changes

    override suspend fun <T> observeChanges(vararg keys: String, mapper: suspend () -> T): Flow<T> = merge(changes, flowOf("")).map { mapper() }

    /** The keys written so far, in order, so tests can assert when `enabled` lands. */
    val writeOrder = mutableListOf<String>()

    private suspend fun write(key: String, value: Any?) {
        values[key] = value
        writeOrder += key
        changes.emit(key)
    }
}

/** Mirrors the private key constant in the implementation. */
private const val PREF_KIOSK_ENABLED = "kiosk_enabled"

class KioskSettingsRepositoryImplTest {

    private val storage = FakeLocalStorage()
    private val repository = KioskSettingsRepositoryImpl(storage)

    @Test
    fun `Given empty storage when getting settings then defaults are returned`() = runTest {
        val settings = repository.getSettings()

        assertFalse(settings.enabled)
        assertFalse(settings.hideStatusBar)
        assertFalse(settings.hideNavigationBar)
        assertNull(settings.brightness)
        assertEquals(KioskScreensaverMode.DISABLED, settings.screensaverMode)
        assertEquals(KioskSettings.DEFAULT_SCREENSAVER_IDLE_TIMEOUT, settings.screensaverIdleTimeout)
    }

    @Test
    fun `Given stored settings when getting settings then the stored values are returned`() = runTest {
        val stored = KioskSettings(
            enabled = true,
            hideStatusBar = true,
            hideNavigationBar = true,
            brightness = KioskBrightness.fromPercent(40),
            screensaverMode = KioskScreensaverMode.CLOCK,
            screensaverIdleTimeout = KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT,
        )
        repository.setSettings(stored)

        assertEquals(stored, repository.getSettings())
    }

    @Test
    fun `Given a brightness above the maximum when creating it then it is clamped`() {
        assertEquals(KioskBrightness.MAX_VALUE, KioskBrightness.of(5f).value)
        assertEquals(KioskBrightness.MIN_VALUE, KioskBrightness.of(-5f).value)
    }

    @Test
    fun `Given an idle timeout below the minimum when setting settings then it is raised to the minimum`() = runTest {
        repository.setSettings(
            KioskSettings(screensaverIdleTimeout = 1.seconds),
        )

        assertEquals(KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT, repository.getSettings().screensaverIdleTimeout)
    }

    @Test
    fun `Given a brightness finer than one percent when stored then it round-trips to the nearest percent`() = runTest {
        repository.setSettings(KioskSettings(brightness = KioskBrightness.of(0.405f)))

        assertEquals(KioskBrightness.fromPercent(41), repository.getSettings().brightness)
    }

    @Test
    fun `Given a stored idle timeout below the minimum when getting settings then it is raised to the minimum`() = runTest {
        storage.putLong("kiosk_screensaver_idle_timeout_seconds", 1L)

        assertEquals(KioskSettings.MIN_SCREENSAVER_IDLE_TIMEOUT, repository.getSettings().screensaverIdleTimeout)
    }

    @Test
    fun `Given an unknown stored screensaver mode when getting settings then it falls back to disabled`() = runTest {
        storage.putString("kiosk_screensaver_mode", "lava_lamp")

        assertEquals(KioskScreensaverMode.DISABLED, repository.getSettings().screensaverMode)
    }

    @Test
    fun `Given a collector when settings change then the current and updated settings are emitted`() = runTest {
        repository.settingsFlow().test {
            assertFalse(awaitItem().enabled)

            repository.setSettings(KioskSettings(enabled = true, hideStatusBar = true))

            // Every key emits as it is written, and `enabled` is written last when switching on,
            // so the values it activates are already in place by the time it arrives.
            var settings = awaitItem()
            while (!settings.enabled) {
                settings = awaitItem()
            }
            assertTrue(settings.hideStatusBar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given kiosk disabled when checking screensaver enabled then it is false regardless of mode`() {
        val settings = KioskSettings(enabled = false, screensaverMode = KioskScreensaverMode.CLOCK)

        assertFalse(settings.isScreensaverEnabled)
        assertTrue(settings.copy(enabled = true).isScreensaverEnabled)
    }

    @Test
    fun `Given two writers changing different fields then neither loses the other's change`() = runTest {
        // The read-modify-write each writer performs suspends, so without serialisation the later
        // write persists a snapshot taken before the earlier one landed.
        repository.setSettings(KioskSettings())

        listOf(
            launch { repository.updateSettings { it.copy(hideStatusBar = true) } },
            launch { repository.updateSettings { it.copy(hideNavigationBar = true) } },
        ).joinAll()

        val settings = repository.getSettings()
        assertTrue(settings.hideStatusBar)
        assertTrue(settings.hideNavigationBar)
    }

    @Test
    fun `Given kiosk mode is switched on then enabled is stored after the values it activates`() = runTest {
        repository.setSettings(
            KioskSettings(enabled = true, hideStatusBar = true, brightness = KioskBrightness.of(0.5f)),
        )

        // A collector applying an emission that has enabled = true must already see the rest.
        assertEquals(PREF_KIOSK_ENABLED, storage.writeOrder.last())
    }

    @Test
    fun `Given kiosk mode is switched off then enabled is stored before anything else`() = runTest {
        repository.setSettings(KioskSettings(enabled = false, hideStatusBar = true))

        assertEquals(PREF_KIOSK_ENABLED, storage.writeOrder.first())
    }

    @Test
    fun `Given a whole percentage brightness then it survives a round trip`() = runTest {
        // 53% is held as a float that multiplies back to 52.999996, which truncation stored as 52.
        for (percent in 0..100) {
            repository.setSettings(KioskSettings(brightness = KioskBrightness.fromPercent(percent)))

            assertEquals(percent, repository.getSettings().brightness?.toPercent())
        }
    }

    @Test
    fun `Given empty storage when getting settings then remote commands and their confirmations are on`() = runTest {
        val settings = repository.getSettings()

        assertTrue(settings.acceptRemoteCommands)
        assertTrue(settings.showRemoteCommandConfirmations)
    }

    @Test
    fun `Given remote commands are refused when getting settings then that survives the round-trip`() = runTest {
        repository.setSettings(
            KioskSettings(acceptRemoteCommands = false, showRemoteCommandConfirmations = false),
        )

        val settings = repository.getSettings()
        assertFalse(settings.acceptRemoteCommands)
        assertFalse(settings.showRemoteCommandConfirmations)
    }

    @Test
    fun `Given no stored brightness when getting settings then the system keeps control of it`() = runTest {
        repository.setSettings(KioskSettings(enabled = true, brightness = null))

        assertNull(repository.getSettings().brightness)
    }

    @Test
    fun `Given a stored brightness when it is cleared then the system takes brightness back`() = runTest {
        repository.setSettings(KioskSettings(enabled = true, brightness = KioskBrightness.of(0.5f)))
        assertEquals(KioskBrightness.fromPercent(50), repository.getSettings().brightness)

        repository.setSettings(KioskSettings(enabled = true, brightness = null))

        assertNull(repository.getSettings().brightness)
    }
}
