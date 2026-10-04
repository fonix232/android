package io.homeassistant.companion.android.kiosk

import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [KioskSettingsRepository] for the kiosk tests in `:app`.
 *
 * It cannot live in `:testing-unit`, which must stay independent from `:common`.
 *
 * Unlike the real repository it stores the settings as one object, so a write produces exactly one
 * emission. Tests that care about the per-key write behaviour belong with the repository itself.
 */
internal class FakeKioskSettingsRepository(initial: KioskSettings = KioskSettings()) : KioskSettingsRepository {

    private val settings = MutableStateFlow(initial)

    override suspend fun getSettings(): KioskSettings = settings.value

    override suspend fun setSettings(settings: KioskSettings) {
        this.settings.value = settings
    }

    override suspend fun updateSettings(change: (KioskSettings) -> KioskSettings) {
        settings.update(change)
    }

    override fun settingsFlow(): Flow<KioskSettings> = settings.asStateFlow()
}
